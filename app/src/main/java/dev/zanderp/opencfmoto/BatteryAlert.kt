// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure rule for the "bike battery is low" warning (no Android imports). The engine must be stopped
 * (a running engine reads 13-14 V), the reading must be reliable (null = no warning) and one warning
 * per 24 h at most, unless the voltage has risen above [REARM_VOLTS] and fallen again since.
 */
object BatteryAlert {
    const val WARN_BELOW_VOLTS = 12.2
    const val REARM_VOLTS = 12.6
    const val MIN_GAP_MS = 24 * 3_600_000L

    /** [rearmed]: the voltage went above [REARM_VOLTS] after the last warning (see [nextRearmed]). */
    fun shouldWarn(volt: Double?, engineRunning: Boolean, lastWarnAtMs: Long, nowMs: Long, rearmed: Boolean = false): Boolean {
        if (volt == null || volt.isNaN() || volt <= 0.0 || engineRunning) return false
        if (volt >= WARN_BELOW_VOLTS) return false
        if (lastWarnAtMs <= 0L) return true
        return rearmed || nowMs - lastWarnAtMs >= MIN_GAP_MS || nowMs < lastWarnAtMs
    }

    /** Update the re-arm flag with a new reading: true once it has been above [REARM_VOLTS]. */
    fun nextRearmed(volt: Double?, rearmed: Boolean): Boolean = rearmed || (volt != null && volt > REARM_VOLTS)
}
