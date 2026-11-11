package dev.zanderp.opencfmoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkIdleTest {
    @Test fun neverPulledIsNotIdle() = assertFalse(LinkIdle.isIdle(0L, 99_999_999L))
    @Test fun slowDashIsNotIdle() = assertFalse(LinkIdle.isIdle(10_000L, 10_000L + 300))
    @Test fun twoSecondsWithoutPullIsIdle() = assertTrue(LinkIdle.isIdle(10_000L, 10_000L + LinkIdle.IDLE_MS))
}
