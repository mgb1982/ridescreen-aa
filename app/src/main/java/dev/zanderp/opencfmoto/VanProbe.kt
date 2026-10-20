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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental, read-only probe of the dash's EasyConn "VAN" vehicle-data service over BLE
 * (service `0000b360-…`, the same HUD service the 125M uses for its clock). It:
 *  1. scans ~15 s for a device advertising the HUD service or an EasyConn-looking name,
 *  2. connects, dumps every service/characteristic to the log,
 *  3. listens to the notify characteristics and logs every frame,
 *  4. answers the dash's VAN handshake (0x31) with a subscription list — only in the modes that
 *     ask for it — and decodes the pushed values.
 * It never writes anything else: no control, lock, key or firmware frames exist in this class.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
internal class VanProbe(
    context: Context,
    private val mode: Mode,
    private val log: (String) -> Unit,
    private val onChange: () -> Unit,
) {
    enum class Mode { LISTEN_ONLY, OFFICIAL, OFFICIAL_PLUS_MILEAGE }

    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private val stopped = AtomicBoolean(false)
    private val stream = VanProtocol.Stream()
    private var gatt: BluetoothGatt? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanCb: ScanCallback? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val pendingDescriptors = ArrayDeque<Pair<BluetoothGattCharacteristic, BluetoothGattDescriptor>>()
    private var answered = false

    @Volatile var status: String = "…"
        private set
    @Volatile var offered: List<Int> = emptyList()
        private set
    val values = ConcurrentHashMap<Int, VanProtocol.Field>()
    @Volatile var lastFrameAt: Long = 0L
        private set

    fun start() {
        val ad = adapter
        if (ad == null || !ad.isEnabled) { setStatus("Bluetooth off"); return }
        val sc = ad.bluetoothLeScanner
        if (sc == null) { setStatus("No BLE scanner"); return }
        scanner = sc
        setStatus("Scanning…")
        log("[VAN] start mode=$mode")
        val seen = HashSet<String>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (stopped.get() || gatt != null) return
                val dev = result.device
                val name = result.scanRecord?.deviceName ?: runCatching { dev.name }.getOrNull()
                val services = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                if (seen.add(dev.address)) {
                    log("[VAN] seen ${mask(dev.address)} name=$name rssi=${result.rssi} services=${services.joinToString { it.toString().take(8) }}")
                }
                if (looksLikeDash(name, services)) {
                    runCatching { sc.stopScan(this) }
                    scanner = null
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
        main.postDelayed({
            if (!stopped.get() && gatt == null) {
                runCatching { sc.stopScan(callback) }
                log("[VAN] no dash found in 15 s (${seen.size} devices seen)")
                setStatus("Dash not found")
            }
        }, SCAN_MS)
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        main.removeCallbacksAndMessages(null)
        scanCb?.let { cb -> runCatching { scanner?.stopScan(cb) } }
        gatt?.let { g -> runCatching { g.disconnect() }; runCatching { g.close() } }
        gatt = null
        log("[VAN] stopped")
    }

    private fun looksLikeDash(name: String?, services: List<UUID>): Boolean {
        if (services.contains(HUD_SERVICE)) return true
        val n = name ?: return false
        return NAME_HINT.containsMatchIn(n)
    }

    private fun connect(dev: BluetoothDevice) {
        setStatus("Connecting…")
        gatt = runCatching { dev.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
        if (gatt == null) { log("[VAN] connectGatt failed"); setStatus("Connect failed") }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            log("[VAN] connection state=$newState status=$st")
            if (newState == BluetoothProfile.STATE_CONNECTED && !stopped.get()) {
                setStatus("Connected")
                runCatching { g.discoverServices() }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setStatus("Disconnected ($st)")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
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
            writeChar = WRITE_CHARS.firstNotNullOfOrNull { hud.getCharacteristic(it) }
            log("[VAN] notify=${notify.joinToString { it.uuid.toString().take(8) }} write=${writeChar?.uuid?.toString()?.take(8)}")
            for (c in notify) {
                runCatching { g.setCharacteristicNotification(c, true) }
                c.getDescriptor(CCC)?.let { pendingDescriptors.addLast(c to it) }
            }
            nextDescriptor(g)
            setStatus("Listening")
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            log("[VAN] notify enabled on ${d.characteristic.uuid.toString().take(8)} status=$st")
            nextDescriptor(g)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            handle(g, c.uuid, c.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handle(g, c.uuid, value)
        }
    }

    private fun nextDescriptor(g: BluetoothGatt) {
        val next = pendingDescriptors.removeFirstOrNull() ?: return
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

    private fun handle(g: BluetoothGatt, from: UUID, value: ByteArray) {
        if (stopped.get()) return
        lastFrameAt = System.currentTimeMillis()
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
        val items = when (mode) {
            Mode.LISTEN_ONLY -> null
            Mode.OFFICIAL -> VanProtocol.OFFICIAL_ITEMS
            Mode.OFFICIAL_PLUS_MILEAGE -> VanProtocol.OFFICIAL_PLUS_MILEAGE
        }
        if (items == null) { log("[VAN] listen only — handshake not answered"); return }
        val target = writeChar
        if (target == null) { log("[VAN] no write characteristic — cannot subscribe"); return }
        val frame = VanProtocol.handshakeResponse(items)
        answered = true
        log("[VAN] subscribe ${items.joinToString { "${it.toInt()}" }} → ${VanProtocol.hex(frame)}")
        val type = if (target.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        var offset = 0
        while (offset < frame.size) {
            val chunk = frame.copyOfRange(offset, minOf(offset + 20, frame.size))
            val r = runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(target, chunk, type)
                } else {
                    target.writeType = type
                    target.value = chunk
                    g.writeCharacteristic(target)
                }
            }.getOrNull()
            log("[VAN] tx ${chunk.size}B result=$r")
            offset += chunk.size
        }
    }

    private fun record(tag: String, fields: List<VanProtocol.Field>) {
        for (f in fields) {
            values[f.id] = f
            log("[VAN] $tag ${VanProtocol.name(f.id)}(${f.id}) = ${f.raw} (0x${java.lang.Long.toHexString(f.raw and 0xFFFFFFFFL)})")
        }
        if (fields.isEmpty()) log("[VAN] $tag frame with no decodable field")
    }

    private fun setStatus(s: String) {
        status = s
        main.post { onChange() }
    }

    private fun mask(address: String) = "…" + address.takeLast(5)

    companion object {
        private const val SCAN_MS = 15_000L
        private val HUD_SERVICE: UUID = UUID.fromString("0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc")
        private val NOTIFY_CHARS = listOf(
            UUID.fromString("0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
            UUID.fromString("0000b364-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
        )
        private val WRITE_CHARS = listOf(
            UUID.fromString("0000b363-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
            UUID.fromString("0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc"),
        )
        private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val NAME_HINT = Regex("^(EC[0-9A-Fa-f]{6,}|ZT[_-]?.*|ZONTES.*|CQKY.*)$", RegexOption.IGNORE_CASE)
    }
}
