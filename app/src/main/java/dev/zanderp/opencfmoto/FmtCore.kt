// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure (no Android) number formatting for text the rider sees: decimal comma vs point, thousands
 * grouping and km/mi, km/h/mph follow the locale and the units setting. File names, GPX/XML/JSON,
 * logs and preference keys must keep using Locale.US and do not go through here.
 */
internal object FmtCore {
    const val METERS_PER_MILE = 1609.344
    private const val MPH_PER_KMH = 0.621371

    fun decimal(v: Double, digits: Int, locale: Locale): String =
        String.format(locale, "%.${digits}f", v)

    fun grouped(n: Int, locale: Locale): String = String.format(locale, "%,d", n)

    fun grouped(n: Long, locale: Locale): String = String.format(locale, "%,d", n)

    fun distanceUnit(imperial: Boolean, lang: String): String = when {
        imperial -> if (lang == "ru") "миль" else "mi"
        else -> Units.km(lang)
    }

    /** Distance in km (or miles when [imperial]). */
    fun distanceValue(meters: Double, imperial: Boolean): Double =
        if (imperial) meters / METERS_PER_MILE else meters / 1000.0

    /** "12,34 km" (es/de) or "7,70 mi"; [digits] decimals. */
    fun distance(meters: Double, imperial: Boolean, locale: Locale, digits: Int = 2): String =
        decimal(distanceValue(meters, imperial), digits, locale) + " " + distanceUnit(imperial, locale.language)

    fun speedValue(kmh: Double, imperial: Boolean): Int =
        if (imperial) (kmh * MPH_PER_KMH).roundToInt() else kmh.toInt()

    fun speedUnit(imperial: Boolean): String = if (imperial) "mph" else "km/h"

    fun speed(kmh: Double, imperial: Boolean): String =
        "${speedValue(kmh, imperial)} ${speedUnit(imperial)}"

    fun rpm(n: Int, locale: Locale): String = grouped(n, locale) + " rpm"
}
