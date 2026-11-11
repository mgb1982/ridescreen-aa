package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipMathTest {
    @Test fun rangeHeaderOnlyWhenResuming() {
        assertNull(ClipMath.rangeHeader(0))
        assertEquals("bytes=1048576-", ClipMath.rangeHeader(1_048_576))
    }

    @Test fun resumeModes() {
        assertEquals(ClipMath.Resume.APPEND, ClipMath.resumeMode(206, 500))
        assertEquals(ClipMath.Resume.RESTART, ClipMath.resumeMode(200, 500))   // server ignored Range
        assertEquals(ClipMath.Resume.RESTART, ClipMath.resumeMode(200, 0))
        assertEquals(ClipMath.Resume.FAIL, ClipMath.resumeMode(416, 500))
        assertEquals(ClipMath.Resume.FAIL, ClipMath.resumeMode(404, 0))
    }

    @Test fun overlapWithMargin() {
        val trip0 = 1_000_000L; val trip1 = 2_000_000L; val m = 60_000L
        assertTrue(ClipMath.overlaps(trip0 - 90_000, 60_000, trip0, trip1, m))   // ends 30 s before the start
        assertFalse(ClipMath.overlaps(trip0 - 200_000, 60_000, trip0, trip1, m))
        assertTrue(ClipMath.overlaps(trip1 + 30_000, 60_000, trip0, trip1, m))
        assertFalse(ClipMath.overlaps(trip1 + 61_000, 60_000, trip0, trip1, m))
    }

    @Test fun nameParsingAndSpace() {
        assertNotNull(ClipMath.startFromName("1794398380-2026-11-11-11-59-40-front.mp4"))
        assertNull(ClipMath.startFromName("garbage.mp4"))
        assertTrue(ClipMath.fits(100L shl 20, 400L shl 20))
        assertFalse(ClipMath.fits(300L shl 20, 400L shl 20))
    }
}
