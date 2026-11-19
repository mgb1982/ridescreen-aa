package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.zanderp.opencfmoto.DashboardMode.Design as D

class DashSkinMathTest {
    private val obdAll = setOf("speed", "rpm", "throttle", "coolant", "load", "volt", "intake", "baro")
    private val phone = setOf("clock", "trip", "avg", "battery", "max", "moving", "alt", "heading")

    @Test fun rpmWhenThereIsObd() {
        assertEquals(DashSkinMath.Secondary.RPM, DashSkinMath.secondary(D.LED, noObd = false, rpmVisible = true, plan = null))
    }

    @Test fun withoutObdLedVfdMinimalShowTheSpeed() {
        for (d in listOf(D.LED, D.VFD, D.MINIMAL)) {
            val p = DashFallback.plan(d, obdAll, phone)
            assertEquals(DashSkinMath.Secondary.SPEED, DashSkinMath.secondary(d, true, true, p))
        }
    }

    @Test fun withoutObdAnalogGetsAClockAndCockpitAnAltitudeTape() {
        val a = DashFallback.plan(D.ANALOG, obdAll, phone)
        assertTrue(a.clockDial)
        assertEquals(DashSkinMath.Secondary.CLOCK, DashSkinMath.secondary(D.ANALOG, true, true, a))
        val c = DashFallback.plan(D.COCKPIT, obdAll, phone)
        assertTrue(c.altTape)
        assertFalse("alt" in c.subs.values)   // the tape already shows it
        assertEquals(DashSkinMath.Secondary.ALTITUDE, DashSkinMath.secondary(D.COCKPIT, true, true, c))
        // no altitude fix yet: the tape shows the speed
        val n = DashFallback.plan(D.COCKPIT, obdAll, phone - "alt")
        assertFalse(n.altTape)
        assertEquals(DashSkinMath.Secondary.SPEED, DashSkinMath.secondary(D.COCKPIT, true, true, n))
    }

    @Test fun everySlotOfTheNewFacesGetsAPhoneReading() {
        val p = DashFallback.plan(D.LED, obdAll, phone)
        assertEquals(setOf("throttle", "coolant", "load", "volt"), p.subs.keys)
        val c = DashFallback.plan(D.COCKPIT, obdAll, phone)
        assertEquals(5, c.subs.size)
    }

    @Test fun planKeyUnchangedForTheOldDesigns() {
        assertFalse(DashFallback.plan(D.SPORT, obdAll, phone).key.endsWith(":alt"))
        assertTrue(DashFallback.plan(D.COCKPIT, obdAll, phone).key.endsWith(":alt"))
    }

    @Test fun arcScales() {
        val rpm = DashSkinMath.arcScale(DashSkinMath.Secondary.RPM, 140, 10_000)
        assertEquals(11, rpm.labels.size)
        assertEquals(0.9f, rpm.redFrom, 1e-6f)
        assertEquals(7, DashSkinMath.arcScale(DashSkinMath.Secondary.RPM, 140, 14_000).labels.size)   // every 2,000
        val sp = DashSkinMath.arcScale(DashSkinMath.Secondary.SPEED, 140, 10_000)
        assertEquals(1f, sp.redFrom, 0f)
        assertEquals("140", sp.labels.last().second)
        assertEquals(7, DashSkinMath.arcScale(DashSkinMath.Secondary.SPEED, 240, 10_000).labels.size)   // every 40
    }

    @Test fun speedDigits() {
        assertEquals("097", DashSkinMath.speedDigits(96.6))
        assertEquals("000", DashSkinMath.speedDigits(0.0))
        assertEquals("---", DashSkinMath.speedDigits(null))
        assertEquals("999", DashSkinMath.speedDigits(1200.0))
    }

    @Test fun barsAndZones() {
        assertEquals(40.0 to 130.0, DashSkinMath.barRange("coolant", 130.0))
        assertNull(DashSkinMath.barRange("clock", 0.0))           // text only
        assertEquals(0.0 to 100.0, DashSkinMath.barRange("battery", 100.0))
        assertEquals(0.5f, DashSkinMath.barFraction(85.0, 40.0 to 130.0), 1e-6f)
        assertEquals(0f, DashSkinMath.barFraction(null, 0.0 to 100.0), 0f)
        assertEquals(DashSkinMath.Zone.WARN, DashSkinMath.zone("coolant", 106.0))
        assertEquals(DashSkinMath.Zone.ALERT, DashSkinMath.zone("coolant", 115.0))
        assertEquals(DashSkinMath.Zone.ALERT, DashSkinMath.zone("volt", 11.8))
        assertEquals(DashSkinMath.Zone.OK, DashSkinMath.zone("volt", 13.8))
        assertEquals(DashSkinMath.Zone.WARN, DashSkinMath.zone("battery", 20.0))
        assertEquals(DashSkinMath.Zone.PLAIN, DashSkinMath.zone("throttle", 99.0))
    }
}
