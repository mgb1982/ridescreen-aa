package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudMathTest {
    private fun m(t: Long, lean: Double? = null, gLat: Double? = null, gLon: Double? = null) = MotionTripSample(t, lean, gLat, gLon, null)

    @Test fun beatIsZeroWithoutPulse() { assertEquals(0f, HudMath.beat(500, null), 0f); assertEquals(0f, HudMath.beat(500, 0), 0f) }

    @Test fun beatPeaksEarlyInTheCycleAndRests() {
        // 60 bpm: period 1000 ms. Peak of the first beat at 90 ms, rest at 600 ms.
        assertEquals(1f, HudMath.beat(90, 60), 0.02f)
        assertEquals(0f, HudMath.beat(600, 60), 0f)
        assertTrue(HudMath.beat(350, 60) in 0.9f * 0.5f..0.5f)
    }

    @Test fun peakLeanKeepsSignAndWindow() {
        val s = listOf(m(0, 40.0), m(5_000, -30.0), m(9_000, 12.0))
        assertEquals(-30.0, HudMath.peakLean(s, 9_500, 5_000)!!, 0.0)   // the 40° at t=0 is out of the window
        assertEquals(40.0, HudMath.peakLean(s, 9_500, 10_000)!!, 0.0)
        assertEquals(null, HudMath.peakLean(s, 30_000, 5_000))
    }

    @Test fun recordsOnlyWhenBeatingTheBest() {
        val s = listOf(m(0, 10.0), m(1, 20.0), m(2, 20.5), m(3, -22.0), m(4, 21.0), m(5, 30.0))
        assertEquals(listOf(1L, 3L, 5L), HudMath.leanRecords(s))
    }

    @Test fun flashFadesOut() {
        val r = listOf(1_000L, 5_000L)
        assertEquals(1f, HudMath.flash(r, 1_000), 0f)
        assertEquals(0.5f, HudMath.flash(r, 1_600), 0.001f)
        assertEquals(0f, HudMath.flash(r, 3_000), 0f)
        assertEquals(0f, HudMath.flash(r, 500), 0f)
    }

    @Test fun bannersSkipSmallPeaks() {
        val speed = listOf(0L to 20.0, 1_000L to 30.0)
        assertTrue(HudMath.banners(speed, listOf(m(0, 5.0, 0.1, 0.1))).isEmpty())
        val b = HudMath.banners(listOf(0L to 20.0, 4_000L to 94.0), listOf(m(2_000, 38.0, 0.6, 0.2), m(3_000, -10.0, 0.9, 0.1)))
        assertEquals(listOf(HudMath.Kind.MAX_LEAN, HudMath.Kind.MAX_G, HudMath.Kind.TOP_SPEED), b.map { it.kind })
        assertTrue(b.first { it.kind == HudMath.Kind.MAX_LEAN }.right)
    }

    @Test fun bannerFades() {
        val b = HudMath.Banner(10_000, HudMath.Kind.TOP_SPEED, 90.0)
        assertEquals(0f, HudMath.bannerAlpha(b, 9_000), 0f)
        assertEquals(1f, HudMath.bannerAlpha(b, 10_500), 0f)
        assertEquals(0f, HudMath.bannerAlpha(b, 13_000), 0f)
        assertTrue(HudMath.bannerAlpha(b, 9_700) in 0.1f..0.9f)
    }

    @Test fun litSegments() {
        assertEquals(10, HudMath.litSegments(5.0, 10.0)); assertEquals(20, HudMath.litSegments(99.0, 10.0)); assertEquals(0, HudMath.litSegments(1.0, 0.0))
    }
}
