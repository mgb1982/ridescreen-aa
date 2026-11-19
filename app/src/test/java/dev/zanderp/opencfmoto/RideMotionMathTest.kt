// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.sqrt

class RideMotionMathTest {
    private val g = RideMotionMath.G

    @Test fun leftTurnLeansNegative() {
        // 20 m/s (72 km/h), yaw rate +0.3 rad/s about up = left turn -> lateral 6 m/s2 to the left
        val lean = RideMotionMath.leanDeg(20.0, 0.3)
        assertTrue(lean < 0)
        assertEquals(-Math.toDegrees(atan(6.0 / g)), lean, 1e-9)
        assertTrue(RideMotionMath.gLat(20.0, 0.3) < 0)
    }

    @Test fun rightTurnLeansPositive() {
        assertEquals(Math.toDegrees(atan(6.0 / g)), RideMotionMath.leanDeg(20.0, -0.3), 1e-9)
        assertEquals(6.0 / g, RideMotionMath.gLat(20.0, -0.3), 1e-9)
    }

    @Test fun belowTenKmhLeanIsZero() {
        assertEquals(0.0, RideMotionMath.leanDeg(9.9 / 3.6, 1.0), 0.0)
        assertTrue(RideMotionMath.leanDeg(10.1 / 3.6, 1.0) != 0.0)
    }

    @Test fun leanIsClampedAt65() {
        assertEquals(-65.0, RideMotionMath.leanDeg(30.0, 3.0), 0.0)
        assertEquals(65.0, RideMotionMath.leanDeg(30.0, -3.0), 0.0)
    }

    @Test fun straightRoadIsZero() {
        assertEquals(0.0, RideMotionMath.leanDeg(25.0, 0.0), 1e-12)
    }

    @Test fun speedWindowGivesLongitudinalAcceleration() {
        val w = RideMotionMath.SpeedWindow()
        assertNull(w.accel())
        w.add(1_000, 10.0)
        assertNull(w.accel())
        w.add(1_100, 10.5) // under 0.4 s apart: not enough
        assertNull(w.accel())
        w.add(2_000, 12.0)
        // oldest kept sample is 1000 (window 1.5 s): (12 - 10) / 1 s
        assertEquals(2.0, w.accel()!!, 1e-9)
        assertEquals(2.0 / g, w.accel()!! / g, 1e-9)
        w.add(3_000, 9.0) // braking: window now holds 2000..3000 only
        assertEquals(-3.0, w.accel()!!, 1e-9)
    }

    @Test fun speedWindowIgnoresOutOfOrder() {
        val w = RideMotionMath.SpeedWindow()
        w.add(2_000, 5.0); w.add(1_000, 50.0); w.add(3_000, 7.0)
        assertEquals(2.0 / 1.0, w.accel()!!, 1e-9)
    }

    @Test fun emaMovesTowardsTheTarget() {
        assertEquals(10.0, RideMotionMath.ema(null, 10.0, 20, 300.0), 0.0)
        val one = RideMotionMath.ema(0.0, 10.0, 300, 300.0)
        assertEquals(10.0 * (1 - Math.exp(-1.0)), one, 1e-9)
        assertEquals(10.0, RideMotionMath.ema(0.0, 10.0, 60_000, 300.0), 1e-6)
    }

    @Test fun angleBetweenVectors() {
        assertEquals(0.0, RideMotionMath.angleDeg(doubleArrayOf(0.0, 0.0, 9.8), doubleArrayOf(0.0, 0.0, 1.0)), 1e-6)
        assertEquals(90.0, RideMotionMath.angleDeg(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0)), 1e-6)
        val t = Math.toRadians(30.0)
        assertEquals(30.0, RideMotionMath.angleDeg(doubleArrayOf(Math.sin(t), 0.0, Math.cos(t)), doubleArrayOf(0.0, 0.0, 1.0)), 1e-6)
    }

    @Test fun calibrationAcceptsAStillPhone() {
        val c = RideMotionMath.CalibAccumulator()
        repeat(100) { c.onGyro(0.004, -0.002, 0.003); c.onAccel(0.5, 1.0, 9.7) }
        assertEquals(CalibResult.OK, c.verdict(null))
        assertEquals(CalibResult.OK, c.verdict(2.0))
        val up = c.up()!!
        assertEquals(1.0, sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2]), 1e-9)
        assertTrue(up[2] > 0.9)
    }

    @Test fun calibrationRefusals() {
        val still = RideMotionMath.CalibAccumulator()
        repeat(100) { still.onGyro(0.0, 0.0, 0.0); still.onAccel(0.0, 0.0, 9.81) }
        assertEquals(CalibResult.MOVING, still.verdict(3.0))
        val shaky = RideMotionMath.CalibAccumulator()
        repeat(100) { shaky.onGyro(0.0, 0.0, 0.2); shaky.onAccel(0.0, 0.0, 9.81) }
        assertEquals(CalibResult.NOT_STILL, shaky.verdict(null))
        val jolt = RideMotionMath.CalibAccumulator()
        repeat(100) { jolt.onGyro(0.0, 0.0, 0.0); jolt.onAccel(0.0, 0.0, if (it % 2 == 0) 9.81 else 12.0) }
        assertEquals(CalibResult.NOT_STILL, jolt.verdict(null))
        val none = RideMotionMath.CalibAccumulator()
        assertEquals(CalibResult.NO_SENSOR, none.verdict(null))
        assertNull(none.up())
    }

    @Test fun calibrationOutlierIsTolerated() {
        val c = RideMotionMath.CalibAccumulator()
        repeat(100) { c.onGyro(0.0, 0.0, if (it == 7) 0.3 else 0.0); c.onAccel(0.0, 0.0, 9.81) }
        assertEquals(CalibResult.OK, c.verdict(0.0))
    }

    @Test fun calibrationExpiresAfterTwelveHours() {
        val at = 1_000_000L
        assertTrue(RideMotionMath.calibrationValid(at, at + 11 * 3_600_000L))
        assertFalse(RideMotionMath.calibrationValid(at, at + 12 * 3_600_000L + 1))
        assertFalse(RideMotionMath.calibrationValid(0L, at))
        assertFalse(RideMotionMath.calibrationValid(at, at - 1))
    }

    @Test fun mountWatchDropsAfterFiveSecondsParkedAndTilted() {
        val m = RideMotionMath.MountWatch()
        assertFalse(m.update(0, 0.0, 40.0))
        assertFalse(m.update(4_000, 0.0, 40.0))
        assertTrue(m.update(5_100, 0.0, 40.0))
    }

    @Test fun mountWatchResetsWhenBackInPlaceOrMoving() {
        val m = RideMotionMath.MountWatch()
        m.update(1_000, 0.0, 40.0)
        assertFalse(m.update(3_000, 0.0, 10.0))     // back within 25 deg: timer restarts
        assertFalse(m.update(9_000, 0.0, 40.0))
        assertFalse(m.update(9_500, 20.0, 40.0))    // riding: leaning is normal, no judgement
        assertFalse(m.update(20_000, null, 40.0))   // unknown speed: no judgement
        assertFalse(m.update(21_000, 0.0, 40.0))
        assertNotNull(m)
    }
}
