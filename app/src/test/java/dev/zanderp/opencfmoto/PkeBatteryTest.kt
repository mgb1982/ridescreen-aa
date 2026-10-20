package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkeBatteryTest {
    @Test fun cleanCodeKeepsDigits() {
        assertEquals("050223176394404", PkeBattery.cleanCode(" 0502 2317-6394404 "))
        assertEquals("", PkeBattery.cleanCode(null))
    }

    @Test fun onlyBuildsTheTwoReadQueries() {
        assertEquals("*BT,0502,10,008#", String(PkeBattery.frame("0502", PkeBattery.Query.HANDSHAKE)))
        assertEquals("*BT,0502,10,001,7#", String(PkeBattery.frame("0502", PkeBattery.Query.STATUS)))
        // The query set is exactly these two; no lock/unlock/etc. exists.
        assertEquals(setOf("008", "001,7"), PkeBattery.Query.values().map { it.cmd }.toSet())
    }

    @Test fun parsesVoltage() {
        assertEquals(12.9f, PkeBattery.voltsFrom("*AT,0502,10,008,A1B2C3D4,129#")!!, 0.01f)
        assertEquals(13.4f, PkeBattery.voltsFrom("*AT,x,y,008,CHAL,134")!!, 0.01f)
        assertNull(PkeBattery.voltsFrom("*AT,x,y,008,CHAL,999#")) // out of range
        assertNull(PkeBattery.voltsFrom("*BT,0502,10,008#"))      // our own request, not a reply
        assertNull(PkeBattery.voltsFrom("garbage"))
    }

    @Test fun parsesLockState() {
        assertEquals(true, PkeBattery.lockedFrom("*AT,x,y,001,7,1#"))
        assertEquals(false, PkeBattery.lockedFrom("*AT,x,y,001,7,2#"))
        assertNull(PkeBattery.lockedFrom("*AT,x,y,001,1,1#")) // action 1 (lock command echo), not status 7
        assertNull(PkeBattery.lockedFrom("*AT,x,y,008,c,129#"))
    }

    @Test fun matchesBoxByCodeOrName() {
        val pke = "050223176394404"
        val adv = ("prefix" + pke + "tail").toByteArray()
        assertTrue(PkeBattery.matches(adv, null, pke))
        assertTrue(PkeBattery.matches(null, "PKE-4404", pke))
        assertFalse(PkeBattery.matches(null, "SomeEarbuds", pke))
        assertFalse(PkeBattery.matches(null, null, "0502")) // too short to match on
    }

    @Test fun masksCodeAndChallenge() {
        val m = PkeBattery.masked("*AT,050223176394404,10,008,SECRETCHAL,129#", "050223176394404")
        assertFalse(m.contains("050223176394404"))
        assertFalse(m.contains("SECRETCHAL"))
        assertTrue(m.contains("4404"))
        assertTrue(m.contains("129"))
    }
}
