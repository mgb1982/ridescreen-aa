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
