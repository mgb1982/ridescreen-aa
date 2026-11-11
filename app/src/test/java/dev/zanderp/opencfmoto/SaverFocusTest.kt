package dev.zanderp.opencfmoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverFocusTest {
    @Test fun channelOpenIsAlwaysGain() = assertTrue(SaverFocus.CHANNEL_OPEN_GAIN)

    @Test fun lossOnlyAfterSteadyWithDashboardAndSaver() {
        assertTrue(SaverFocus.shouldApplyLoss(dashboardWanted = true, saverOn = true, sessionSteady = true))
        assertFalse(SaverFocus.shouldApplyLoss(true, true, sessionSteady = false))   // new session, not steady yet
        assertFalse(SaverFocus.shouldApplyLoss(true, saverOn = false, sessionSteady = true))
        assertFalse(SaverFocus.shouldApplyLoss(dashboardWanted = false, saverOn = true, sessionSteady = true))
    }

    @Test fun projectedRequestRefusedOnlyWhileHoldingASteadySession() {
        assertFalse(SaverFocus.projectedReplyGain(saverHeld = true, sessionSteady = true))
        assertTrue(SaverFocus.projectedReplyGain(saverHeld = true, sessionSteady = false))
        assertTrue(SaverFocus.projectedReplyGain(saverHeld = false, sessionSteady = true))
    }
}
