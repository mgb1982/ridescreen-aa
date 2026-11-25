package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottleScaleTest {
    @Test fun fullGripAtDefaultCapReads100() = assertEquals(100.0, ThrottleScale.scale(65.0, ThrottleScale.DEFAULT_CAP), 1e-9)
    @Test fun zeroStaysZero() = assertEquals(0.0, ThrottleScale.scale(0.0, 65.0), 1e-9)
    @Test fun halfWay() = assertEquals(50.0, ThrottleScale.scale(32.5, 65.0), 1e-9)
    @Test fun clampsHighAndLow() {
        assertEquals(100.0, ThrottleScale.scale(80.0, 65.0), 1e-9)
        assertEquals(0.0, ThrottleScale.scale(-3.0, 65.0), 1e-9)
    }
    @Test fun capRaisesOnHigherRaw() = assertEquals(70.0, ThrottleScale.nextCap(65.0, 70.0), 1e-9)
    @Test fun capNeverLowers() {
        assertEquals(65.0, ThrottleScale.nextCap(65.0, 30.0), 1e-9)
        assertEquals(65.0, ThrottleScale.nextCap(65.0, 65.0), 1e-9)
    }
    @Test fun capNeverAbove100() = assertEquals(100.0, ThrottleScale.nextCap(65.0, 130.0), 1e-9)
    @Test fun raisedCapKeepsScaleSane() {
        val cap = ThrottleScale.nextCap(65.0, 100.0)
        assertEquals(100.0, ThrottleScale.scale(100.0, cap), 1e-9)
        assertEquals(50.0, ThrottleScale.scale(50.0, cap), 1e-9)
    }
}

class ThrottleResetTest {
    @Test fun resetGoesBackToDefault() = assertEquals(ThrottleScale.DEFAULT_CAP, ThrottleScale.resetValue(), 0.0)
    @Test fun resetUndoesASpike() {
        val spiked = ThrottleScale.nextCap(65.5, 100.0)
        assertEquals(100.0, spiked, 0.0)
        assertEquals(65.0, ThrottleScale.resetValue(), 0.0)
        // after reset a full grip (65.5 raw) raises the cap to the real value again
        assertEquals(65.5, ThrottleScale.nextCap(ThrottleScale.resetValue(), 65.5), 1e-9)
    }
    @Test fun resetLineAndCapText() {
        assertEquals("throttle cap reset to 65 (was 65.5)", ThrottleScale.resetLine(65.5))
        assertEquals("100.0", ThrottleScale.capText(100.0))
    }
}

class ThrottlePeakTest {
    @Test fun emptyWindowGivesNull() = org.junit.Assert.assertNull(ThrottlePeak().take())
    @Test fun keepsMaxAndItsAbs() {
        val p = ThrottlePeak()
        p.add(20.0, 30.0); p.add(64.0, 68.5); p.add(40.0, 50.0)
        val r = p.take()!!
        assertEquals(64.0, r.raw, 1e-9)
        assertEquals(68.5, r.abs!!, 1e-9)
    }
    @Test fun absNullWhenNotReadAtMax() {
        val p = ThrottlePeak()
        p.add(10.0, 20.0); p.add(50.0, null)
        org.junit.Assert.assertNull(p.take()!!.abs)
    }
    @Test fun takeResets() {
        val p = ThrottlePeak()
        p.add(50.0, null); p.take()
        org.junit.Assert.assertNull(p.take())
        p.add(5.0, null)
        assertEquals(5.0, p.take()!!.raw, 1e-9)
    }
    @Test fun lineFormat() {
        assertEquals("throttle peak 60 s: raw 65.0%, scaled 100.0%, cap 65.0, abs 68.6",
            ThrottlePeak.line(ThrottlePeak.Result(65.0, 68.6), 65.0))
        assertEquals("throttle peak 60 s: raw 32.5%, scaled 50.0%, cap 65.0",
            ThrottlePeak.line(ThrottlePeak.Result(32.5, null), 65.0))
    }
}
