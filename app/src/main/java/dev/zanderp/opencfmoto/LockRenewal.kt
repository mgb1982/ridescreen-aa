// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * The CPU and screen wake locks are taken with a 4 h ceiling. On a long ride they expire silently, so the
 * watchdog takes them again, but only when the service still wants them: a lock released on purpose
 * (bike parked, service stopping) must stay released. Pure, no Android types (unit tested).
 */
internal object LockRenewal {
    const val MIN_GAP_MS = 30_000L

    /** [lastAttemptMs] = 0 means no attempt yet. */
    fun due(wanted: Boolean, held: Boolean, nowMs: Long, lastAttemptMs: Long, minGapMs: Long = MIN_GAP_MS): Boolean =
        wanted && !held && (lastAttemptMs == 0L || nowMs - lastAttemptMs >= minGapMs)
}
