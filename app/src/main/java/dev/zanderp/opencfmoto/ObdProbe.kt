// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * v2.4 test · reads the ECU through a generic ELM327 adapter plugged into the bike's OBD port (Luis's
 * 125X with Torque, 30 Oct 2026: RPM and mode 06 results over Bluetooth). Classic Bluetooth SPP only,
 * adapter paired in Android's settings first.
 *
 * READ ONLY by design, like [PkeBattery]: AT setup commands, mode 01 (live data), 01 01 (light +
 * code count) and mode 03 (stored codes). Never mode 04 (clear codes) or anything that writes; [SAFE]
 * is checked before every send. Everything goes to the log under [OBD]; the VIN and the adapter's
 * address are never asked for or logged.
 */
@SuppressLint("MissingPermission")
object ObdProbe {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val NAME_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "KONNWEI", "ICAR", "VEEPEAK", "SCAN")
    // 01 + 1..6 PIDs (+ optional "responses expected" digit, an ELM327 speed-up) | AT setup | 03 (stored codes).
    internal val SAFE = Regex("^(AT[A-Z0-9 ]*|01([0-9A-F]{2}){1,6}[1-9]?|03)$")
    private val LIVE = listOf(0x0C, 0x0D, 0x05, 0x11)
    private const val LIVE_SECONDS = 20

    private fun log(s: String) = LogBus.log("[OBD] $s")

    enum class Failure { BLUETOOTH_OFF, NO_ADAPTER, NO_LINK, NO_ECU }

    class Result(val failure: Failure?, val summary: List<String>)

    /** Paired devices that look like an OBD adapter, best guess first; names only, never addresses. */
    fun candidates(ctx: Context): List<BluetoothDevice> {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        val bonded = runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList())
        return bonded.filter { d -> val n = runCatching { d.name }.getOrNull().orEmpty().uppercase(); NAME_HINTS.any { n.contains(it) } }
    }

    fun pairedNames(ctx: Context): List<String> {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching { adapter.bondedDevices.map { runCatching { it.name }.getOrNull() ?: "?" } }.getOrDefault(emptyList())
    }

    /** Blocking: call off the main thread. [progress] gets short status lines for the dialog. */
    fun run(ctx: Context, device: BluetoothDevice, progress: (String) -> Unit): Result {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return Result(Failure.BLUETOOTH_OFF, emptyList())
        val name = runCatching { device.name }.getOrNull() ?: "?"
        log("probe start · adapter '$name' (read only: AT, 01xx, 03)")
        runCatching { adapter.cancelDiscovery() }
        val t0 = SystemClock.elapsedRealtime()
        val socket = connect(device) ?: return Result(Failure.NO_LINK, emptyList())
        log("connected in ${SystemClock.elapsedRealtime() - t0} ms")
        val out = ArrayList<String>()
        try {
            val io = Elm(socket.inputStream, socket.outputStream)
            progress("ELM327…")
            io.cmd("ATZ", 3_000)
            for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) io.cmd(c)
            io.cmd("ATI").trim().takeIf { it.isNotEmpty() }?.let { out += "adapter: $it" }
            io.cmd("ATRV").trim().takeIf { it.isNotEmpty() }?.let { out += "adapter voltage: $it" }

            progress("ECU…")
            val supported = ArrayList<Int>()
            var base = 0x00
            while (base <= 0x60) {
                // First query may take seconds while the adapter searches for the protocol.
                val r = io.cmd("01%02X".format(base), if (base == 0) 12_000 else 4_000)
                val s = ObdDecode.supported(r, base)
                if (s.isEmpty()) break
                supported += s
                if ((base + 0x20) !in s) break
                base += 0x20
            }
            if (supported.isEmpty()) {
                log("the ECU didn't answer 01 00 — engine/contact on? protocol not found")
                return Result(Failure.NO_ECU, out)
            }
            io.cmd("ATDP").trim().takeIf { it.isNotEmpty() }?.let { out += "protocol: $it" }
            out += "supported PIDs: " + supported.filter { it % 0x20 != 0 }.joinToString(" ") { "%02X".format(it) }
            log(out.last())

            progress("PIDs…")
            for (p in ObdDecode.PIDS) {
                if (p.pid !in supported) continue
                val v = ObdDecode.value(io.cmd("01%02X".format(p.pid)), p.pid)
                if (v != null) out += "${p.name}: ${ObdDecode.format(v, p.unit)}"
            }
            ObdDecode.milAndCount(io.cmd("0101"))?.let { (mil, n) -> out += "check-engine light: ${if (mil) "ON" else "off"} · stored codes: $n" }
            val codes = ObdDecode.troubleCodes(io.cmd("03", 6_000))
            out += "trouble codes: " + (codes.joinToString(" ").ifEmpty { "none" })
            out.forEach { log(it) }

            val live = LIVE.filter { it in supported }
            if (live.isNotEmpty()) {
                progress("${LIVE_SECONDS} s…")
                log("live: ${live.joinToString(", ") { ObdDecode.pid(it)!!.name }} for $LIVE_SECONDS s")
                val until = SystemClock.elapsedRealtime() + LIVE_SECONDS * 1000L
                var rounds = 0; var sum = 0L; var max = 0L; var misses = 0
                while (SystemClock.elapsedRealtime() < until) {
                    val ts = SystemClock.elapsedRealtime()
                    val line = live.mapNotNull { pid ->
                        val v = ObdDecode.value(io.cmd("01%02X".format(pid), 2_000), pid)
                        if (v == null) misses++
                        v?.let { "${ObdDecode.pid(pid)!!.name} ${ObdDecode.format(it, ObdDecode.pid(pid)!!.unit)}" }
                    }
                    val took = SystemClock.elapsedRealtime() - ts
                    rounds++; sum += took; if (took > max) max = took
                    log("live: ${line.joinToString(" · ")} (${took} ms)")
                    progress(line.joinToString("\n"))
                }
                val avg = if (rounds > 0) sum / rounds else 0
                out += "live: $rounds rounds of ${live.size} PIDs, avg $avg ms, max $max ms, misses $misses"
                log(out.last())
            }
            return Result(null, out)
        } catch (e: Exception) {
            log("probe error: $e")
            return Result(if (out.isEmpty()) Failure.NO_ECU else null, out)
        } finally {
            runCatching { socket.close() }
            log("probe end · ${(SystemClock.elapsedRealtime() - t0) / 1000} s")
        }
    }

    internal fun connect(device: BluetoothDevice): BluetoothSocket? {
        val attempts: List<Pair<String, () -> BluetoothSocket>> = listOf(
            "secure" to { device.createRfcommSocketToServiceRecord(SPP) },
            "insecure" to { device.createInsecureRfcommSocketToServiceRecord(SPP) },
            // Some cheap clones only answer on channel 1 without an SDP record.
            "channel 1" to { device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket },
        )
        for ((how, make) in attempts) {
            val s = runCatching { make() }.getOrNull() ?: continue
            try { s.connect(); log("RFCOMM link ($how)"); return s }
            catch (e: Exception) { log("RFCOMM $how failed: ${e.message}"); runCatching { s.close() } }
        }
        log("no link to the adapter — paired? still connected to another app (Torque)? is it BLE-only?")
        return null
    }

    /** ELM327 request/response: one command, read until the ">" prompt. */
    internal class Elm(private val inp: InputStream, private val outp: OutputStream) {
        /** Off for the continuous reader, which would otherwise log 10+ lines a second. */
        @Volatile var verbose = true

        fun cmd(c: String, timeoutMs: Long = 2_500): String {
            require(SAFE.matches(c)) { "not a read-only command: $c" }
            while (inp.available() > 0) inp.read()
            outp.write("$c\r".toByteArray(Charsets.US_ASCII)); outp.flush()
            val sb = StringBuilder()
            val until = SystemClock.elapsedRealtime() + timeoutMs
            while (SystemClock.elapsedRealtime() < until) {
                if (inp.available() > 0) {
                    val ch = inp.read()
                    if (ch < 0) break
                    if (ch == '>'.code) break
                    sb.append(ch.toChar())
                } else Thread.sleep(10)
            }
            val reply = sb.toString().replace('\r', '\n').lines().map { it.trim() }.filter { it.isNotEmpty() && it != c }.joinToString("\n")
            if (verbose) LogBus.log("[OBD] → $c  ← ${reply.replace('\n', ' ').ifEmpty { "(nothing)" }}")
            return reply
        }
    }
}
