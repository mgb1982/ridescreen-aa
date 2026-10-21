// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental probe of the dash's EasyConn "VAN" vehicle-data service over BLE (service
 * `0000b360-…`, the same HUD service the 125M uses for its clock). It:
 *  1. scans ~15 s for a device advertising the HUD service or an EasyConn-looking name,
 *  2. connects, dumps every service/characteristic to the log,
 *  3. enables notifications and logs every frame the dash sends,
 *  4. answers the dash's VAN handshake (0x31) with a subscription list and, if the dash stays silent
 *     for [QUIET_MS], volunteers that list itself (in "Insist" mode several variants one after
 *     another, reconnecting if the dash drops the link),
 *  5. decodes the pushed values.
 *
 * The only frames it can write are the official app's hello (cmd 0x30, a tiny JSON with a fixed probe label) and EC-BTP 0x31 subscription lists (the phone's half of the VAN
 * handshake): no control, lock, key, clock or firmware frame exists in this class. Every write goes
 * through one queue of ≤20-byte chunks, each waiting for the previous one to complete, and all
 * callbacks run on the main thread.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
internal class VanProbe(
    context: Context,
    private val mode: Mode,
    private val log: (String) -> Unit,
    private val onChange: () -> Unit,
) {
    enum class Mode { LISTEN_ONLY, OFFICIAL, OFFICIAL_PLUS_MILEAGE, FULL, INSIST }

    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private val stopped = AtomicBoolean(false)

    /** Lists sent in answer to the dash's own 0x31: the nth offer gets the nth list, the last one repeats. */
    private val replyPlan: List<ByteArray> = when (mode) {
        Mode.LISTEN_ONLY -> emptyList()
        Mode.OFFICIAL -> listOf(VanProtocol.OFFICIAL_ITEMS)
        Mode.OFFICIAL_PLUS_MILEAGE -> listOf(VanProtocol.OFFICIAL_PLUS_MILEAGE)
        Mode.FULL -> listOf(VanProtocol.FULL)
        Mode.INSIST -> VanProtocol.INSIST_REPLIES
    }

    private class Ask(val label: String, val frame: ByteArray)

    /** Frames volunteered, one per step, when the dash stays silent. Insist starts with the official app's own hello (cmd 0x30), plain then base64. */
    private val askPlan: List<Ask> = buildList {
        if (mode == Mode.INSIST) {
            add(Ask("hello 0x30 plain", VanProtocol.helloFrame(base64 = false)))
            add(Ask("hello 0x30 base64", VanProtocol.helloFrame(base64 = true)))
        }
        val lists = if (mode == Mode.INSIST) VanProtocol.INSIST_ASKS else replyPlan
        for (items in lists) add(Ask("subscribe 0x31 (${idList(items)})", VanProtocol.handshakeResponse(items)))
    }

    private var stream = VanProtocol.Stream()
    private var gatt: BluetoothGatt? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanCb: ScanCallback? = null
    private val seen = HashSet<String>()
    private var device: BluetoothDevice? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val pendingDescriptors = ArrayDeque<Pair<BluetoothGattCharacteristic, BluetoothGattDescriptor>>()
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writing = false
    private var writeRefusals = 0
    private var noResponseFallback = false

    // Per run (survive a reconnect).
    private var rung = 0
    private var reconnects = 0
    private var answered = false
    private var earlyDrop = false

    /** Consecutive failed attempts to establish a link (reset when one comes up), so not part of resetConnection. */
    private var connectRetries = 0

    // Per connection (reset in resetConnection).
    private var linkUp = false
    private var connectedAt = 0L
    private var txThisConn = false
    private var lastTxMono = 0L
    private var lastRxMono = 0L
    private var handshakeReplies = 0
    private var ladderArmed = false

    @Volatile var status: String = "…"
        private set
    @Volatile var offered: List<Int> = emptyList()
        private set
    @Volatile var sent: List<String> = emptyList()
        private set
    val values = ConcurrentHashMap<Int, VanProtocol.Field>()
    @Volatile var lastFrameAt: Long = 0L
        private set

    private val ladderTick = Runnable { tick() }
    private val writeWatchdog = Runnable {
        if (writing && !stopped.get()) {
            log("[VAN] write callback never came — moving on")
            writing = false
            gatt?.let { pumpWrites(it) }
        }
    }
    private val scanTimeout = Runnable {
        if (stopped.get() || gatt != null || device != null) return@Runnable
        scanCb?.let { cb -> runCatching { scanner?.stopScan(cb) } }
        log("[VAN] no dash found in 15 s (${seen.size} devices seen)")
        setStatus("Dash not found")
    }

    fun start() {
        val ad = adapter
        if (ad == null || !ad.isEnabled) { setStatus("Bluetooth off"); return }
        val sc = ad.bluetoothLeScanner
        if (sc == null) { setStatus("No BLE scanner"); return }
        scanner = sc
        setStatus("Scanning…")
        log("[VAN] start mode=$mode")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (stopped.get() || gatt != null || device != null) return
                val dev = result.device
                val name = result.scanRecord?.deviceName ?: runCatching { dev.name }.getOrNull()
                val services = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                if (seen.add(dev.address)) {
                    log("[VAN] seen ${mask(dev.address)} name=$name rssi=${result.rssi} services=${services.joinToString { it.toString().take(8) }}")
                }
                if (looksLikeDash(name, services)) {
                    runCatching { sc.stopScan(this) }
                    scanner = null
                    main.removeCallbacks(scanTimeout)
                    log("[VAN] candidate ${mask(dev.address)} name=$name — connecting")
                    connect(dev)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log("[VAN] scan failed $errorCode")
                setStatus("Scan failed ($errorCode)")
            }
        }
        scanCb = callback
        runCatching { sc.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback) }
            .onFailure { log("[VAN] startScan: ${it.message}"); setStatus("Scan failed") }
        main.postDelayed(scanTimeout, SCAN_MS)
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        main.removeCallbacksAndMessages(null)
        scanCb?.let { cb -> runCatching { scanner?.stopScan(cb) } }
        gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
        gatt = null
        writeQueue.clear()
        log("[VAN] stopped")
    }

    private fun looksLikeDash(name: String?, services: List<UUID>): Boolean {
        if (services.contains(HUD_SERVICE)) return true
        val n = name ?: return false
        return NAME_HINT.containsMatchIn(n)
    }

    private fun resetConnection() {
        main.removeCallbacks(ladderTick)
        main.removeCallbacks(writeWatchdog)
        stream = VanProtocol.Stream()
        pendingDescriptors.clear()
        writeQueue.clear()
        writing = false
        writeRefusals = 0
        writeChar = null
        linkUp = false
        connectedAt = 0L
        txThisConn = false
        lastTxMono = 0L
        lastRxMono = 0L
        handshakeReplies = 0
        ladderArmed = false
    }

    private fun connect(dev: BluetoothDevice) {
        device = dev
        resetConnection()
        setStatus("Connecting…")
        // Callbacks on the main looper, so the queue, timers and state below never race.
        gatt = runCatching {
            dev.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, main)
        }.getOrNull()
        if (gatt == null) { log("[VAN] connectGatt failed"); setStatus("Connect failed") }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            log("[VAN] connection state=$newState status=$st")
            if (stopped.get()) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                linkUp = true
                connectedAt = SystemClock.elapsedRealtime()
                connectRetries = 0
                setStatus("Connected")
                log("[VAN] bond state=${runCatching { g.device.bondState }.getOrNull()} (10 none, 12 bonded)")
                runCatching { g.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onLinkClosed(g, st)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            if (stopped.get()) return
            log("[VAN] services status=$st")
            var found: android.bluetooth.BluetoothGattService? = null
            for (s in g.services) {
                log("[VAN] service ${s.uuid}")
                for (c in s.characteristics) {
                    log("[VAN]   char ${c.uuid} props=0x${c.properties.toString(16)}")
                }
                if (s.uuid == HUD_SERVICE) found = s
            }
            val hud = found
            if (hud == null) {
                log("[VAN] no HUD service b360 on this device")
                setStatus("No b360 service")
                return
            }
            val notify = NOTIFY_CHARS.mapNotNull { hud.getCharacteristic(it) }
                .filter { it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 }
            // Same preference order as the official app: V3 (b364 notify+write), V2 (b363), V1 (b362).
            writeChar = WRITE_CHARS.mapNotNull { hud.getCharacteristic(it) }.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            }
            log("[VAN] notify=${notify.joinToString { it.uuid.toString().take(8) }} write=${writeChar?.uuid?.toString()?.take(8)}")
            for (c in notify) {
                runCatching { g.setCharacteristicNotification(c, true) }
                c.getDescriptor(CCC)?.let { pendingDescriptors.addLast(c to it) }
            }
            nextDescriptor(g)
            setStatus("Listening")
            // If a descriptor write never reports back, still start the clock for "dash is silent".
            main.postDelayed({ if (!stopped.get() && gatt === g) armLadder() }, DISCOVERY_GRACE_MS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            if (stopped.get()) return
            log("[VAN] notify enabled on ${d.characteristic.uuid.toString().take(8)} status=$st")
            nextDescriptor(g)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            if (stopped.get()) return
            main.removeCallbacks(writeWatchdog)
            writing = false
            log("[VAN] write done status=$st")
            if (st != BluetoothGatt.GATT_SUCCESS) {
                // 3 = write not permitted, 5/15 = the dash wants pairing/encryption, 133 = generic stack error.
                writeQueue.clear()
                if (!noResponseFallback && c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                    noResponseFallback = true
                    log("[VAN] will use write-without-response from now on")
                }
                return
            }
            pumpWrites(g)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            log("[VAN] software revision (status=$status) ${String(c.value ?: ByteArray(0), Charsets.UTF_8)}")
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            log("[VAN] software revision (status=$status) ${String(value, Charsets.UTF_8)}")
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handle(g, c.uuid, c.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handle(g, c.uuid, value)
        }
    }

    /** The link closed: retry a connection that never came up, or (Insist mode) reconnect and carry on with the next request. */
    private fun onLinkClosed(g: BluetoothGatt, st: Int) {
        val dev = device
        val now = SystemClock.elapsedRealtime()
        main.removeCallbacks(ladderTick)
        main.removeCallbacks(writeWatchdog)
        runCatching { g.close() }
        if (gatt === g) gatt = null
        if (!linkUp) {
            // Never connected: same policy as the official stack, up to 3 retries 200 ms apart.
            if (dev != null && connectRetries < 3) {
                connectRetries++
                log("[VAN] connect retry $connectRetries/3")
                main.postDelayed({ if (!stopped.get()) connect(dev) }, 200)
            } else {
                setStatus("Disconnected ($st)")
            }
            return
        }
        val up = now - connectedAt
        val after = if (txThisConn) "${now - lastTxMono} ms after our last frame" else "before we sent anything"
        log("[VAN] link dropped (status=$st) $up ms after connecting, $after")
        earlyDrop = !txThisConn
        if (dev != null && canResume()) {
            reconnects++
            log("[VAN] reconnecting ($reconnects/$MAX_RECONNECTS), next request is #${rung + 1}" + if (earlyDrop) ", asked right away this time" else "")
            setStatus("Reconnecting…")
            main.postDelayed({ if (!stopped.get()) connect(dev) }, RECONNECT_DELAY_MS)
        } else {
            setStatus("Disconnected ($st)")
        }
    }

    private fun canResume(): Boolean =
        mode == Mode.INSIST && reconnects < MAX_RECONNECTS && rung < askPlan.size && !answered && values.isEmpty()

    private fun readRevision(g: BluetoothGatt) {
        val c = g.services.firstNotNullOfOrNull { it.getCharacteristic(SOFTWARE_REVISION) } ?: return
        runCatching { g.readCharacteristic(c) }.onFailure { log("[VAN] read revision: ${it.message}") }
    }

    private fun nextDescriptor(g: BluetoothGatt) {
        val next = pendingDescriptors.removeFirstOrNull()
        if (next == null) { readRevision(g); armLadder(); return }
        val (c, d) = next
        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, value)
            } else {
                d.value = value
                g.writeDescriptor(d)
            }
        }.onFailure { log("[VAN] writeDescriptor: ${it.message}") }
    }

    /** Starts the "is the dash going to speak first?" clock, once per connection. */
    private fun armLadder() {
        if (ladderArmed || stopped.get()) return
        ladderArmed = true
        if (askPlan.isEmpty()) { log("[VAN] listen only — nothing will be sent"); return }
        if (rung >= askPlan.size || answered) return
        val wait = if (earlyDrop) EARLY_QUIET_MS else if (mode == Mode.INSIST) HELLO_QUIET_MS else QUIET_MS
        log("[VAN] listening; if the dash stays silent for ${wait / 1000.0} s the probe asks for the data itself")
        main.postDelayed(ladderTick, wait)
    }

    /** The dash has been silent: volunteer the next subscription list, then give it [RESPONSE_MS] to react. */
    private fun tick() {
        if (stopped.get()) return
        val g = gatt ?: return
        if (answered || values.isNotEmpty()) return
        if (lastTxMono > 0 && lastRxMono > lastTxMono) {
            log("[VAN] the dash replied after our last frame — not sending more")
            return
        }
        if (rung >= askPlan.size) {
            log("[VAN] $rung request(s) sent and the dash never answered")
            setStatus("Dash silent after $rung request(s)")
            return
        }
        val ask = askPlan[rung]
        rung++
        lastTxMono = SystemClock.elapsedRealtime()
        txThisConn = true
        note("#$rung ${ask.label}")
        send(g, ask.frame, "ask #$rung (${ask.label})")
        setStatus("Asked the dash (#$rung)")
        main.postDelayed(ladderTick, RESPONSE_MS)
    }

    private fun handle(g: BluetoothGatt, from: UUID, value: ByteArray) {
        if (stopped.get()) return
        lastFrameAt = System.currentTimeMillis()
        lastRxMono = SystemClock.elapsedRealtime()
        log("[VAN] rx ${from.toString().take(8)} ${VanProtocol.hex(value)}")
        for (f in stream.feed(value)) {
            val cmd = f.command.toInt() and 0xFF
            when (f.command) {
                VanProtocol.CMD_MODEL -> logModel(f.payload)
                VanProtocol.CMD_HANDSHAKE -> onHandshake(g, f.payload)
                VanProtocol.CMD_REALTIME -> record("rt", VanProtocol.parseRealtime(f.payload))
                VanProtocol.CMD_NON_REALTIME -> record("slow", VanProtocol.parseNonRealtime(f.payload))
                else -> log("[VAN] cmd=0x${cmd.toString(16)} payload=${VanProtocol.hex(f.payload)} (not answered)")
            }
        }
        onChange()
    }

    private fun logModel(payload: ByteArray) {
        val text = runCatching { String(payload, Charsets.UTF_8) }.getOrDefault("")
        val shown = text.replace(Regex("\"sn\"\\s*:\\s*\"([^\"]*)\"")) { m ->
            val sn = m.groupValues[1]
            "\"sn\":\"…${sn.takeLast(4)}\""
        }.replace(Regex("\"gpsid\"\\s*:\\s*\"[^\"]*\""), "\"gpsid\":\"…\"")
        log("[VAN] model $shown")
    }

    private fun onHandshake(g: BluetoothGatt, payload: ByteArray) {
        offered = VanProtocol.handshakeItems(payload)
        log("[VAN] dash offers ${offered.joinToString { "${it}=${VanProtocol.name(it)}" }}")
        if (replyPlan.isEmpty()) { log("[VAN] listen only — handshake not answered"); return }
        if (handshakeReplies >= MAX_HANDSHAKE_REPLIES) {
            log("[VAN] already answered $handshakeReplies times on this connection — not answering again")
            return
        }
        val items = replyPlan[minOf(handshakeReplies, replyPlan.size - 1)]
        handshakeReplies++
        answered = true
        main.removeCallbacks(ladderTick)
        lastTxMono = SystemClock.elapsedRealtime()
        txThisConn = true
        note("reply ${idList(items)}")
        send(g, VanProtocol.handshakeResponse(items), "subscribe (${idList(items)})")
        setStatus("Subscribed")
    }

    /** Queues a frame as ≤20-byte writes; they go out one at a time, each after the previous one completed. */
    private fun send(g: BluetoothGatt, frame: ByteArray, label: String) {
        if (writeChar == null) { log("[VAN] no write characteristic — cannot send $label"); return }
        log("[VAN] $label → ${VanProtocol.hex(frame)}")
        for (chunk in VanProtocol.chunks(frame)) writeQueue.addLast(chunk)
        pumpWrites(g)
    }

    private fun pumpWrites(g: BluetoothGatt) {
        if (writing) return
        val target = writeChar ?: return
        val chunk = writeQueue.removeFirstOrNull() ?: return
        // The official stack writes with response (WRITE_TYPE_DEFAULT); no-response only if the dash refused that.
        val withResponse = !noResponseFallback && target.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        val type = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        val started = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(target, chunk, type) == 0 // BluetoothStatusCodes.SUCCESS
            } else {
                target.writeType = type
                target.value = chunk
                g.writeCharacteristic(target)
            }
        }.getOrDefault(false)
        if (started) {
            writing = true
            writeRefusals = 0
            main.postDelayed(writeWatchdog, WRITE_TIMEOUT_MS)
            log("[VAN] tx ${chunk.size}B ${VanProtocol.hex(chunk)} (${if (withResponse) "with" else "without"} response)")
        } else if (writeRefusals < MAX_WRITE_REFUSALS) {
            // Usually another GATT operation (the revision read) is still in flight: try again shortly.
            writeRefusals++
            writeQueue.addFirst(chunk)
            log("[VAN] the stack is busy — retrying the write ($writeRefusals/$MAX_WRITE_REFUSALS)")
            main.postDelayed({ if (!stopped.get()) pumpWrites(g) }, WRITE_RETRY_MS)
        } else {
            log("[VAN] the stack refused the write — dropping ${writeQueue.size + 1} chunk(s)")
            writeRefusals = 0
            writeQueue.clear()
        }
    }

    private fun record(tag: String, fields: List<VanProtocol.Field>) {
        for (f in fields) {
            values[f.id] = f
            log("[VAN] $tag ${VanProtocol.name(f.id)}(${f.id}) = ${f.raw} (0x${java.lang.Long.toHexString(f.raw and 0xFFFFFFFFL)}) → ${VanProtocol.describe(f)}")
        }
        if (fields.isEmpty()) log("[VAN] $tag frame with no decodable field")
    }

    private fun idList(items: ByteArray): String =
        if (items.isEmpty()) "empty list" else items.joinToString(",") { "${it.toInt() and 0xFF}" }

    private fun note(text: String) {
        sent = (sent + text).takeLast(6)
    }

    private fun setStatus(s: String) {
        status = s
        main.post { onChange() }
    }

    private fun mask(address: String) = "…" + address.takeLast(5)

    companion object {
        private const val SCAN_MS = 15_000L

        /** Silence after the notifications are on before the probe asks for the data itself. */
        private const val QUIET_MS = 6_000L

        /** Insist: the official app sends its hello 1 s after services are discovered, so wait about as long. */
        private const val HELLO_QUIET_MS = 1_500L

        /** After a drop with nothing sent, ask almost at once: the dash may expect a first word within seconds. */
        private const val EARLY_QUIET_MS = 300L

        /** Time the dash gets to react to a volunteered request before the next one. */
        private const val RESPONSE_MS = 8_000L
        private const val WRITE_TIMEOUT_MS = 2_000L
        private const val WRITE_RETRY_MS = 400L
        private const val MAX_WRITE_REFUSALS = 3
        private const val DISCOVERY_GRACE_MS = 4_000L
        private const val RECONNECT_DELAY_MS = 1_500L
        private const val MAX_RECONNECTS = 3
        private const val MAX_HANDSHAKE_REPLIES = 3
        private val HUD_SERVICE: UUID = UUID.fromString("0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc")
        private val NOTIFY_CHARS = listOf(
            UUID.fromString("0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
            UUID.fromString("0000b364-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
        )
        private val WRITE_CHARS = listOf(
            UUID.fromString("0000b364-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
            UUID.fromString("0000b363-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
            UUID.fromString("0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
        )
        private val SOFTWARE_REVISION: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
        private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val NAME_HINT = Regex("^(EC[0-9A-Fa-f]{6,}|ZT[_-]?.*|ZONTES.*|CQKY.*)$", RegexOption.IGNORE_CASE)
    }
}
