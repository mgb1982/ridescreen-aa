// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Pure hit-testing of a tap against polylines given in screen pixels (no Android). */
object RouteHit {
    /** Distance in px from (px,py) to the segment (ax,ay)-(bx,by). */
    fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * Index of the route closest to the tap within [maxDistPx], or -1. Each route is a flat
     * array x0,y0,x1,y1,... Routes whose bounding box is farther than [maxDistPx] are skipped fast.
     */
    fun nearest(routes: List<FloatArray>, x: Float, y: Float, maxDistPx: Float): Int {
        var best = -1
        var bestD = maxDistPx
        for ((i, r) in routes.withIndex()) {
            if (r.size < 4) continue
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var k = 0
            while (k + 1 < r.size) {
                minX = min(minX, r[k]); maxX = max(maxX, r[k])
                minY = min(minY, r[k + 1]); maxY = max(maxY, r[k + 1])
                k += 2
            }
            if (x < minX - maxDistPx || x > maxX + maxDistPx || y < minY - maxDistPx || y > maxY + maxDistPx) continue
            k = 0
            while (k + 3 < r.size) {
                val d = segmentDistance(x, y, r[k], r[k + 1], r[k + 2], r[k + 3])
                if (d <= bestD) { bestD = d; best = i }
                k += 2
            }
        }
        return best
    }
}
