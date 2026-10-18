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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * v2.3 · Carbit "phone hotspot" dashes that don't offer the classic-Bluetooth service (Zontes 125M,
 * 18 Oct 2026 log: SDP only lists AVRCP + hands-free). Carbit Ride's BleNetHelper does the same job
 * over **BLE**, with the EC-BTP framing we already use for the dash clock ([EcBtpProtocol]):
 *  - phone → dash `0x50` build-net request (empty);
 *  - dash → phone `0x52` BuildNetStatusInfo JSON {status, phoneApFrequency, carNetDeviceInfo…};
 *  - phone → dash `0x52` PhoneApInfo JSON {"ssid","pwd","auth","mac","ip"} of the phone's own
 *    local-only hotspot;
 *  - dash → phone `0x53` NotifyCarNetInfo {cnt, data:[{name, ip, mask}]} once it has joined.
 * Everything the dash sends is logged (passwords masked) so a field log shows each step.
 */
@SuppressLint("MissingPermission")
object CarbitBleNet {
    private const val CMD_BUILD_NET: Byte = 0x50
    private const val CMD_REQUEST_BUILD_NET: Byte = 0x52
    private const val CMD_NOTIFY_CAR_NET: Byte = 0x53

    // Carbit "HudNSManager" (jj.a in Carbit Ride 2.4): the 125M exposes only this one (18 Oct log).
    private val HUD_SERVICE: UUID = UUID.fromString("0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc")
    private val HUD_CHARS = listOf("0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc", "0000b363-d6d8-c7ec-bdf0-eab1bfc6bcbc", "0000b364-d6d8-c7ec-bdf0-eab1bfc6bcbc").map(UUID::fromString)
    private val SERVICE_UUIDS = listOf(
        "0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc",
        "00001c00-d102-11e1-9b23-000efb0000b2", "0000474d-0000-1000-8000-00805f9b34fb",
        "00001c00-d102-11e1-9b23-000efb0000c3", "0000474e-0000-1000-8000-00805f9b34fb",
        "00001c00-d102-11e1-9b23-000efb0000c6", "0000ffe0-0000-1000-8000-00805f9b34fb",
        "0000fff0-0000-1000-8000-00805f9b34fb",
    ).map(UUID::fromString)
    private val CHAR_UUIDS = listOf(
        "0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc", "0000b364-d6d8-c7ec-bdf0-eab1bfc6bcbc", "0000b363-d6d8-c7ec-bdf0-eab1bfc6bcbc",
        "00001c0f-d102-11e1-9b23-000efb0000b2", "00004b59-0000-1000-8000-00805f9b34fb",
        "00001c0f-d102-11e1-9b23-000efb0000c6", "0000ffe1-0000-1000-8000-00805f9b34fb",
        "0000fff1-0000-1000-8000-00805f9b34fb",
    ).map(UUID::fromString)
    private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun log(s: String) = LogBus.log("[BLENET] $s")

    /** Pure: splits a byte stream into EC-BTP frames (`$ cmd len … xor \n`), keeping leftovers. */
    internal class Reassembler {
        private val buf = ByteArrayOutputStream()
        fun feed(bytes: ByteArray): List<EcBtpProtocol.Frame> {
            buf.write(bytes)
            val out = ArrayList<EcBtpProtocol.Frame>()
            var data = buf.toByteArray()
            var i = 0
            while (true) {
                while (i < data.size && data[i] != EcBtpProtocol.START) i++
                if (data.size - i < 5) break
                val size = (data[i + 2].toInt() and 0xFF) + 1
                if (size < 5) { i++; continue }
                if (data.size - i < size) break
                val frame = EcBtpProtocol.parse(data.copyOfRange(i, i + size))
                if (frame != null) { out += frame; i += size } else i++
            }
            data = data.copyOfRange(i, data.size)
            buf.reset(); buf.write(data)
            return out
        }
    }

    /** Hides the "pwd" values in a JSON-ish text for the log. */
    internal fun masked(text: String): String =
        text.replace(Regex("(\"pwd\"\\s*:\\s*\")([^\"]*)(\")")) { m -> m.groupValues[1] + "•".repeat(m.groupValues[2].length.coerceAtMost(12)) + m.groupValues[3] }

    private class Link(val gatt: BluetoothGatt, val ch: BluetoothGattCharacteristic, val frames: LinkedBlockingQueue<EcBtpProtocol.Frame>, val writes: Semaphore, @Volatile var mtu: Int)

    /**
     * Blocking (off the main thread). True when the dash got our hotspot details and the hotspot is
     * up, so the caller can go on looking for the dash on it. [dash] is the paired dash (classic).
     */
    fun run(ctx: Context, dash: BluetoothDevice): Boolean {
        log("trying Bluetooth LE (what Carbit Ride uses on these dashes)")
        // 18 Oct: the classic address never answers on LE (12 s timeout); the dash advertises its
        // BLE side as "EC302428DC" at DD:… and is found in < 1 s — so look first.
        val link = scanAndConnect(ctx, dash) ?: connect(ctx, dash) ?: run {
            log("no BLE link to the dash — is Carbit Ride open? It may be holding the dash's BLE: force-stop it")
            return false
        }
        try {
            // Anything the dash says on its own first (handshake / clock / build-net request).
            drain(link, 1_500)
            send(link, CMD_BUILD_NET, ByteArray(0), "build-net request")
            var status: JSONObject? = null
            val until = System.currentTimeMillis() + 8_000
            while (System.currentTimeMillis() < until && status == null) {
                val f = link.frames.poll(until - System.currentTimeMillis(), TimeUnit.MILLISECONDS) ?: break
                val text = String(f.payload, Charsets.UTF_8)
                if (f.command == CMD_REQUEST_BUILD_NET || f.command == CMD_BUILD_NET) status = runCatching { JSONObject(text) }.getOrNull()
            }
            val dev = status?.optJSONObject("carNetDeviceInfo")
            if (dev != null && dev.optString("ssid").isNotBlank()) {
                log("dash offers its OWN network: ssid='${dev.optString("ssid")}' mode=${dev.optInt("mode")} — not handled yet, please send this log")
            }
            log("build-net status=${status?.optInt("status", -100) ?: "no answer"} phoneApFrequency=${status?.opt("phoneApFrequency")}")

            val ap = CarbitBtNet.startLocalHotspot(ctx) { LogBus.log("[BLENET] $it") } ?: return false
            val body = JSONObject().put("ssid", ap.ssid).put("pwd", ap.pwd).put("auth", ap.auth)
                .put("mac", "").put("ip", ap.ip ?: "").toString().toByteArray(Charsets.UTF_8)
            send(link, CMD_REQUEST_BUILD_NET, body, "our hotspot details")
            // Wait for the dash to say it joined (0x53); answer a repeated request once more.
            var resent = false
            val joinUntil = System.currentTimeMillis() + 25_000
            while (System.currentTimeMillis() < joinUntil) {
                val f = link.frames.poll(joinUntil - System.currentTimeMillis(), TimeUnit.MILLISECONDS) ?: break
                if (f.command == CMD_NOTIFY_CAR_NET) { log("dash joined our hotspot ✓"); break }
                if (f.command == CMD_REQUEST_BUILD_NET && !resent) { resent = true; send(link, CMD_REQUEST_BUILD_NET, body, "our hotspot details (again)") }
            }
            return true
        } catch (e: Exception) {
            log("failed: $e"); return false
        } finally {
            runCatching { link.gatt.disconnect() }; runCatching { link.gatt.close() }
        }
    }

    private fun drain(link: Link, ms: Long) {
        val until = System.currentTimeMillis() + ms
        while (true) {
            val left = until - System.currentTimeMillis(); if (left <= 0) return
            link.frames.poll(left, TimeUnit.MILLISECONDS) ?: return
        }
    }

    private fun send(link: Link, cmd: Byte, payload: ByteArray, what: String) {
        val frame = EcBtpProtocol.build(cmd, payload)
        val chunk = (link.mtu - 3).coerceAtLeast(20)
        var ok = true
        var off = 0
        while (off < frame.size) {
            val part = frame.copyOfRange(off, minOf(frame.size, off + chunk))
            link.writes.drainPermits()
            val r = if (Build.VERSION.SDK_INT >= 33) {
                link.gatt.writeCharacteristic(link.ch, part, writeType(link.ch))
            } else {
                @Suppress("DEPRECATION") run {
                    link.ch.writeType = writeType(link.ch)
                    link.ch.value = part
                    if (link.gatt.writeCharacteristic(link.ch)) 0 else -1
                }
            }
            if (r != 0) ok = false
            link.writes.tryAcquire(3, TimeUnit.SECONDS)
            off += part.size
        }
        log("→ 0x${hex(cmd)} $what (${frame.size}B in ${(frame.size + chunk - 1) / chunk} part(s)${if (ok) "" else ", write refused"})")
    }

    private fun props(p: Int) = listOf(2 to "read", 4 to "writeNR", 8 to "write", 16 to "notify", 32 to "indicate")
        .filter { p and it.first != 0 }.joinToString("+") { it.second }

    private fun writeType(c: BluetoothGattCharacteristic) =
        if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

    private fun hex(b: Byte) = "%02x".format(b.toInt() and 0xFF)

    private fun connect(ctx: Context, dev: BluetoothDevice): Link? {
        log("GATT to ${dev.name} ${dev.address}")
        return open(ctx, dev, 12_000)
    }

    /** The dash's BLE address can differ from its classic one: look for it nearby for a few seconds. */
    private fun scanAndConnect(ctx: Context, classic: BluetoothDevice): Link? {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            log("no 'Nearby devices' scan permission — can't look for the dash's BLE"); return null
        }
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return null
        val tail = classic.address.uppercase().replace(":", "").takeLast(6)
        val name = (classic.name ?: "").uppercase()
        val seen = LinkedHashMap<String, String>()
        var found: BluetoothDevice? = null
        val latch = CountDownLatch(1)
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                val d = r.device; val n = r.scanRecord?.deviceName ?: runCatching { d.name }.getOrNull() ?: ""
                val uuids = r.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
                if (seen.size < 25 && d.address !in seen) seen[d.address] = "$n ${uuids.joinToString(",") { it.toString().substring(4, 8) }} ${r.rssi}dBm"
                val hit = d.address.uppercase().replace(":", "").endsWith(tail) ||
                    (name.isNotEmpty() && n.uppercase() == name) ||
                    (n.uppercase().endsWith(tail) && n.isNotEmpty()) ||
                    uuids.any { it in SERVICE_UUIDS } && DashClock.nameLooksLikeDash(n)
                if (hit && found == null) { found = d; latch.countDown() }
            }
        }
        log("scanning BLE for the dash (10 s)…")
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
            latch.await(10, TimeUnit.SECONDS)
        } catch (e: Exception) { log("scan failed: $e") } finally { runCatching { scanner.stopScan(cb) } }
        val f = found
        if (f == null) {
            log("dash not found over BLE. Nearby: " + seen.entries.joinToString(" · ") { "${it.key} ${it.value}" }.ifEmpty { "nothing" })
            return null
        }
        log("found the dash over BLE: ${f.address} (${seen[f.address]})")
        return open(ctx, f, 12_000)
    }

    private fun open(ctx: Context, dev: BluetoothDevice, timeoutMs: Long): Link? {
        val ready = CountDownLatch(1)
        val frames = LinkedBlockingQueue<EcBtpProtocol.Frame>()
        val writes = Semaphore(0)
        val rx = Reassembler()
        var link: Link? = null
        var fail: String? = null
        var mtu = 23
        var ch: BluetoothGattCharacteristic? = null
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    log("BLE connected (status=$status) — asking for a bigger MTU")
                    if (!g.requestMtu(247)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (link == null) { fail = "disconnected (status=$status)"; ready.countDown() } else log("BLE disconnected (status=$status)")
                }
            }
            override fun onMtuChanged(g: BluetoothGatt, m: Int, status: Int) {
                mtu = if (status == BluetoothGatt.GATT_SUCCESS) m else 23
                log("MTU $mtu"); link?.mtu = mtu
                g.discoverServices()
            }
            private val pendingCcc = ArrayDeque<BluetoothGattDescriptor>()
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val services = g.services.orEmpty()
                log("services: " + services.joinToString { s -> s.uuid.toString().let { if (it.endsWith("-0000-1000-8000-00805f9b34fb")) it.substring(4, 8) else it } })
                val svc = SERVICE_UUIDS.firstNotNullOfOrNull { g.getService(it) }
                if (svc == null) { fail = "no EC-BTP service on this device"; ready.countDown(); return }
                for (c in svc.characteristics.orEmpty()) log("char ${c.uuid.toString().substring(4, 8)} props=${props(c.properties)}")
                val known = if (svc.uuid == HUD_SERVICE) HUD_CHARS else CHAR_UUIDS
                val chars = svc.characteristics.orEmpty().filter { it.uuid in known }.ifEmpty { svc.characteristics.orEmpty() }
                val writable = BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                val notifiable = BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE
                val w = chars.firstOrNull { it.properties and writable != 0 }
                if (w == null) { fail = "no writable characteristic"; ready.countDown(); return }
                ch = w
                log("writing to ${w.uuid.toString().substring(4, 8)}")
                for (c in chars.filter { it.properties and notifiable != 0 }) {
                    g.setCharacteristicNotification(c, true)
                    c.getDescriptor(CCC)?.let { pendingCcc += it }
                }
                nextCcc(g)
            }
            private fun nextCcc(g: BluetoothGatt) {
                val d = pendingCcc.removeFirstOrNull()
                if (d == null) { link = Link(g, ch!!, frames, writes, mtu); ready.countDown(); return }
                val v = if (d.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, v)
                else @Suppress("DEPRECATION") run { d.value = v; g.writeDescriptor(d) }
            }
            override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
                log("notifications on ${d.characteristic.uuid.toString().substring(4, 8)} (status=$status)")
                nextCcc(g)
            }
            override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) log("write status=$status")
                writes.release()
            }
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = got(value)
            @Deprecated("pre-33")
            override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION") got(c.value ?: return)
            }
            private fun got(value: ByteArray) {
                val fs = rx.feed(value)
                if (fs.isEmpty() && value.isNotEmpty()) log("← ${value.size}B raw ${value.take(16).joinToString("") { "%02x".format(it) }}")
                for (f in fs) {
                    val text = String(f.payload, Charsets.UTF_8)
                    val shown = if (text.all { it == '\n' || it == '\r' || !it.isISOControl() }) masked(text).take(300)
                        else f.payload.take(32).joinToString("") { "%02x".format(it) }
                    log("← 0x${hex(f.command)} ${f.payload.size}B $shown")
                    frames.put(f)
                }
            }
        }
        val g = runCatching { dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
        if (g == null) { log("couldn't open GATT"); return null }
        if (!ready.await(timeoutMs, TimeUnit.MILLISECONDS)) fail = fail ?: "timeout"
        val l = link
        if (l == null) {
            log("BLE link to ${dev.address} failed: $fail")
            runCatching { g.disconnect() }; runCatching { g.close() }
            return null
        }
        return l
    }
}
