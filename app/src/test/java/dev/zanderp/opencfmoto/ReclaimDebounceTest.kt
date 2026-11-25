package dev.zanderp.opencfmoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReclaimDebounceTest {
    @Test fun firstCallRuns() = assertTrue(ReclaimDebounce.shouldRun(null, 0L))

    @Test fun callsWithinWindowSkipped() {
        assertFalse(ReclaimDebounce.shouldRun(10_000L, 10_050L))
        assertFalse(ReclaimDebounce.shouldRun(10_000L, 11_999L))
    }

    @Test fun callAtOrAfterWindowRuns() {
        assertTrue(ReclaimDebounce.shouldRun(10_000L, 12_000L))
        assertTrue(ReclaimDebounce.shouldRun(10_000L, 60_000L))
    }

    @Test fun clockGoingBackwardsRuns() = assertTrue(ReclaimDebounce.shouldRun(10_000L, 5_000L))
}
