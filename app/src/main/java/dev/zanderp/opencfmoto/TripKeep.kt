// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

/** Pure rules for when a trip keeps going and why a leg is thrown away (JVM-testable). */
object TripKeep {
    /** How long a dropped link may last before the trip is closed anyway. */
    const val LINK_GAP_GRACE_MS = 10 * 60_000L

    /** Phases where the app is still trying to get the bike back: the ride is not over. */
    fun isLinkGap(p: Phase): Boolean = when (p) {
        Phase.RECONNECTING, Phase.WAITING_FOR_BIKE, Phase.JOINING_WIFI,
        Phase.PXC_CONNECTING, Phase.STARTING_AA, Phase.AA_VIDEO_LIVE -> true
        else -> false
    }

    fun keepRecording(live: Boolean, phase: Phase, linkLostAt: Long, now: Long): Boolean {
        if (live) return true
        if (!isLinkGap(phase) || linkLostAt <= 0L) return false
        return now - linkLostAt < LINK_GAP_GRACE_MS
    }

    /** null = keep the leg; otherwise a short reason for the log. */
    fun discardReason(distanceM: Double, movingMs: Long, points: Int, minDistM: Double, minMovingMs: Long): String? {
        val r = ArrayList<String>(3)
        if (distanceM < minDistM) r += "only ${distanceM.toInt()} m (min ${minDistM.toInt()})"
        if (movingMs < minMovingMs) r += "only ${movingMs / 1000} s moving (min ${minMovingMs / 1000})"
        if (points < 2) r += "$points GPS point(s) — no usable location (background GPS limited?)"
        return if (r.isEmpty()) null else r.joinToString(", ")
    }
}
