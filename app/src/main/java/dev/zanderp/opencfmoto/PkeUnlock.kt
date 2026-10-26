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
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * v2.3 · Lock / unlock / fuel-tank / seat release on the keyless box (Zontes "PKE 3.0") over
 * Bluetooth LE — the control counterpart to [PkeBattery]'s read-only voltage/lock-state query.
 * Decompiled from Zontes Smart EU 1.12 (BlueToothOperateActivity, a/vf9.java; rea-lab, 22 Oct 2026 —
 * see findings/zontes-smart-eu/PORTING-PKE-UNLOCK.md in that repo for the full derivation).
 *
 * THIS SENDS REAL COMMANDS TO THE BIKE. Exactly four actions exist; nothing else is implemented,
 * and nothing here is wired into the automatic connect flow — every send is an explicit,
 * confirmed user action (see [PkeUnlockUi]):
 *  - `*BT,<pke>,10,001,1,<cipher>#` — lock
 *  - `*BT,<pke>,10,001,2,<cipher>#` — unlock
 *  - `*BT,<pke>,10,001,5,<cipher>#` — oil box / fuel-tank release
 *  - `*BT,<pke>,10,001,6,<cipher>#` — cushion / seat release
 *
 * `<cipher>` is derived fresh for every single command from a `008` challenge query (the same
 * handshake [PkeBattery] already uses, read-only) plus the bike's McuID — AES-128/ECB/NoPadding
 * throughout, read directly off the decompiled code, not reconstructed from a spec:
 *   h      = AES_encrypt(key = ASCII "TAYOBTa1YCWc2gTS", plaintext = hex-decode(McuID))
 *   k      = h[0..11] + (challenge[0..3] XOR h[12..15])
 *   block  = pkeCode's first 15 ASCII bytes, zero-padded to 16
 *   cipher = hex-encode(AES_encrypt(key = k, plaintext = block))
 *
 * The AES key above is a constant baked into every copy of the official app (also found in
 * plain text inside CN's libhud_protocol.so) — it is not a secret by itself and is not unique
 * to any bike. **McuID is the real per-device secret.** It is not on the bike's binding QR and
 * is not readable over BLE anywhere in this protocol; the official app fetches it from Zontes'
 * own cloud backend after login (tied to the bound PKE code) and caches it locally. Until the
 * rider supplies it (below — read it off a phone where the official app is already logged in
 * and bound, there is no other source for it here), nothing in this file can succeed: [send]
 * fails cleanly with [Failure.NO_MCU_ID] rather than guessing or sending a malformed command.
 *
 * Not yet validated against real hardware — read PORTING-PKE-UNLOCK.md's safety notes before
 * relying on this. The official app's own reply-field semantics for these four actions weren't
 * pinned down in the investigation this is based on (only the two read-only queries were); treat
 * any `*AT,…,001,<action>` reply as an acknowledgement that *something* happened, not as a
 * verified success/failure code, until that's confirmed empirically.
 */
@SuppressLint("MissingPermission")
object PkeUnlock {
    private val UART_SERVICE: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val UART_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // phone → box
    private val UART_TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // box → phone
    private val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** App-wide constant, not a secret (see class doc) — the same in every install. */
    private const val AES_KEY_ASCII = "TAYOBTa1YCWc2gTS"

    private const val PREFS = "pke_unlock"
    private const val KEY_MCU_ID = "mcu_id"

    enum class Action(val code: String, val label: String) {
        LOCK("1", "lock"),
        UNLOCK("2", "unlock"),
        OILBOX("5", "oilbox"),
        CUSHION("6", "cushion"),
    }

    enum class Failure { NO_CODE, NO_MCU_ID, NO_PERMISSION, BLUETOOTH_OFF, NOT_FOUND, NO_LINK, NO_CHALLENGE, BAD_CHALLENGE, BAD_MCU_ID, DERIVE_FAILED, NO_ANSWER }

    class Result(val ok: Boolean, val failure: Failure?)

    private fun log(s: String) = LogBus.log("[PKE-unlock] $s")

    // ── storage — McuID only; the PKE code/MAC belong to PkeBattery and are read from there,
    //    never duplicated, so there is exactly one place that holds each value.

    fun mcuId(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MCU_ID, "").orEmpty()

    fun setMcuId(ctx: Context, id: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MCU_ID, id.trim().uppercase()).apply()
    }
    
    /** AES plaintext must be one 16-byte block, written as 32 hexadecimal digits. */
    internal fun isValidMcuId(id: String): Boolean =
        id.length == 32 && id.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }

    // ── pure bits (unit-testable, same style as PkeBattery's)

    /** The raw challenge field (between `008` and the voltage) of a `008` reply, unparsed. */
    internal fun challengeField(reply: String): String? {
        val p = reply.trim().removeSuffix("#").split(",")
        if (p.firstOrNull() != "*AT") return null
        val i = p.indexOf("008").takeIf { it >= 1 } ?: return null
        return p.getOrNull(i + 1)?.trim()
    }

    /**
     * 4-byte challenge out of a `008` reply (`*AT,a,b,008,<challenge>,…#`). The decompiled official
     * app does `str2.substring(0, str2.length() - 1)` before hex-decoding — but that app reads a raw
     * buffer that still carries a trailing byte our frame splitter already strips, so we don't know
     * up front which form is already clean. Try the field as-is first, then the drop-last variant;
     * accept whichever hex-decodes to exactly 4 bytes. The challenge is a per-session nonce, not a
     * secret, so it is safe to inspect/log.
     */
    internal fun challengeFrom(reply: String): ByteArray? {
        val hex = challengeField(reply) ?: return null
        for (c in listOf(hex, hex.dropLast(1))) {
            val b = runCatching { BleProtocol.hexToBytes(c) }.getOrNull()
            if (b != null && b.size == 4) return b
        }
        return null
    }

    private fun aesEcbEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(plaintext)
    }

    /** Distinguishes "the McuID itself is unusable" from "something else in the chain broke". */
    sealed class Derived {
        data class Ok(val cipherHex: String) : Derived()
        object BadMcuId : Derived()
        object Failed : Derived()
    }

    /**
     * The exact derivation read from BlueToothOperateActivity.java / a/vf9.java (its h/K/i/e).
     * Logs the shape of each stage (never the McuID value, only its byte length) so a failed
     * attempt tells us which step broke.
     */
    internal fun derive(pkeCode: String, mcuId: String, challenge: ByteArray): Derived {
        if (challenge.size != 4) return Derived.Failed
        val mcuBytes = runCatching { BleProtocol.hexToBytes(mcuId) }.getOrNull()
        if (mcuBytes == null) {
            log("derive: McuID is not valid hex (len=${mcuId.length}) — check it was entered as hex digits only")
            return Derived.BadMcuId
        }
        log("derive: McuID decodes to ${mcuBytes.size} bytes, challenge ${BleProtocol.bytesToHex(challenge)}")
        if (mcuBytes.size != 16) {
            log("derive: McuID is ${mcuBytes.size} bytes, AES needs exactly 16 — the stored McuID is the wrong length/format")
            return Derived.BadMcuId
        }
        val h = runCatching { aesEcbEncrypt(AES_KEY_ASCII.toByteArray(Charsets.US_ASCII), mcuBytes) }.getOrNull()
        if (h == null || h.size != 16) { log("derive: AES step 1 (h) failed"); return Derived.BadMcuId }
        val k = ByteArray(16)
        for (i in 0 until 16) k[i] = if (i < 12) h[i] else (challenge[i - 12].toInt() xor h[i].toInt()).toByte()
        val block = ByteArray(16)
        val codeBytes = pkeCode.toByteArray(Charsets.US_ASCII)
        System.arraycopy(codeBytes, 0, block, 0, minOf(15, codeBytes.size))
        val cipher = runCatching { aesEcbEncrypt(k, block) }.getOrNull() ?: return Derived.Failed.also { log("derive: AES step 4 (cipher) failed") }
        log("derive: cipher ready (${cipher.size} bytes)")
        return Derived.Ok(BleProtocol.bytesToHex(cipher))
    }

    internal fun frame(pke: String, action: Action, cipherHex: String): ByteArray =
        "*BT,$pke,10,001,${action.code},$cipherHex#".toByteArray(Charsets.US_ASCII)

    /** Frames as they appear in the log: PKE code down to its last 4 digits, challenge/cipher hidden. */
    internal fun masked(text: String, pke: String): String {
        var t = if (pke.length >= 8) text.replace(pke, "…" + pke.takeLast(4)) else text
        val p = t.split(",")
        when (p.firstOrNull()) {
            "*AT" -> {
                val i = p.indexOf("008")
                if (i >= 1 && p.size > i + 1) t = p.toMutableList().also { it[i + 1] = "••••" }.joinToString(",")
            }
            "*BT" -> {
                val i = p.indexOf("001")
                if (i >= 1 && p.size > i + 2) t = p.toMutableList().also { it[i + 2] = "••••" }.joinToString(",")
            }
        }
        return t
    }

    // ── the send (blocking: call it off the main thread, same contract as PkeBattery.read)

    fun send(ctx: Context, action: Action): Result {
        val pke = PkeBattery.code(ctx)
        if (pke.length < 8) return Result(false, Failure.NO_CODE)
        val mcu = mcuId(ctx)
        if (mcu.isBlank()) return Result(false, Failure.NO_MCU_ID)
        if (!isValidMcuId(mcu)) {
            log("invalid McuID format: expected 32 hexadecimal digits (16 bytes), received length=${mcu.length}; nothing sent")
            return Result(false, Failure.BAD_MCU_ID)
        }
        if (Build.VERSION.SDK_INT >= 31 && listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                .any { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }) {
            return Result(false, Failure.NO_PERMISSION)
        }
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return Result(false, Failure.BLUETOOTH_OFF)
        log("sending '${action.label}' to the keyless box for PKE …${pke.takeLast(4)}")
        val dev = scan(ctx, pke) ?: return Result(false, Failure.NOT_FOUND)
        val link = open(ctx, dev, pke) ?: return Result(false, Failure.NO_LINK)
        try {
            val challengeReply = ask(link, pke, "*BT,$pke,10,008#".toByteArray(Charsets.US_ASCII), "008", 6_000)
                ?: return Result(false, Failure.NO_CHALLENGE)
            val challenge = challengeFrom(challengeReply)
            if (challenge == null) {
                log("challenge field '${challengeField(challengeReply) ?: "?"}' didn't decode to 4 bytes — nothing sent")
                return Result(false, Failure.BAD_CHALLENGE)
            }
            val cipherHex = when (val d = derive(pke, mcu, challenge)) {
                is Derived.Ok -> d.cipherHex
                is Derived.BadMcuId -> return Result(false, Failure.BAD_MCU_ID)
                is Derived.Failed -> return Result(false, Failure.DERIVE_FAILED)
            }
            val bytes = frame(pke, action, cipherHex)
            link.replies.clear()
            val ok = writeBytes(link, bytes)
            log("→ ${masked(String(bytes, Charsets.US_ASCII), pke)}${if (ok) "" else " (write refused)"}")
            val reply = link.replies.poll(4_000, TimeUnit.MILLISECONDS)
            if (reply == null) log("no reply to '${action.label}' within 4s (the box may still have acted — check the bike)")
            return Result(ok, if (ok) null else Failure.NO_ANSWER)
        } finally {
            runCatching { link.gatt.disconnect() }; runCatching { link.gatt.close() }
        }
    }

    // ── GATT plumbing — deliberately a separate copy of PkeBattery's connect/scan logic rather
    //    than a shared helper: PkeBattery stays read-only by its own documented design, and this
    //    file alone owns every write this protocol can do. Duplication traded for isolation.

    private class Link(val gatt: BluetoothGatt, val rx: BluetoothGattCharacteristic, val replies: LinkedBlockingQueue<String>)

    private fun writeType(c: BluetoothGattCharacteristic) =
        if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

    private fun writeBytes(link: Link, bytes: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            link.gatt.writeCharacteristic(link.rx, bytes, writeType(link.rx)) == 0
        } else {
            @Suppress("DEPRECATION") run { link.rx.writeType = writeType(link.rx); link.rx.value = bytes; link.gatt.writeCharacteristic(link.rx) }
        }

    /** Sends one frame and waits for a reply whose `*AT,…,<want>,…` matches. */
    private fun ask(link: Link, pke: String, bytes: ByteArray, want: String, timeoutMs: Long): String? {
        link.replies.clear()
        val ok = writeBytes(link, bytes)
        log("→ ${masked(String(bytes, Charsets.US_ASCII), pke)}${if (ok) "" else " (write refused)"}")
        val until = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = until - System.currentTimeMillis()
            if (left <= 0) { log("no answer to the challenge query"); return null }
            val r = link.replies.poll(left, TimeUnit.MILLISECONDS) ?: continue
            if (r.split(",").getOrNull(3) == want) return r
        }
    }

    private fun scan(ctx: Context, pke: String): BluetoothDevice? {
        val scanner = ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return null
        var found: BluetoothDevice? = null
        val latch = CountDownLatch(1)
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull()
                if (found == null && PkeBattery.matches(r.scanRecord?.bytes, name, pke)) {
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
        if (found == null) log("box not found")
        return found
    }

    private fun open(ctx: Context, dev: BluetoothDevice, pke: String): Link? {
        val ready = CountDownLatch(1)
        val replies = LinkedBlockingQueue<String>()
        val text = StringBuilder()
        var link: Link? = null
        var fail: String? = null
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!g.requestMtu(185)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && link == null) {
                    fail = "disconnected (status=$status)"; ready.countDown()
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
                        log("← ${masked(f, pke)}")
                        replies.put(f)
                    }
                    if (text.length > 512) text.setLength(0)
                }
            }
        }
        val g = runCatching { dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) }.getOrNull() ?: return null
        if (!ready.await(12_000, TimeUnit.MILLISECONDS)) fail = fail ?: "timeout"
        val l = link
        if (l == null) {
            log("no link to the box: $fail")
            runCatching { g.disconnect() }; runCatching { g.close() }
            return null
        }
        return l
    }
}
