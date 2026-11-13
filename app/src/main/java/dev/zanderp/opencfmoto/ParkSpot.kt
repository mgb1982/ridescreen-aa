// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Which position is "where I parked" (pure, unit tested). The bike's Wi-Fi goes away when the
 * contact is switched off, but the ride is only declared over 45 s later (or when the rider taps
 * Stop), by which time the rider has walked off: the phone's position *then* is not the bike's.
 * The spot is therefore the last position seen while the bike link was still up.
 */
internal object ParkSpot {
    /** A position at [timeMs] (epoch ms) with its accuracy in metres (0 = unknown). */
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Int, val timeMs: Long)

    /** A position from the linked time older than this is not trusted any more (a new ride would have replaced it). */
    const val MAX_LINKED_AGE_MS = 30 * 60_000L

    /** How old the position noted while linked may be when it is taken. */
    const val MAX_NOTE_AGE_MS = 30_000L

    /**
     * [linked]: last position noted while the link was up (null: none). [current]: the phone's
     * position now (null: none). The linked one wins while it is recent enough.
     */
    fun pick(linked: Fix?, current: Fix?, nowMs: Long): Fix? {
        if (linked != null && nowMs - linked.timeMs in 0..MAX_LINKED_AGE_MS) return linked
        return current
    }
}
