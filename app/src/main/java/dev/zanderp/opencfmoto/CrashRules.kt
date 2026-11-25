// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** Pure rules of [CrashDetector] (JVM-testable). */
internal object CrashRules {
    /** Since when the rider has been moving at ≥ [minKmh] without a break (0 = not moving or no speed). */
    fun movingSince(since: Long, kmh: Float?, minKmh: Float, now: Long): Long = when {
        kmh == null || kmh < minKmh -> 0L
        since == 0L -> now
        else -> since
    }

    /** Riding on for [keepMs] after an impact: it was a bump, not a crash. */
    fun rodeOn(since: Long, now: Long, keepMs: Long): Boolean = since > 0L && now - since >= keepMs
}
