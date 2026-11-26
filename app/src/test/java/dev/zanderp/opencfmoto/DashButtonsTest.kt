package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.DashButtons.Command
import dev.zanderp.opencfmoto.DashButtons.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashButtonsTest {

    @Test fun parsesTheOfficialPayload() {
        assertEquals(Event(4161, 2), DashButtons.parse("""{"code":4161,"type":2}"""))
        assertEquals(Event(4359, 4), DashButtons.parse("""{"type":4,"code":4359}"""))
    }

    @Test fun unreadableOrEmptyPayloadIsNull() {
        assertNull(DashButtons.parse(""))
        assertNull(DashButtons.parse("not json"))
        assertNull(DashButtons.parse("""{"type":2}"""))
    }

    @Test fun musicAndVolumeWorkInBothModes() {
        for (controlAa in listOf(false, true)) {
            assertEquals(Command.Music(Command.Media.NEXT), DashButtons.map(Event(4161, 2), controlAa))
            assertEquals(Command.Music(Command.Media.PREVIOUS), DashButtons.map(Event(4162, 2), controlAa))
            assertEquals(Command.Music(Command.Media.PLAY_PAUSE), DashButtons.map(Event(4167, 2), controlAa))
            assertEquals(Command.Music(Command.Media.PLAY), DashButtons.map(Event(4160, 2), controlAa))
            assertEquals(Command.Music(Command.Media.PAUSE), DashButtons.map(Event(4163, 2), controlAa))
            assertEquals(Command.Music(Command.Media.PAUSE), DashButtons.map(Event(4164, 2), controlAa))
            assertEquals(Command.Volume(up = true), DashButtons.map(Event(4176, 2), controlAa))
            assertEquals(Command.Volume(up = false), DashButtons.map(Event(4177, 4), controlAa))
        }
    }

    @Test fun androidAutoKeysOnlyWhenButtonsControlAndroidAuto() {
        val aaKeys = mapOf(
            4355 to ButtonAction.DPAD_LEFT, 4356 to ButtonAction.DPAD_RIGHT,
            4357 to ButtonAction.DPAD_UP, 4358 to ButtonAction.DPAD_DOWN,
            4359 to ButtonAction.SELECT, 4224 to ButtonAction.BACK, 4144 to ButtonAction.ASSISTANT,
            4353 to ButtonAction.KNOB_BACK, 4354 to ButtonAction.KNOB_FORWARD,
        )
        for ((code, action) in aaKeys) {
            assertEquals(Command.AndroidAuto(action), DashButtons.map(Event(code, 2), controlAa = true))
            assertNull(DashButtons.map(Event(code, 2), controlAa = false))
        }
    }

    @Test fun doubleTapsAndUnknownKeysAreIgnoredLikeTheOfficialApp() {
        assertNull(DashButtons.map(Event(4161, 3), controlAa = true))
        assertNull(DashButtons.map(Event(4161, 0), controlAa = true))
        assertNull(DashButtons.map(Event(4208, 2), controlAa = true))   // MODE
        assertNull(DashButtons.map(Event(4128, 2), controlAa = true))   // NAVIGATION
        assertNull(DashButtons.map(Event(9999, 2), controlAa = true))
    }

    @Test fun trackKeysAreSpacedHalfASecondApart() {
        val gate = DashButtons.TrackKeyGate()
        val next = Command.Music(Command.Media.NEXT)
        val prev = Command.Music(Command.Media.PREVIOUS)
        assertTrue(gate.allow(next, 10_000))
        assertFalse(gate.allow(next, 10_300))
        assertFalse(gate.allow(prev, 10_499))
        assertTrue(gate.allow(prev, 10_500))
        // Other keys are never held back, and don't move the window.
        assertTrue(gate.allow(Command.Volume(up = true), 10_501))
        assertTrue(gate.allow(Command.Music(Command.Media.PLAY_PAUSE), 10_502))
        assertFalse(gate.allow(next, 10_999))
        assertTrue(gate.allow(next, 11_000))
    }
}
