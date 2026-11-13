// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.PowerManager
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v2.5 · "Lock the bike" from the watch. Only LOCK can be asked for (never unlock, seat or fuel
 * cap), and it goes through the same [PkeUnlock.send] as the widget: refused while the bike moves
 * (OBD or GPS speed), and the after-lock steps (parking spot, end of ride, no automatic connect)
 * run the same. The watch asks for a second tap before sending. The phone answers on
 * [PATH_RESULT] with its own result text, so the watch needs no copy of the box's error messages.
 */
object WearLock {
    const val PATH_LOCK = "/ocm/lock"
    const val PATH_RESULT = "/ocm/lockres"
    private const val WAKE_MS = 30_000L

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "wear-lock").apply { isDaemon = true } }
    private val busy = AtomicBoolean(false)

    /** Whether the keyless lock is set up on this phone (the watch shows its Lock button only then). */
    fun available(ctx: Context): Boolean =
        PkeBattery.code(ctx).length >= 8 && PkeUnlock.isValidMcuId(PkeUnlock.mcuId(ctx))

    /** A message on [PATH_LOCK] from [node]. Anything but "lock" is ignored. */
    fun handle(ctx: Context, node: String, text: String) {
        if (text != "lock") return
        // Atomic: two messages at once must not both get through (the box takes one client).
        if (!busy.compareAndSet(false, true)) { LogBus.log("[PKE-watch] lock already in progress — ignored"); return }
        val app = ctx.applicationContext
        // The phone may be in a pocket with the screen off: keep the CPU up for the BLE round trip.
        val wake = (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideScreen:wearLock")
            ?.apply { setReferenceCounted(false); acquire(WAKE_MS) }
        try { worker.execute {
            try {
                LogBus.log("[PKE-watch] lock requested from the watch")
                val res = try { PkeUnlock.send(app, PkeUnlock.Action.LOCK) } catch (e: Exception) {
                    LogBus.log("[PKE-watch] send failed: $e")
                    PkeUnlock.Result(false, PkeUnlock.Failure.NO_ANSWER, PkeUnlock.Action.LOCK)
                }
                val ok = res.ok && !res.mismatch
                reply(app, node, ok, PkeUnlockUi.resultText(app, PkeUnlock.Action.LOCK, res))
            } finally {
                busy.set(false)
                runCatching { if (wake?.isHeld == true) wake.release() }
            }
        } } catch (e: Exception) {
            // Could not even schedule it: do not stay "busy" or keep the wake lock.
            busy.set(false)
            runCatching { if (wake?.isHeld == true) wake.release() }
            LogBus.log("[PKE-watch] could not start the lock: $e")
        }
    }

    private fun reply(ctx: Context, node: String, ok: Boolean, text: String) {
        val body = JSONObject().put("ok", ok).put("text", text).toString().toByteArray(Charsets.UTF_8)
        try { Wearable.getMessageClient(ctx).sendMessage(node, PATH_RESULT, body) } catch (e: Exception) {
            LogBus.log("[PKE-watch] could not answer the watch: ${e.message}")
        }
    }
}
