package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.ButtonVolumeLogic.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonVolumeLogicTest {
    // ---- B1 · a jump to 0 is the dash muting, not a press

    @Test fun jumpToZeroWithPinAboveZeroIsMuted() {
        assertEquals(Change.MUTED, ButtonVolumeLogic.classify(pinned = 7, now = 0))
        assertEquals(Change.MUTED, ButtonVolumeLogic.classify(pinned = 1, now = 0))
    }

    @Test fun ordinaryPressesKeepTheirDirection() {
        assertEquals(Change.UP, ButtonVolumeLogic.classify(7, 8))
        assertEquals(Change.UP, ButtonVolumeLogic.classify(7, 15))
        assertEquals(Change.DOWN, ButtonVolumeLogic.classify(7, 6))
        assertEquals(Change.DOWN, ButtonVolumeLogic.classify(7, 1))      // one step above mute is still a press
        assertEquals(Change.NONE, ButtonVolumeLogic.classify(7, 7))
    }

    // ---- B2 · a real press after ABSENT

    private fun repin(
        rocker: ButtonPresence = ButtonPresence.ABSENT, volumeToAa: Boolean = true, streaming: Boolean = true,
        previous: Int = 7, now: Int = 8, already: Boolean = false,
    ) = ButtonVolumeLogic.shouldRepinAfterAbsent(rocker, volumeToAa, streaming, previous, now, already)

    @Test fun absentThenAChangeWhileStreamingRepins() { assertTrue(repin()) }

    @Test fun onlyWhenAbsent() {
        assertFalse(repin(rocker = ButtonPresence.UNKNOWN))
        assertFalse(repin(rocker = ButtonPresence.PRESENT))
    }

    @Test fun neverWhenTheSettingLeavesVolumeToThePhone() { assertFalse(repin(volumeToAa = false)) }

    @Test fun needsTheBikeStreaming() { assertFalse(repin(streaming = false)) }

    @Test fun noChangeOrNoBaselineIsNotAPress() {
        assertFalse(repin(previous = 7, now = 7))
        assertFalse(repin(previous = -1, now = 7))
    }

    @Test fun onlyOncePerSession() { assertFalse(repin(already = true)) }

    // ---- B3 · a key never released

    @Test fun unreleasedKeyIsATapBelowTheLongThreshold() {
        assertFalse(ButtonVolumeLogic.isLongPress(heldMs = 400, holdsEnabled = true, longPressMs = 600))
        assertFalse(ButtonVolumeLogic.isLongPress(heldMs = 5_000, holdsEnabled = false, longPressMs = 600))   // holds off: always a tap
    }

    @Test fun unreleasedKeyHeldPastTheThresholdIsLong() {
        assertTrue(ButtonVolumeLogic.isLongPress(heldMs = 5_000, holdsEnabled = true, longPressMs = 600))
        assertTrue(ButtonVolumeLogic.isLongPress(heldMs = 600, holdsEnabled = true, longPressMs = 600))
    }

    @Test fun watchdogIsFiveSeconds() { assertEquals(5_000L, ButtonVolumeLogic.NO_RELEASE_MS) }
}
