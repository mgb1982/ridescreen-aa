// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * v2.4 (task 20) · locking the bike from the app ends the ride: saves the parking spot and stops the
 * projection, as the Stop button would. Agreed safeguards (Manel, 30 Oct 2026):
 *  - only after a lock the box confirmed and that wasn't read back as unlocked;
 *  - never above 5 km/h or with the engine running (real RPM if the OBD reader is on, else the
 *    battery voltage); unknown speed / engine don't block it;
 *  - option "Locking ends the ride", on by default ([AppSettings.lockEndsTrip]);
 *  - if the lock fails, nothing else happens.
 */
object LockEndsTrip {
    const val MAX_SPEED_KMH = 5.0

    enum class Decision { END, SKIP_NOT_LOCKED, SKIP_MOVING, SKIP_ENGINE, SKIP_OFF }

    /** Pure: what to do after a PKE command. */
    internal fun decide(
        enabled: Boolean, action: PkeUnlock.Action, ok: Boolean, mismatch: Boolean,
        speedKmh: Double?, engineRunning: Boolean?,
    ): Decision = when {
        action != PkeUnlock.Action.LOCK || !ok || mismatch -> Decision.SKIP_NOT_LOCKED
        !enabled -> Decision.SKIP_OFF
        speedKmh != null && speedKmh > MAX_SPEED_KMH -> Decision.SKIP_MOVING
        engineRunning == true -> Decision.SKIP_ENGINE
        else -> Decision.END
    }

    private val main = Handler(Looper.getMainLooper())

    /** Called by [PkeUnlock.send] with every result; does nothing unless it was a confirmed lock. */
    fun after(ctx: Context, action: PkeUnlock.Action, res: PkeUnlock.Result) {
        if (action != PkeUnlock.Action.LOCK) return
        val app = ctx.applicationContext
        val speed = speedKmh(app)
        val engine = PkeBattery.engineState(app)
        val d = decide(AppSettings.lockEndsTrip(app), action, res.ok, res.mismatch, speed, engine)
        val why = "speed ${speed?.let { "%.0f km/h".format(it) } ?: "unknown"}, engine ${when (engine) { true -> "on"; false -> "off"; null -> "unknown" }}"
        when (d) {
            Decision.SKIP_NOT_LOCKED -> return
            Decision.SKIP_OFF -> return
            Decision.SKIP_MOVING, Decision.SKIP_ENGINE -> { LogBus.log("[PKE] locked, but the ride goes on ($why)"); return }
            Decision.END -> Unit
        }
        val riding = ConnectionState.phase != Phase.IDLE && ConnectionState.phase != Phase.STOPPED
        LogBus.log("[PKE] locked → saving the parking spot${if (riding) " and ending the ride" else ""} ($why)")
        main.post {
            try { Parking.saveNow(app) } catch (e: Exception) { LogBus.log("[PKE] parking not saved: $e") }
            if (riding) try { RideStop.stopAll(app, "bike locked from the app", notify = false) } catch (e: Exception) { LogBus.log("[PKE] stop failed: $e") }
        }
    }

    /** OBD speed if the live reader has a fresh one, else the phone's GPS speed from the last minute. */
    private fun speedKmh(ctx: Context): Double? {
        ObdLive.latest?.takeIf { SystemClock.elapsedRealtime() - it.atMs <= 5_000 }?.speed?.let { return it }
        val loc = runCatching { RideExtras.lastLocation(ctx, 60_000) }.getOrNull() ?: return null
        return if (loc.hasSpeed()) loc.speed * 3.6 else null
    }
}
