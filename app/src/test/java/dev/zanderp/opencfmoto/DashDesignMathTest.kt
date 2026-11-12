// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.FocusHero.Hero
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashDesignMathTest {
    private val m = DashDesignMath

    @Test fun fractionClamps() {
        assertEquals(0f, m.fraction(null, 10.0), 0f)
        assertEquals(0f, m.fraction(-5.0, 10.0), 0f)
        assertEquals(1f, m.fraction(99.0, 10.0), 0f)
        assertEquals(.5f, m.fraction(5.0, 10.0), 1e-6f)
        assertEquals(0f, m.fraction(5.0, 0.0), 0f)
    }

    @Test fun tickAngles() {
        assertEquals(135f, m.tickAngle(0, 14), 0f)
        assertEquals(270f, m.tickAngle(7, 14), 1e-4f)
        assertEquals(405f, m.tickAngle(14, 14), 1e-4f)
    }

    @Test fun fillBelowAndAboveRed() {
        val low = m.fill(.62f, .9f)
        assertEquals(.62f, low.accentTo, 0f); assertFalse(low.hasRed)
        assertEquals(0f, m.sweep(low.redFrom, low.redTo), 0f)
        val hot = m.fill(.95f, .9f)
        assertEquals(.9f, hot.accentTo, 0f); assertTrue(hot.hasRed)
        assertEquals(.05f * 270f, m.sweep(hot.redFrom, hot.redTo), 1e-3f)
        assertEquals(0f, m.fill(-1f, .9f).accentTo, 0f)
    }

    @Test fun pitSegments() {
        assertEquals(0, m.litSegments(0f, 24))
        assertEquals(12, m.litSegments(.5f, 24))
        assertEquals(14, m.litSegments(.62f, 24)) // 14.88: the 15th is not reached yet
        assertEquals(24, m.litSegments(1f, 24))
        assertEquals(24, m.litSegments(7f, 24))
        assertEquals(21, m.firstRedSegment(24, .9f))
        assertEquals(24, m.firstRedSegment(24, 1f))
    }

    @Test fun barLabels() {
        val l = m.barLabels(10)
        assertEquals(listOf("0", "2", "4", "6", "8", "10"), l.map { it.second })
        assertEquals(1f, l.last().first, 0f)
        assertEquals(8, m.barLabels(14).size)
    }

    @Test fun speedDial() {
        val s = m.speedScale(140)
        assertEquals(14, s.ticks)
        assertEquals(listOf(0, 20, 40, 60, 80, 100, 120, 140), s.labels().map { it.second })
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14), s.labels().map { it.first })
        assertEquals(13, m.speedScale(240).labels().size)
    }

    @Test fun rpmDial() {
        val a = m.rpmScale(10)
        assertEquals(20, a.ticks)
        assertEquals((0..10).toList(), a.labels().map { it.second })
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14), m.rpmScale(14).labels().map { it.second })
        assertTrue(m.rpmScale(14).isMajor(2)); assertFalse(m.rpmScale(14).isMajor(3))
    }

    private val amber = 105.0
    private fun pick(c: Double?, r: Double?, v: Double?, on: Boolean = false) = FocusHero.pick(c, r, v, on, amber)

    @Test fun heroPicks() {
        assertEquals(Hero.SPEED, pick(90.0, 3000.0, 14.0))
        assertEquals(Hero.COOLANT, pick(105.0, 3000.0, 14.0))
        assertEquals(Hero.COOLANT, pick(118.0, 0.0, 12.0)) // hot beats stopped
        assertEquals(Hero.VOLT, pick(90.0, 0.0, 12.4))
        assertEquals(Hero.VOLT, pick(null, null, 12.4))
        assertEquals(Hero.SPEED, pick(90.0, 0.0, null)) // no voltage: nothing to show
        assertEquals(Hero.SPEED, pick(null, null, null))
    }

    @Test fun heroHysteresis() {
        val s = FocusHeroState(amber)
        assertEquals(Hero.SPEED, s.update(104.0, 3000.0, 14.0))
        assertEquals(Hero.COOLANT, s.update(105.0, 3000.0, 14.0))
        assertEquals(Hero.COOLANT, s.update(103.0, 3000.0, 14.0)) // between 102 and 105: stays
        assertEquals(Hero.COOLANT, s.update(102.0, 3000.0, 14.0)) // exit is below 102
        assertEquals(Hero.SPEED, s.update(101.9, 3000.0, 14.0))
        assertEquals(Hero.SPEED, s.update(104.0, 3000.0, 14.0)) // back in the band: does not re-enter
        assertEquals(Hero.COOLANT, s.update(106.0, 3000.0, 14.0))
        assertEquals(Hero.SPEED, s.update(null, 3000.0, 14.0)) // reading lost
    }
}
