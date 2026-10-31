// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * v2.3 · Battery voltage (and lock state) of the bike, read from its keyless box (Zontes "PKE 3.0")
 * over Bluetooth LE — the same "voltage" Zontes Smart EU 1.12 shows (decompiled: MotorCtrlFragment,
 * a.x44, a.jc9; 20 Oct 2026).
 *  - The box advertises; its scan record, as hex, contains the bike's PKE code (x44: hex + contains).
 *  - Nordic UART service 6E400001: write 6E400002, notify 6E400003. ASCII frames `*…#`.
 *  - `*BT,<pke>,10,008#`   → `*AT,…,…,008,<challenge>,<volts×10>…#`
 *  - `*BT,<pke>,10,001,7#` → `*AT,…,…,001,7,<1 locked | 2 unlocked>#`
 *
 * READ ONLY by design (Manel, 20 Oct 2026: "values from that box, read only"). [frame] builds only
 * those two queries; there is no code path that sends lock / unlock / seat / fuel cap / keys /
 * password / firmware commands, and the challenge is never answered.
 */
@SuppressLint("MissingPermission")
object PkeBattery {
    private val UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val UART_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // phone → box
    private val UART_TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // box → phone
    private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private const val PREFS = "pke_box"
    private const val KEY_CODE = "code"
    private const val KEY_MAC = "mac"
    private const val KEY_VOLTS = "volts"
    private const val KEY_LOCKED = "locked"
    private const val KEY_AT = "at"
    private const val KEY_VOLTS_AT = "volts_at"

    /** The only two things RideScreen ever asks the box. */
    internal enum class Query(val cmd: String) { HANDSHAKE("008"), STATUS("001,7") }

    /** [voltsAtMs] is when the voltage itself was read: a later lock-state-only reading keeps it. */
    data class Reading(val volts: Float?, val locked: Boolean?, val atMs: Long, val voltsAtMs: Long = atMs)

    enum class Failure { NO_CODE, NO_PERMISSION, BLUETOOTH_OFF, NOT_FOUND, NO_LINK, NO_ANSWER }

    class Result(val reading: Reading?, val failure: Failure?)

    private fun log(s: String) = LogBus.log("[PKE] $s")

    // ── pure bits (unit-tested)

    /** Digits only; the code Zontes Smart shows under "vehicle management" (15 digits on the 125X). */
    internal fun cleanCode(raw: String?): String = raw.orEmpty().filter { it.isDigit() }

    internal fun frame(pke: String, q: Query): ByteArray = "*BT,$pke,10,${q.cmd}#".toByteArray(Charsets.US_ASCII)

    /** Hex of the advertisement contains the PKE code; or the box is named "PKE-<last 4>". */
    internal fun matches(scanRecord: ByteArray?, name: String?, pke: String): Boolean {
        if (pke.length < 8) return false
        if (scanRecord != null && scanRecord.joinToString("") { "%02x".format(it) }.contains(pke)) return true
        val n = name.orEmpty().uppercase()
        return n.contains(pke) || n == "PKE-" + pke.takeLast(4)
    }

    /** Voltage from a `008` reply (`*AT,a,b,008,<challenge>,<volts×10>…#`), sanity-checked. */
    internal fun voltsFrom(reply: String): Float? {
        val p = reply.trim().removeSuffix("#").split(",")
        if (p.firstOrNull() != "*AT") return null
        val i = p.indexOf("008").takeIf { it >= 1 } ?: return null
        val v = p.getOrNull(i + 2)?.trim()?.toFloatOrNull() ?: return null
        return (v / 10f).takeIf { it in 5f..20f }
    }

    /** Lock state from a `001,7` reply: true locked, false unlocked, null unknown. */
    internal fun lockedFrom(reply: String): Boolean? {
        val p = reply.trim().removeSuffix("#").split(",")
        if (p.firstOrNull() != "*AT") return null
        val i = p.indexOf("001").takeIf { it >= 1 } ?: return null
        if (p.getOrNull(i + 1) != "7") return null
        return when (p.getOrNull(i + 2)?.trim()) { "1" -> true; "2" -> false; else -> null }
    }

    /** Frames as they appear in the log: PKE code down to its last 4 digits, challenge hidden. */
    internal fun masked(text: String, pke: String): String {
        var t = if (pke.length >= 8) text.replace(pke, "…" + pke.takeLast(4)) else text
        val p = t.split(",")
        val i = p.indexOf("008")
        if (p.firstOrNull() == "*AT" && i >= 1 && p.size > i + 1) t = p.toMutableList().also { it[i + 1] = "••••" }.joinToString(",")
        return t
    }

    // ── storage

    fun code(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CODE, "").orEmpty()

    fun setCode(ctx: Context, code: String) {
        val c = cleanCode(code)
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (c != p.getString(KEY_CODE, "")) p.edit().putString(KEY_CODE, c).remove(KEY_MAC).apply()
    }

    fun last(ctx: Context): Reading? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong(KEY_AT, 0L).takeIf { it > 0 } ?: return null
        val v = p.getFloat(KEY_VOLTS, -1f).takeIf { it > 0 }
        val l = if (p.contains(KEY_LOCKED)) p.getBoolean(KEY_LOCKED, false) else null
        return Reading(v, l, at, p.getLong(KEY_VOLTS_AT, at))
    }

    private fun save(ctx: Context, r: Reading) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_AT, r.atMs)
            if (r.volts != null) putFloat(KEY_VOLTS, r.volts).putLong(KEY_VOLTS_AT, r.voltsAtMs) else remove(KEY_VOLTS).remove(KEY_VOLTS_AT)
            if (r.locked != null) putBoolean(KEY_LOCKED, r.locked) else remove(KEY_LOCKED)
        }.apply()
    }

    // ── the read (blocking: call it off the main thread)

    fun read(ctx: Context): Result {
        val pke = code(ctx)
        if (pke.length < 8) return Result(null, Failure.NO_CODE)
        if (Build.VERSION.SDK_INT >= 31 && listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                .any { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }) {
            return Result(null, Failure.NO_PERMISSION)
        }
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return Result(null, Failure.BLUETOOTH_OFF)
        log("reading the keyless box (read only: 008 + 001,7) for PKE …${pke.takeLast(4)}")
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = prefs.getString(KEY_MAC, null)?.let { runCatching { adapter.getRemoteDevice(it) }.getOrNull() }
        var link = known?.let { log("trying the box at ${it.address} directly"); open(ctx, it, pke, 8_000) }
        if (link == null) {
            val dev = scan(ctx, pke) ?: return Result(null, Failure.NOT_FOUND)
            prefs.edit().putString(KEY_MAC, dev.address).apply()
            link = open(ctx, dev, pke, 12_000) ?: return Result(null, Failure.NO_LINK)
        }
        try {
            val volts = ask(link, pke, Query.HANDSHAKE, 6_000)?.let { voltsFrom(it) }
            val locked = ask(link, pke, Query.STATUS, 4_000)?.let { lockedFrom(it) }
            if (volts == null && locked == null) return Result(null, Failure.NO_ANSWER)
            val r = Reading(volts, locked, System.currentTimeMillis())
            save(ctx, r)
            volts?.let { noteVolts(it, verbose = true) }
            log("battery ${volts?.let { "%.1f V".format(it) } ?: "?"} · ${when (locked) { true -> "locked"; false -> "unlocked"; null -> "lock state ?" }}")
            return Result(r, null)
        } finally {
            runCatching { link.gatt.disconnect() }; runCatching { link.gatt.close() }
        }
    }

    internal class Link(val gatt: BluetoothGatt, val rx: BluetoothGattCharacteristic, val replies: LinkedBlockingQueue<String>) {
        @Volatile var dead = false
    }

    /** Sends one allowed query and waits for the reply to it (other frames are logged and skipped). */
    private fun ask(link: Link, pke: String, q: Query, timeoutMs: Long, quiet: Boolean = false): String? {
        link.replies.clear()
        val bytes = frame(pke, q)
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            link.gatt.writeCharacteristic(link.rx, bytes, writeType(link.rx)) == 0
        } else {
            @Suppress("DEPRECATION") run { link.rx.writeType = writeType(link.rx); link.rx.value = bytes; link.gatt.writeCharacteristic(link.rx) }
        }
        if (!quiet || !ok) log("→ ${masked(String(bytes, Charsets.US_ASCII), pke)}${if (ok) "" else " (write refused)"}")
        val want = q.cmd.substringBefore(",")
        val until = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = until - System.currentTimeMillis()
            if (left <= 0) { log("no answer to ${q.cmd}"); return null }
            val r = link.replies.poll(left, TimeUnit.MILLISECONDS) ?: continue
            if (r.split(",").getOrNull(3) == want || r.split(",").contains(want)) return r
        }
    }

    private fun writeType(c: BluetoothGattCharacteristic) =
        if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

    private fun scan(ctx: Context, pke: String): BluetoothDevice? {
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return null
        var found: BluetoothDevice? = null
        var seen = 0
        var uart = 0
        val latch = CountDownLatch(1)
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                seen++
                val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull()
                if (r.scanRecord?.serviceUuids?.any { it.uuid == UART_SERVICE } == true) uart++
                if (found == null && matches(r.scanRecord?.bytes, name, pke)) {
                    found = r.device
                    log("found the box: ${r.device.address} '${name ?: "?"}' ${r.rssi} dBm")
                    latch.countDown()
                }
            }
        }
        log("scanning Bluetooth LE for the keyless box (12 s)…")
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
            latch.await(12, TimeUnit.SECONDS)
        } catch (e: Exception) { log("scan failed: $e") } finally { runCatching { scanner.stopScan(cb) } }
        if (found == null) log("box not found ($seen BLE adverts seen, $uart with the Nordic UART service). Too far, or the official app holding it?")
        return found
    }

    private fun open(ctx: Context, dev: BluetoothDevice, pke: String, timeoutMs: Long, verbose: Boolean = true): Link? {
        val ready = CountDownLatch(1)
        val replies = LinkedBlockingQueue<String>()
        val text = StringBuilder()
        var link: Link? = null
        var fail: String? = null
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!g.requestMtu(185)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (link == null) { fail = "disconnected (status=$status)"; ready.countDown() }
                    else { link?.dead = true; if (!verbose) log("live link dropped by the box (status=$status)") }
                }
            }
            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val svc = g.getService(UART_SERVICE)
                val rx = svc?.getCharacteristic(UART_RX)
                val tx = svc?.getCharacteristic(UART_TX)
                if (rx == null || tx == null) { fail = "no Nordic UART service"; ready.countDown(); return }
                g.setCharacteristicNotification(tx, true)
                val d = tx.getDescriptor(CCC)
                if (d == null) { link = Link(g, rx, replies); ready.countDown(); return }
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else @Suppress("DEPRECATION") run { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
                link = Link(g, rx, replies)
            }
            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { ready.countDown() }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = got(value)
            @Deprecated("pre-33")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION") got(c.value ?: return)
            }
            private fun got(value: ByteArray) {
                synchronized(text) {
                    text.append(String(value, Charsets.US_ASCII))
                    while (true) {
                        val start = text.indexOf("*")
                        val end = text.indexOf("#", maxOf(start, 0))
                        if (start < 0 || end < 0) break
                        val f = text.substring(start, end + 1)
                        text.delete(0, end + 1)
                        if (verbose) log("← ${masked(f, pke)}")
                        replies.put(f)
                    }
                    if (text.length > 512) text.setLength(0)
                }
            }
        }
        val g = runCatching { dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) }.getOrNull() ?: return null
        if (!ready.await(timeoutMs, TimeUnit.MILLISECONDS)) fail = fail ?: "timeout"
        val l = link
        if (l == null) {
            log("no link to the box: $fail")
            runCatching { g.disconnect() }; runCatching { g.close() }
            return null
        }
        return l
    }

    // ── v2.4: live mode — ONE connection kept open while projecting, polling the voltage every couple
    // of seconds. Same two read-only queries; the challenge is never answered.

    // ── engine running, inferred from the voltage. Lives here, not in the Activity, so every reading
    // (15 s card, live voltage, Help → Battery, widget) updates it (case 20261030: 14.7 V read, card
    // still "stopped"). The charging system lifts the battery above ~13.2 V; once the engine stops it
    // falls back below ~12.8 V. One charging reading turns it ON (a parked bike can't produce it);
    // OFF takes two in a row so a load dip doesn't flicker it (one is enough while still unknown).

    const val ENGINE_ON_V = 13.2f
    const val ENGINE_OFF_V = 12.8f

    internal data class Engine(val running: Boolean?, val hi: Int, val lo: Int)

    internal fun nextEngine(e: Engine, v: Float): Engine {
        val hi = if (v >= ENGINE_ON_V) e.hi + 1 else 0
        val lo = if (v <= ENGINE_OFF_V) e.lo + 1 else 0
        var running = e.running
        if (hi >= 1) running = true
        if (lo >= 2 || (lo >= 1 && running == null)) running = false
        return Engine(running, hi, lo)
    }

    @Volatile private var engine = Engine(null, 0, 0)
    val engineRunning: Boolean? get() = engine.running

    /** Called on the thread that read the voltage; the Activity hops to the UI thread itself. */
    @Volatile var onEngineChanged: (() -> Unit)? = null

    @Synchronized
    fun noteVolts(v: Float, verbose: Boolean = false) {
        val before = engine.running
        engine = nextEngine(engine, v)
        if (engine.running != before) {
            log("engine ${if (engine.running == true) "ON" else "OFF"} at ${"%.1f".format(v)} V")
            onEngineChanged?.invoke()
        } else if (verbose) {
            log("engine still ${when (engine.running) { true -> "ON"; false -> "OFF"; null -> "unknown" }} at ${"%.1f".format(v)} V")
        }
    }

    /** True / false from a live OBD RPM if there is one, else from a voltage read under 3 min ago, else null. */
    fun engineState(ctx: Context): Boolean? {
        // A live OBD reading (real RPM) beats guessing from the battery voltage.
        ObdLive.engineRunning()?.let { return it }
        val r = last(ctx) ?: return null
        return if (System.currentTimeMillis() - r.voltsAtMs <= 3 * 60_000L) engine.running else null
    }

    // The box takes one client and stops advertising while it has one, so a command (PkeUnlock) can't
    // even find it while the live link is open (case 20261029: four 'box not found' while projecting).
    // [liveOpen] lets PkeUnlock see that link from outside the Activity; [wakeLive] cuts the live loop's
    // pause short so it hands the box over at once; [awaitLiveClosed] waits for that.
    private val liveSignal = java.lang.Object()

    @Volatile var liveOpen = false
        private set

    fun wakeLive() = synchronized(liveSignal) { liveSignal.notifyAll() }

    /** The live loop's pause between reads: up to [ms], ended early by [wakeLive] or when [stop] is true. */
    fun livePause(ms: Long, stop: () -> Boolean) {
        if (ms <= 0) return
        synchronized(liveSignal) { if (!stop()) liveSignal.wait(ms) }
    }

    /** Waits up to [ms] for the live link to close. True if it is closed. */
    fun awaitLiveClosed(ms: Long): Boolean {
        val until = android.os.SystemClock.elapsedRealtime() + ms
        synchronized(liveSignal) {
            while (liveOpen) {
                val left = until - android.os.SystemClock.elapsedRealtime()
                if (left <= 0) return false
                liveSignal.wait(left)
            }
        }
        return true
    }

    /** Stores a lock state read by someone else (PkeUnlock's check after a command), like a normal reading. */
    fun noteLocked(ctx: Context, locked: Boolean) {
        val prev = last(ctx)
        save(ctx, Reading(prev?.volts, locked, System.currentTimeMillis(), prev?.voltsAtMs ?: 0L))
    }

    class Live internal constructor(private val ctx: Context, private val pke: String, private val link: Link) {
        val alive: Boolean get() = !link.dead
        /** Voltage now (null if the box did not answer in 3 s). Saved like a normal reading. */
        fun volts(): Float? {
            val v = ask(link, pke, Query.HANDSHAKE, 3_000, quiet = true)?.let { voltsFrom(it) } ?: return null
            save(ctx, Reading(v, last(ctx)?.locked, System.currentTimeMillis()))
            noteVolts(v)
            return v
        }
        /** Lock state (true locked / false unlocked / null unknown). */
        fun locked(): Boolean? {
            val l = ask(link, pke, Query.STATUS, 3_000, quiet = true)?.let { lockedFrom(it) } ?: return null
            val prev = last(ctx)
            save(ctx, Reading(prev?.volts, l, System.currentTimeMillis(), prev?.voltsAtMs ?: 0L))
            return l
        }
        fun close() {
            runCatching { link.gatt.disconnect() }; runCatching { link.gatt.close() }
            synchronized(liveSignal) { liveOpen = false; liveSignal.notifyAll() }
        }
    }

    /** Blocking. Opens the persistent link (known address first, else scan). Null if it cannot (reason in the log). */
    fun openLive(ctx: Context): Live? {
        val pke = code(ctx)
        if (pke.length < 8) return null
        if (Build.VERSION.SDK_INT >= 31 && listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                .any { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }) return null
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return null
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = prefs.getString(KEY_MAC, null)?.let { runCatching { adapter.getRemoteDevice(it) }.getOrNull() }
        var link = known?.let { open(ctx, it, pke, 8_000, verbose = false) }
        if (link == null) {
            val dev = scan(ctx, pke) ?: return null
            prefs.edit().putString(KEY_MAC, dev.address).apply()
            link = open(ctx, dev, pke, 12_000, verbose = false) ?: return null
        }
        liveOpen = true
        log("live link open (read only: 008 + 001,7)")
        return Live(ctx, pke, link)
    }
}
