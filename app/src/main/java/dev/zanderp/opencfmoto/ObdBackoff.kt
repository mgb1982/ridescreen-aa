// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Wait before the live OBD reader tries the adapter again (pure, unit tested). Short at first, so a
 * dropped link comes back quickly; then longer, because an adapter that has failed again and again
 * is usually unpowered (bike switched off). Each failed try costs ~13 s of Bluetooth connects; with a
 * 30 s cap that was one every ~50 s for as long as the bike stayed off (12 Nov log: 25 tries in 17 min).
 */
internal object ObdBackoff {
    /** Wait after the 1st, 2nd, 3rd… consecutive failure; the last value repeats. */
    val STEPS_MS = longArrayOf(5_000L, 10_000L, 20_000L, 30_000L, 60_000L, 120_000L, 300_000L)

    /** While projecting the bike is on, so the adapter should have power: keep the old 30 s cap. */
    const val PROJECTING_MAX_MS = 30_000L

    fun waitAfter(consecutiveFailures: Int, projecting: Boolean = false): Long {
        val w = if (consecutiveFailures <= 0) STEPS_MS[0] else STEPS_MS[minOf(consecutiveFailures, STEPS_MS.size) - 1]
        return if (projecting) minOf(w, PROJECTING_MAX_MS) else w
    }
}
