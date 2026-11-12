// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure rule for the "bike battery is low" warning (no Android imports). The engine must be stopped
 * (a running engine reads 13-14 V), the reading must be reliable (null = no warning) and one warning
 * per 24 h at most, unless the voltage has risen above [REARM_VOLTS] and fallen again since.
 *
 * Two limits: right after parking the dash, the OBD adapter and a just-stopped engine pull the
 * battery down (a healthy 125X read 12.3 V there and 12.8 V 17 min later, 12 Nov log), so the
 * parking check only warns below [WARN_AT_PARKING_VOLTS]. A reading taken [REST_MS] or more after
 * parking is a resting voltage and uses the normal [WARN_BELOW_VOLTS].
 */
object BatteryAlert {
    /** Resting voltage below this = weak battery (≈50 % charge on a 12 V lead-acid). */
    const val WARN_BELOW_VOLTS = 12.2
    /** Right at parking, under load: only a clearly low voltage warns. */
    const val WARN_AT_PARKING_VOLTS = 12.0
    const val REARM_VOLTS = 12.6
    const val MIN_GAP_MS = 24 * 3_600_000L
    /** Time after parking from which a reading counts as a resting voltage. */
    const val REST_MS = 10 * 60_000L

    /**
     * [rearmed]: the voltage went above [REARM_VOLTS] after the last warning (see [nextRearmed]).
     * [atRest]: the reading was taken [REST_MS] or more after parking (see [isAtRest]).
     */
    fun shouldWarn(
        volt: Double?, engineRunning: Boolean, lastWarnAtMs: Long, nowMs: Long,
        rearmed: Boolean = false, atRest: Boolean = false,
    ): Boolean {
        if (volt == null || volt.isNaN() || volt <= 0.0 || engineRunning) return false
        if (volt >= if (atRest) WARN_BELOW_VOLTS else WARN_AT_PARKING_VOLTS) return false
        if (lastWarnAtMs <= 0L) return true
        return rearmed || nowMs - lastWarnAtMs >= MIN_GAP_MS || nowMs < lastWarnAtMs
    }

    /** True when a reading taken at [readingAtMs] is a resting voltage for a bike parked at [parkedAtMs]. */
    fun isAtRest(parkedAtMs: Long, readingAtMs: Long): Boolean =
        parkedAtMs > 0L && readingAtMs - parkedAtMs >= REST_MS

    /** Update the re-arm flag with a new reading: true once it has been above [REARM_VOLTS]. */
    fun nextRearmed(volt: Double?, rearmed: Boolean): Boolean = rearmed || (volt != null && volt > REARM_VOLTS)
}
