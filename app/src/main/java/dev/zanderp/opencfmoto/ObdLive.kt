// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * v2.4 · continuous read-only reading of the ECU through the ELM327 the rider chose in Help → OBD
 * ([AppSettings.obdLiveAdapter]). Runs while the projection service is alive and feeds [latest]; the
 * engine state ([engineRunning]) is taken from the real RPM instead of guessing it from the battery.
 *
 * Same rules as [ObdProbe]: only AT setup, mode 01 and 03 ever leave the phone ([ObdProbe.SAFE] is
 * checked before every send), nothing is cleared or written, and no VIN or adapter address is logged.
 * One reader at a time: the Help test stops it first and starts it again afterwards.
 */
@SuppressLint("MissingPermission")
object ObdLive {
    class Snapshot(val rpm: Double?, val speed: Double?, val coolant: Double?, val throttle: Double?, val atMs: Long)

    private val WANT = listOf(0x0C, 0x0D, 0x11, 0x05)   // rpm, speed, throttle, coolant
    private const val FRESH_MS = 5_000L
    private const val ENGINE_ON_RPM = 400.0
    private const val RETRY_MIN_MS = 5_000L
    private const val RETRY_MAX_MS = 30_000L

    /** How one round of [WANT] is asked; chosen by timing at the start of each connection. */
    internal enum class Mode(val label: String) { SINGLE("one PID per request"), SINGLE_COUNT("one PID, response count"), MULTI("all PIDs in one request") }

    @Volatile var latest: Snapshot? = null
        private set
    @Volatile private var worker: Thread? = null
    @Volatile private var stopFlag = false
    @Volatile private var engine: Boolean? = null

    private fun log(s: String) = LogBus.log("[OBD] $s")

    /** True / false while a reading under 5 s old has an RPM; null when there is no live reading. */
    fun engineRunning(): Boolean? {
        val s = latest ?: return null
        if (SystemClock.elapsedRealtime() - s.atMs > FRESH_MS) return null
        return s.rpm?.let { engineFromRpm(it) }
    }

    internal fun engineFromRpm(rpm: Double): Boolean = rpm >= ENGINE_ON_RPM

    fun running(): Boolean = worker?.isAlive == true

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val name = AppSettings.obdLiveAdapter(app) ?: return
        if (running()) return
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            log("live reader not started: no Bluetooth permission")
            return
        }
        stopFlag = false
        val t = Thread({ loop(app, name) }, "obd-live")
        t.isDaemon = true
        worker = t
        t.start()
    }

    /** Stops the reader; [wait] (off the main thread only) holds up to 3 s so the adapter is free to reopen. */
    fun stop(wait: Boolean = false) {
        val t = worker ?: return
        stopFlag = true
        t.interrupt()
        if (wait) runCatching { t.join(3_000) }
        worker = null
        latest = null
        setEngine(null)
    }

    private fun setEngine(now: Boolean?) {
        if (now == engine) return
        engine = now
        PkeBattery.onEngineChanged?.invoke()
    }

    private fun loop(ctx: Context, adapterName: String) {
        var wait = RETRY_MIN_MS
        log("live reader on for '$adapterName' (read only)")
        while (!stopFlag) {
            val ok = try { session(ctx, adapterName) } catch (e: InterruptedException) { break } catch (e: Exception) { log("live reader error: $e"); false }
            if (stopFlag) break
            latest = null
            setEngine(null)
            wait = if (ok) RETRY_MIN_MS else (wait * 2).coerceAtMost(RETRY_MAX_MS)
            try { Thread.sleep(wait) } catch (_: InterruptedException) { break }
        }
        log("live reader off")
    }

    /** One connection: true if it ran and was lost after reading, false if it never got going. */
    private fun session(ctx: Context, adapterName: String): Boolean {
        val bt = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (bt == null || !bt.isEnabled) return false
        val dev = runCatching { bt.bondedDevices.firstOrNull { runCatching { it.name }.getOrNull() == adapterName } }.getOrNull()
        if (dev == null) { log("live reader: '$adapterName' is not paired any more"); return false }
        runCatching { bt.cancelDiscovery() }
        val socket = ObdProbe.connect(dev) ?: return false
        try {
            val elm = ObdProbe.Elm(socket.inputStream, socket.outputStream)
            elm.cmd("ATZ", 3_000)
            for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) elm.cmd(c)
            // The first request makes the adapter search for the protocol; the ECU answers only with contact on.
            val first = elm.cmd("0100", 12_000)
            if (ObdDecode.supported(first, 0x00).isEmpty()) { log("live reader: the ECU didn't answer (contact off?)"); return false }
            val supported = ObdDecode.supported(first, 0x00).toSet()
            val pids = WANT.filter { it in supported }
            if (0x0C !in pids) { log("live reader: the ECU doesn't offer RPM"); return false }
            val mode = pickMode(elm, pids)
            elm.verbose = false   // from here on only summaries: a live reader must not flood the log
            log("live reader: reading ${pids.joinToString(", ") { ObdDecode.pid(it)!!.name }} · ${mode.label}")
            var round = 0
            var failed = 0
            while (!stopFlag) {
                val t0 = SystemClock.elapsedRealtime()
                val got = readRound(elm, mode, pids, round)
                val now = SystemClock.elapsedRealtime()
                if (got.isEmpty()) {
                    if (++failed >= 5) { log("live reader: 5 empty rounds in a row — reconnecting"); return true }
                } else {
                    failed = 0
                    val prev = latest
                    latest = Snapshot(
                        got[0x0C], got[0x0D] ?: prev?.speed, got[0x05] ?: prev?.coolant,
                        got[0x11] ?: prev?.throttle, now,
                    )
                    val rpm = got[0x0C]
                    if (rpm != null) {
                        val e = engineFromRpm(rpm)
                        if (e != engine) log("engine ${if (e) "ON" else "OFF"} at ${"%.0f".format(rpm)} rpm")
                        setEngine(e)
                    }
                }
                if (round % 40 == 0) LogBus.logThrottled("obd-live", "[OBD] live: ${describe(latest)} (${now - t0} ms/round)", 60_000)
                round++
            }
            return true
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun describe(s: Snapshot?): String = if (s == null) "-" else listOfNotNull(
        s.rpm?.let { "rpm ${"%.0f".format(it)}" }, s.speed?.let { "speed ${"%.0f".format(it)}" },
        s.coolant?.let { "coolant ${"%.0f".format(it)}" }, s.throttle?.let { "throttle ${"%.1f".format(it)}" },
    ).joinToString(" · ")

    /** One round of reads; coolant, which barely moves, only every 10th round in the one-PID modes. */
    private fun readRound(elm: ObdProbe.Elm, mode: Mode, pids: List<Int>, round: Int): Map<Int, Double> {
        if (mode == Mode.MULTI) {
            val cmd = "01" + pids.joinToString("") { "%02X".format(it) }
            return ObdDecode.multi(elm.cmd(cmd, 2_000), pids) ?: emptyMap()
        }
        val out = HashMap<Int, Double>()
        for (pid in pids) {
            if (pid == 0x05 && round % 10 != 0) continue
            val cmd = "01%02X".format(pid) + if (mode == Mode.SINGLE_COUNT) "1" else ""
            ObdDecode.value(elm.cmd(cmd, 2_000), pid)?.let { out[pid] = it }
        }
        return out
    }

    /**
     * Times three ways of asking for the same data and keeps the fastest one that returns everything:
     * plain, with the ELM327 "responses expected" digit, and all PIDs in one request. A clone that
     * doesn't like one of them simply fails that test and the plain way stays.
     */
    private fun pickMode(elm: ObdProbe.Elm, pids: List<Int>): Mode {
        var best = Mode.SINGLE
        var bestMs = Long.MAX_VALUE
        val report = ArrayList<String>()
        for (m in Mode.values()) {
            var ok = 0
            val t0 = SystemClock.elapsedRealtime()
            repeat(3) {
                val got = runCatching { readRound(elm, m, pids, 0) }.getOrDefault(emptyMap())
                if (got.keys.containsAll(pids)) ok++
            }
            val avg = (SystemClock.elapsedRealtime() - t0) / 3
            report += "${m.name} ${if (ok == 3) "$avg ms" else "failed $ok/3"}"
            if (ok == 3 && avg < bestMs) { best = m; bestMs = avg }
        }
        log("live reader: speed test · ${report.joinToString(" · ")} → ${best.name}")
        return best
    }
}
