package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteHitTest {
    private val a = floatArrayOf(0f, 0f, 100f, 0f)
    private val b = floatArrayOf(0f, 50f, 100f, 50f, 100f, 100f)

    @Test fun segmentDistanceInsideAndBeyondEnds() {
        assertEquals(10f, RouteHit.segmentDistance(50f, 10f, 0f, 0f, 100f, 0f), 0.001f)
        assertEquals(5f, RouteHit.segmentDistance(-3f, 4f, 0f, 0f, 100f, 0f), 0.001f)
        assertEquals(5f, RouteHit.segmentDistance(3f, 4f, 0f, 0f, 0f, 0f), 0.001f)
    }

    @Test fun picksClosestRoute() {
        assertEquals(0, RouteHit.nearest(listOf(a, b), 50f, 8f, 20f))
        assertEquals(1, RouteHit.nearest(listOf(a, b), 50f, 42f, 20f))
        assertEquals(1, RouteHit.nearest(listOf(a, b), 108f, 80f, 20f))
    }

    @Test fun gapAndDegenerateReturnMinusOne() {
        assertEquals(-1, RouteHit.nearest(listOf(a, b), 50f, 25f, 10f))
        assertEquals(-1, RouteHit.nearest(listOf(floatArrayOf(1f, 1f)), 1f, 1f, 10f))
        assertEquals(-1, RouteHit.nearest(emptyList(), 0f, 0f, 10f))
    }
}
