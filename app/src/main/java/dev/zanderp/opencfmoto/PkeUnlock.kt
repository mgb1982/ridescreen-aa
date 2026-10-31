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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private const val KEY_UNLOCK_ON_CONNECT = "unlock_on_connect"

    enum class Action(val code: String, val label: String) {
        LOCK("1", "lock"),
        UNLOCK("2", "unlock"),
        OILBOX("5", "oilbox"),
        CUSHION("6", "cushion"),
    }

    enum class Failure { NO_CODE, NO_MCU_ID, NO_PERMISSION, BLUETOOTH_OFF, NOT_FOUND, NO_LINK, NO_CHALLENGE, BAD_CHALLENGE, BAD_MCU_ID, DERIVE_FAILED, NO_ANSWER }

    /**
     * [lockedAfter] is the lock state read back ~1.5 s after a lock/unlock (null: not read or no answer).
     * [mismatch] means the box confirmed the command but the bike ended up the other way round
     * (case 20261029: a confirmed 'lock' read back as unlocked). Never retried automatically.
     */
    class Result(val ok: Boolean, val failure: Failure?, val action: Action? = null, val lockedAfter: Boolean? = null) {
        val mismatch: Boolean get() {
            val want = PkeUnlock.expectedLocked(action ?: return false) ?: return false
            return lockedAfter != null && lockedAfter != want
        }
    }

    internal fun expectedLocked(a: Action): Boolean? = when (a) { Action.LOCK -> true; Action.UNLOCK -> false; else -> null }

    private fun log(s: String) = LogBus.log("[PKE-unlock] $s")

    // ── storage — McuID only; the PKE code/MAC belong to PkeBattery and are read from there,
    //    never duplicated, so there is exactly one place that holds each value.

    fun mcuId(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MCU_ID, "").orEmpty()

    /** "Unlock when connecting": a Connect tap also unlocks the bike. Off by default. */
    fun unlockOnConnect(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNLOCK_ON_CONNECT, false)

    fun setUnlockOnConnect(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_UNLOCK_ON_CONNECT, on).apply()
    }

    fun setMcuId(ctx: Context, id: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MCU_ID, id.trim().uppercase()).apply()
    }
    
    /** AES plaintext must be one 16-byte block, written as 32 hexadecimal digits. */
    internal fun isValidMcuId(id: String): Boolean =
        id.length == 32 && id.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }

    /**
     * Pull the McuID out of the text of the bike's information QR, which the rider scans instead of
     * typing 32 hex digits by hand. We don't have a verified sample of that QR's exact layout, so this
     * is deliberately tolerant and the caller confirms the result before saving:
     *  1. a `mcuid`/`McuID` key (URL query, JSON, or `key:value` / `key=value`) → the 32-hex run after it;
     *  2. otherwise, the single 32-hex run in the whole text (ambiguous if there are several → give up).
     * Returns the value upper-cased, or null when nothing usable is found. Only the McuID is taken; the
     * rest of the QR (which may carry other vehicle data) is ignored and never stored or logged.
     */
    internal fun mcuIdFromQr(raw: String): String? {
        val hex = Regex("(?<![0-9A-Fa-f])[0-9A-Fa-f]{32}(?![0-9A-Fa-f])")
        val keyed = Regex("mcu[_-]?id", RegexOption.IGNORE_CASE).find(raw)
        if (keyed != null) {
            hex.find(raw, keyed.range.last + 1)?.let { return it.value.uppercase() }
        }
        val all = hex.findAll(raw).map { it.value }.toList()
        return all.singleOrNull()?.uppercase()
    }

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
        // Keep this pure validation ahead of diagnostic logging: JVM unit tests do not provide
        // Android's android.util.Log implementation, and malformed input needs no AES work.
        if (!isValidMcuId(mcuId)) return Derived.BadMcuId
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
        val res = sendRaw(ctx, action)
        try { LockEndsTrip.after(ctx, action, res) } catch (e: Exception) { log("after-lock step failed: $e") }
        return res
    }

    private fun sendRaw(ctx: Context, action: Action): Result {
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
        sending = true
        val t0 = SystemClock.elapsedRealtime()
        val link: Link
        val warmLink = takeWarm()
        if (warmLink != null) {
            link = warmLink
            log("using the pre-opened link (${SystemClock.elapsedRealtime() - t0} ms)")
        } else {
            freeTheBox()
            val dev = scan(ctx, pke)
            if (dev == null) { sending = false; return Result(false, Failure.NOT_FOUND) }
            val tScan = SystemClock.elapsedRealtime()
            val opened = open(ctx, dev, pke)
            if (opened == null) { sending = false; return Result(false, Failure.NO_LINK) }
            link = opened
            log("timing: scan ${tScan - t0} ms, connect+discover ${SystemClock.elapsedRealtime() - tScan} ms")
        }
        var handedOff = false
        try {
            val tChallenge = SystemClock.elapsedRealtime()
            val challengeReply = ask(link, pke, "*BT,$pke,10,008#".toByteArray(Charsets.US_ASCII), "008", 6_000)
                ?: return Result(false, Failure.NO_CHALLENGE)
            log("timing: challenge round trip ${SystemClock.elapsedRealtime() - tChallenge} ms")
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
            val tWrite = SystemClock.elapsedRealtime()
            val reply = link.replies.poll(1_500, TimeUnit.MILLISECONDS)
            if (reply == null) log("no reply to '${action.label}' within 1.5 s (the box may still have acted — check the bike)")
            else log("timing: action reply after ${SystemClock.elapsedRealtime() - tWrite} ms")
            log("timing: total ${SystemClock.elapsedRealtime() - t0} ms")
            val want = expectedLocked(action)
            var after: Boolean? = null
            if (ok && want != null) {
                after = readStateAt(link, pke, tWrite, STATE_CHECKS_MS[0])
                logState(action, after, want, STATE_CHECKS_MS[0])
                if (after != null && after == want) PkeBattery.noteLocked(ctx, after)
                else if (after != null && link.alive) {
                    // The box can take more than 1.5 s to report the new state (case 20261030: a lock
                    // confirmed by the box read back "unlocked", the bike was locked). Re-check on the same
                    // link in the background; the rider sees the result now, a warning only if it holds.
                    handedOff = true
                    recheckLater(ctx.applicationContext, link, pke, action, want, tWrite)
                    after = null
                } else if (after != null) {
                    log("state after '${action.label}': link gone, can't re-check — not warning on one early read")
                    after = null
                }
            }
            return Result(ok, if (ok) null else Failure.NO_ANSWER, action, after)
        } finally {
            if (!handedOff) {
                runCatching { link.gatt.disconnect() }; runCatching { link.gatt.close() }
                sending = false
            }
        }
    }

    private fun word(l: Boolean) = if (l) "locked" else "unlocked"

    private fun logState(action: Action, got: Boolean?, want: Boolean, atMs: Long, tail: String = "") {
        val at = "@${"%.1f".format(atMs / 1000.0)}s"
        log("state after '${action.label}' $at: ${got?.let { word(it) } ?: "unknown"} (expected ${word(want)})$tail")
    }

    /** One read-only `001,7` on the command's link, [atMs] after the write. Null: no answer / link gone. */
    private fun readStateAt(link: Link, pke: String, tWrite: Long, atMs: Long): Boolean? {
        val wait = atMs - (SystemClock.elapsedRealtime() - tWrite)
        if (wait > 0) runCatching { Thread.sleep(wait) }
        if (!link.alive) return null
        link.replies.clear()
        val bytes = "*BT,$pke,10,001,7#".toByteArray(Charsets.US_ASCII)
        val ok = writeBytes(link, bytes)
        log("→ ${masked(String(bytes, Charsets.US_ASCII), pke)}${if (ok) "" else " (write refused)"}")
        var locked: Boolean? = null
        val until = SystemClock.elapsedRealtime() + 2_000
        while (ok && locked == null) {
            val left = until - SystemClock.elapsedRealtime()
            if (left <= 0) break
            val r = link.replies.poll(left, TimeUnit.MILLISECONDS) ?: break
            locked = PkeBattery.lockedFrom(r)
        }
        return locked
    }

    /**
     * Re-reads at 4 s and 8 s. Stores the state only once the box agrees with the command; warns the
     * rider (toast) only if every read disagrees. Never re-sends the command: a second automatic order
     * could fight the bike's own button. Holds the link (and [holdingBox]) until done.
     */
    private fun recheckLater(app: Context, link: Link, pke: String, action: Action, want: Boolean, tWrite: Long) {
        Thread({
            var last: Boolean? = null
            try {
                for (at in STATE_CHECKS_MS.drop(1)) {
                    val got = readStateAt(link, pke, tWrite, at)
                    if (got != null) last = got
                    if (got == want) { logState(action, got, want, at, " — confirmed late"); PkeBattery.noteLocked(app, got); return@Thread }
                    logState(action, got, want, at)
                }
                val final = last
                if (final != null && final != want) {
                    log("state after '${action.label}': still ${word(final)} after ${STATE_CHECKS_MS.last() / 1000} s — MISMATCH, rider warned, no retry")
                    PkeBattery.noteLocked(app, final)
                    idleHandler.post {
                        android.widget.Toast.makeText(app, app.getString(if (final) R.string.pke_state_left_locked else R.string.pke_state_left_unlocked), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                runCatching { link.gatt.disconnect() }; runCatching { link.gatt.close() }
                sending = false
            }
        }, "pke-recheck").start()
    }

    /**
     * The box takes one client and stops advertising while the live voltage link holds it, so a scan
     * would come back empty. Wake the live loop (it sees [holdingBox] and hands the box over) and give it
     * up to [LIVE_HANDOVER_MS] to close; scan anyway after that. Call only once [holdingBox] is true.
     */
    private fun freeTheBox() {
        if (!PkeBattery.liveOpen) return
        val t = SystemClock.elapsedRealtime()
        PkeBattery.wakeLive()
        val closed = runCatching { PkeBattery.awaitLiveClosed(LIVE_HANDOVER_MS) }.getOrDefault(false)
        log(if (closed) "live voltage link handed the box over (${SystemClock.elapsedRealtime() - t} ms)"
            else "live voltage link still open after ${LIVE_HANDOVER_MS} ms — scanning anyway")
    }

    private const val LIVE_HANDOVER_MS = 1_000L
    /** When the lock state is read back after lock/unlock, ms after the write. */
    private val STATE_CHECKS_MS = longArrayOf(1_500L, 4_000L, 8_000L)

    // ── pre-opened link: the GATT setup (connect, MTU, discovery, notifications) is what makes a tap
    //    feel slow (1-4 s on this box), not the challenge or the write. warmUp() does that setup while
    //    the rider is still choosing, send() then only needs the challenge round trip and the write.
    //    The box takes one client at a time, so while a link is held or being opened [holdingBox] is
    //    true and MainActivity skips its own battery reads. It closes by itself after WARM_IDLE_MS.

    private const val WARM_IDLE_MS = 40_000L
    // lazy: Looper isn't available in plain JVM unit tests, which touch this object (PkeUnlockTest)
    private val idleHandler by lazy { Handler(Looper.getMainLooper()) }
    private val idleClose = Runnable { releaseWarm("idle ${WARM_IDLE_MS / 1000} s") }
    private val warmLock = Any()
    @Volatile private var warm: Link? = null
    @Volatile private var warming = false
    @Volatile private var warmDone: CountDownLatch? = null
    @Volatile private var sending = false

    /** True while this class holds (or is opening) a link to the box: other readers must stay off it. */
    val holdingBox: Boolean get() = warming || sending || warm?.alive == true

    /** Drops a pre-opened link now (the rider cancelled), instead of waiting for the idle timeout. */
    fun release() = releaseWarm("cancelled")

    private fun rearmIdle() {
        idleHandler.removeCallbacks(idleClose)
        idleHandler.postDelayed(idleClose, WARM_IDLE_MS)
    }

    private fun releaseWarm(why: String) {
        idleHandler.removeCallbacks(idleClose)
        val l = synchronized(warmLock) { warm.also { warm = null } } ?: return
        log("closing the pre-opened link ($why)")
        runCatching { l.gatt.disconnect() }; runCatching { l.gatt.close() }
    }

    /** Hands the pre-opened link to send(), waiting for it if it is still being opened. */
    private fun takeWarm(): Link? {
        warmDone?.let { runCatching { it.await(14, TimeUnit.SECONDS) } }
        idleHandler.removeCallbacks(idleClose)
        val l = synchronized(warmLock) { warm.also { warm = null } } ?: return null
        if (!l.alive) { runCatching { l.gatt.close() }; log("pre-opened link was already gone"); return null }
        return l
    }

    /** Non-blocking: starts opening a link in the background (call when the rider opens the action menu). */
    fun warmUp(ctx: Context) {
        val app = ctx.applicationContext
        synchronized(warmLock) {
            if (warming || sending) return
            if (warm?.alive == true) { rearmIdle(); return }
            warming = true
            warmDone = CountDownLatch(1)
        }
        Thread({
            try {
                val pke = PkeBattery.code(app)
                if (pke.length < 8 || !isValidMcuId(mcuId(app))) return@Thread
                if (Build.VERSION.SDK_INT >= 31 && listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                        .any { ContextCompat.checkSelfPermission(app, it) != PackageManager.PERMISSION_GRANTED }) return@Thread
                val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
                if (adapter == null || !adapter.isEnabled) return@Thread
                val t0 = SystemClock.elapsedRealtime()
                log("pre-opening the link while the rider chooses…")
                freeTheBox()
                val dev = scan(app, pke) ?: return@Thread
                val tScan = SystemClock.elapsedRealtime()
                val l = open(app, dev, pke) ?: return@Thread
                synchronized(warmLock) { warm = l }
                log("timing: pre-opened link ready (scan ${tScan - t0} ms, connect+discover ${SystemClock.elapsedRealtime() - tScan} ms)")
                rearmIdle()
            } catch (e: Exception) {
                log("pre-open failed: $e")
            } finally {
                warming = false
                warmDone?.countDown()
            }
        }, "pke-warm").start()
    }

    // ── GATT plumbing — deliberately a separate copy of PkeBattery's connect/scan logic rather
    //    than a shared helper: PkeBattery stays read-only by its own documented design, and this
    //    file alone owns every write this protocol can do. Duplication traded for isolation.

    private class Link(val gatt: BluetoothGatt, val rx: BluetoothGattCharacteristic, val replies: LinkedBlockingQueue<String>) {
        @Volatile var alive = true
    }

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
                    runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
                    if (!g.requestMtu(185)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    link?.alive = false
                    if (link == null) { fail = "disconnected (status=$status)"; ready.countDown() }
                }
            }
            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val svc = g.getService(UART_SERVICE)
                val rx = svc?.getCharacteristic(UART_RX)
                val tx = svc?.getCharacteristic(UART_TX)
                if (rx == null || tx == null) { fail = "no Nordic UART service"; ready.countDown(); return }
                g.setCharacteristicNotification(tx, true)
                link = Link(g, rx, replies)
                val d = tx.getDescriptor(CCC)
                if (d == null) { ready.countDown(); return }
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else @Suppress("DEPRECATION") run { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
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
