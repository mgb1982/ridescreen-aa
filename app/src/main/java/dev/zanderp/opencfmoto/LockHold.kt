// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.SystemClock

/**
 * Locking the bike from the app ends the ride ([LockEndsTrip]); it must not start a new one a few
 * seconds later. Manel, 11 Nov 2026: right after the lock the dash's Wi-Fi is still up for a moment,
 * the low-power wait ([BikeWatchService]) saw it and launched Connect again.
 *
 * After a lock from the app, automatic connects are held until there is a sign of a new ride:
 *  - an unlock from the app, or
 *  - the bike's Wi-Fi was seen gone (the dash switched off), or
 *  - the dash's Bluetooth connects again at least [BT_GRACE_MS] after the lock, or
 *  - [MAX_HOLD_MS] passed (safety net).
 * A Connect tapped by the rider is never held. Kept in prefs so a restarted process still honours it.
 */
object LockHold {
    private const val PREFS = "lock_hold"
    private const val KEY_AT = "at_elapsed"
    private const val KEY_GONE = "gone"
    const val BT_GRACE_MS = 90_000L
    const val MAX_HOLD_MS = 2 * 3600_000L

    enum class Trigger { WIFI_SEEN, BT_CONNECTED }

    /** Pure decision (unit tested). [heldAt] 0 = no hold. */
    internal fun allows(heldAt: Long, now: Long, sawGone: Boolean, trigger: Trigger): Boolean = when {
        heldAt <= 0L -> true
        now < heldAt -> true                         // clock went back (reboot): don't hold
        now - heldAt >= MAX_HOLD_MS -> true
        sawGone -> true
        trigger == Trigger.BT_CONNECTED && now - heldAt >= BT_GRACE_MS -> true
        else -> false
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun arm(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_AT, SystemClock.elapsedRealtime()).putBoolean(KEY_GONE, false).apply()
        LogBus.log("[LOCK-HOLD] bike locked from the app: no automatic connect until the bike is switched off and on again, or unlocked from the app")
    }

    fun release(ctx: Context, why: String) {
        if (prefs(ctx).getLong(KEY_AT, 0L) <= 0L) return
        prefs(ctx).edit().remove(KEY_AT).remove(KEY_GONE).apply()
        LogBus.log("[LOCK-HOLD] released ($why)")
    }

    /** The low-power wait saw a fresh scan without the bike's Wi-Fi: the dash is off. */
    fun onWifiGone(ctx: Context) {
        val p = prefs(ctx)
        if (p.getLong(KEY_AT, 0L) <= 0L || p.getBoolean(KEY_GONE, false)) return
        p.edit().putBoolean(KEY_GONE, true).apply()
        LogBus.log("[LOCK-HOLD] the bike's Wi-Fi is gone — the next time it appears is a new ride")
    }

    /** True if an automatic connect may go ahead now; logs once per refusal. */
    fun allowsAuto(ctx: Context, trigger: Trigger): Boolean {
        // B1/B4: an import of bike camera clips holds the bike's Wi-Fi without Android Auto — don't start a ride under it.
        if (DashcamImport.busy()) {
            LogBus.logThrottled("dashcam-hold", "[DASHCAM] automatic connect (${trigger.name.lowercase()}) skipped — importing camera clips", 60_000)
            return false
        }
        val p = prefs(ctx)
        val at = p.getLong(KEY_AT, 0L)
        val ok = allows(at, SystemClock.elapsedRealtime(), p.getBoolean(KEY_GONE, false), trigger)
        if (at > 0L) {
            if (ok) release(ctx, "new ride: ${trigger.name.lowercase()}")
            else LogBus.logThrottled("lock-hold", "[LOCK-HOLD] ${trigger.name.lowercase()} ignored — the bike was locked from the app ${(SystemClock.elapsedRealtime() - at) / 1000} s ago", 60_000)
        }
        return ok
    }
}
