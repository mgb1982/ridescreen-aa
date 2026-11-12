// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

/**
 * Pure rules for [LocationHub]: how to combine several subscribers' requests into one registration,
 * and which fixes each subscriber still gets so nobody sees a different cadence than it asked for.
 */
object LocationFanout {
    internal data class Spec(val provider: String, val minTimeMs: Long, val minDistanceM: Float)
    internal data class Plan(val minTimeMs: Long, val minDistanceM: Float)

    /** Combined request: fastest time; distance 0 if anyone wants every fix, else the smallest. */
    internal fun plan(specs: List<Spec>): Plan? {
        if (specs.isEmpty()) return null
        val time = specs.minOf { it.minTimeMs }
        val dist = if (specs.any { it.minDistanceM <= 0f }) 0f else specs.minOf { it.minDistanceM }
        return Plan(time, dist)
    }

    /**
     * Whether [spec]'s subscriber gets this fix. [movedM] is the distance from the last position
     * delivered to that subscriber (null if none). Time is only throttled for a subscriber clearly
     * slower than the hub: GPS jitter must not drop alternate fixes of a 1 s subscriber.
     */
    internal fun shouldDeliver(
        spec: Spec,
        hubMinTimeMs: Long,
        lastDeliveredAtMs: Long,
        nowMs: Long,
        movedM: Float?,
    ): Boolean {
        if (lastDeliveredAtMs == 0L) return true
        if (spec.minTimeMs >= 2 * hubMinTimeMs && nowMs - lastDeliveredAtMs < spec.minTimeMs - 250L) return false
        if (spec.minDistanceM > 0f && movedM != null && movedM < spec.minDistanceM) return false
        return true
    }
}
