package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdPaceTest {
    @Test fun dashboardMeansNoGap() = assertEquals(0L, ObdPace.gapMs(true, 150))
    @Test fun withoutDashboardFillsTo700() = assertEquals(550L, ObdPace.gapMs(false, 150))
    @Test fun slowRoundNeedsNoGap() = assertEquals(0L, ObdPace.gapMs(false, 900))
}

class ObdPaceTripRateTest {
    /** F3 · without the dashboard the trip still gets at least one OBD sample a second (1 round ≈ 1 sample). */
    @org.junit.Test fun slowPaceStillFeedsTheTripEverySecond() =
        org.junit.Assert.assertTrue(ObdPace.SLOW_ROUND_MS <= 1_000L)
}

class ObdPaceShouldReadTest {
    @org.junit.Test fun rpmAndSpeedEveryRound() {
        for (r in 0..30) {
            org.junit.Assert.assertTrue(ObdPace.shouldRead(0x0C, r))
            org.junit.Assert.assertTrue(ObdPace.shouldRead(0x0D, r))
        }
    }
    @org.junit.Test fun loadAndThrottleEveryThird() {
        org.junit.Assert.assertTrue(ObdPace.shouldRead(0x45, 0))
        org.junit.Assert.assertFalse(ObdPace.shouldRead(0x45, 1))
        org.junit.Assert.assertFalse(ObdPace.shouldRead(0x04, 2))
        org.junit.Assert.assertTrue(ObdPace.shouldRead(0x04, 3))
    }
    @org.junit.Test fun slowPidsEveryTenth() {
        for (pid in listOf(0x05, 0x42, 0x0F, 0x33, 0x43)) {
            org.junit.Assert.assertTrue(ObdPace.shouldRead(pid, 0))
            org.junit.Assert.assertFalse(ObdPace.shouldRead(pid, 5))
            org.junit.Assert.assertTrue(ObdPace.shouldRead(pid, 10))
        }
    }
    @org.junit.Test fun roundZeroReadsEverything() {
        for (pid in listOf(0x0C, 0x0D, 0x05, 0x42, 0x04, 0x0F, 0x33, 0x43, 0x45))
            org.junit.Assert.assertTrue(ObdPace.shouldRead(pid, 0))
    }
}
