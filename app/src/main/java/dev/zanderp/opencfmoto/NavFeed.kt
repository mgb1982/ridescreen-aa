// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.SystemClock

/** Pure rules, no Android imports (unit-tested). */
internal object NavFeedLogic {
    fun shouldAdvertise(watch: Boolean, arrow: Boolean) = watch || arrow
    fun isFresh(ageMs: Long, maxMs: Long = 15_000L) = ageMs in 0..maxMs
}

/**
 * Next-manoeuvre data from Android Auto's navigation-status channel, independent of the watch
 * vibrations. Opening the channel is controlled by [advertise]; nothing is drawn from it yet.
 */
object NavFeed {
    private const val PREFS = "nav_feed"
    private const val KEY_ARROW = "arrow"

    /** The "Next-turn arrow" setting. Takes effect the next time AA connects. */
    @Volatile var arrowOn = false
        private set

    data class Maneuver(val kind: TurnHaptics.Kind, val exit: Int, val meters: Int, val road: String, val ageMs: Long)

    fun load(ctx: Context) {
        arrowOn = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ARROW, false)
        if (arrowOn) LogBus.log("[NAV] next-turn arrow ON: advertising the navigation-status service")
    }

    fun setArrow(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ARROW, on).apply()
        load(ctx)
    }

    fun advertise(): Boolean = shouldAdvertise(TurnHaptics.enabled, arrowOn)

    internal fun shouldAdvertise(watch: Boolean, arrow: Boolean) = NavFeedLogic.shouldAdvertise(watch, arrow)

    internal fun isFresh(ageMs: Long, maxMs: Long = 15_000L) = NavFeedLogic.isFresh(ageMs, maxMs)

    /** Latest manoeuvre, or null when none, no distance yet, or older than 15 s. */
    fun current(nowMs: Long = SystemClock.elapsedRealtime()): Maneuver? {
        val (kind, exit, road) = TurnHaptics.snapshot()
        val meters = TurnHaptics.lastDistance
        if (kind == TurnHaptics.Kind.NONE || meters < 0) return null
        val age = nowMs - TurnHaptics.lastUpdateMs
        if (!isFresh(age)) return null
        return Maneuver(kind, exit, meters, road, age)
    }
}
