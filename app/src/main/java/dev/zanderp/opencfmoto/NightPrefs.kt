// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Calendar

/**
 * Map (Android Auto) day/night theme. Android Auto apps like Google Maps switch between their light
 * and dark map styles based on the head unit's NIGHT sensor — which OpenCfMoto reports (see
 * [dev.zanderp.opencfmoto.aa.NightModeEvent]). This just decides what value to report.
 */
enum class MapTheme(val label: String, @StringRes val labelRes: Int) {
    AUTO("Auto (day / night)", R.string.setup_auto_day_night),
    DAY("Day (light)", R.string.pref_map_theme_day),
    NIGHT("Night (dark)", R.string.pref_map_theme_night),
}

object NightPrefs {
    private const val PREFS = "opencfmoto_bike"
    private const val KEY = "map_theme"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun theme(ctx: Context): MapTheme {
        val name = prefs(ctx).getString(KEY, MapTheme.AUTO.name)
        return runCatching { MapTheme.valueOf(name!!) }.getOrDefault(MapTheme.AUTO)
    }

    fun setTheme(ctx: Context, theme: MapTheme) {
        prefs(ctx).edit().putString(KEY, theme.name).apply()
    }

    /** Cycle Auto → Day → Night → Auto (for a one-tap toggle). Returns the new theme. */
    fun cycle(ctx: Context): MapTheme {
        val next = when (theme(ctx)) {
            MapTheme.AUTO -> MapTheme.DAY
            MapTheme.DAY -> MapTheme.NIGHT
            MapTheme.NIGHT -> MapTheme.AUTO
        }
        setTheme(ctx, next)
        return next
    }

    /**
     * The night value to report to Android Auto right now. In [MapTheme.AUTO] it's night when either
     * the phone is in its own dark (night) UI mode — so a phone set to switch at sunset carries the
     * dash with it — or it's simply evening/early-morning by the clock (a self-contained fallback for
     * phones left on a fixed light theme). When the dash itself reports day/night from its light sensor
     * ([DashNight], recent answer only), that wins in Auto: it is the light the rider actually sees.
     */
    fun isNightNow(ctx: Context): Boolean = when (theme(ctx)) {
        MapTheme.DAY -> false
        MapTheme.NIGHT -> true
        MapTheme.AUTO -> DashNight.current() ?: (phoneInNightMode(ctx) || clockSaysNight())
    }

    private fun phoneInNightMode(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Night between 19:00 and 06:59 local time. */
    private fun clockSaysNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h < 7 || h >= 19
    }
}
