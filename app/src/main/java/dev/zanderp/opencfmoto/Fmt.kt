// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import java.util.Locale

/** User-facing number formatting: current locale + the map units setting ([MapPrefs.units]). */
object Fmt {
    fun imperial(ctx: Context): Boolean = MapPrefs.units(ctx) == MapUnits.IMPERIAL

    fun decimal(v: Double, digits: Int): String = FmtCore.decimal(v, digits, Locale.getDefault())

    fun grouped(n: Int): String = FmtCore.grouped(n, Locale.getDefault())

    fun rpm(n: Int): String = FmtCore.rpm(n, Locale.getDefault())

    /** "12,34 km" or "7,70 mi". */
    fun distance(ctx: Context, meters: Double, digits: Int = 2): String =
        FmtCore.distance(meters, imperial(ctx), Locale.getDefault(), digits)

    /** Distance number in km or mi, no unit. */
    fun distanceValue(ctx: Context, meters: Double): Double = FmtCore.distanceValue(meters, imperial(ctx))

    fun distanceUnit(ctx: Context): String = FmtCore.distanceUnit(imperial(ctx), Locale.getDefault().language)

    /** "85 km/h" or "53 mph". */
    fun speed(ctx: Context, kmh: Double): String = FmtCore.speed(kmh, imperial(ctx))

    fun speed(ctx: Context, kmh: Int): String = speed(ctx, kmh.toDouble())
}
