package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdPaceTest {
    @Test fun dashboardMeansNoGap() = assertEquals(0L, ObdPace.gapMs(true, 150))
    @Test fun withoutDashboardFillsTo700() = assertEquals(550L, ObdPace.gapMs(false, 150))
    @Test fun slowRoundNeedsNoGap() = assertEquals(0L, ObdPace.gapMs(false, 900))
}
