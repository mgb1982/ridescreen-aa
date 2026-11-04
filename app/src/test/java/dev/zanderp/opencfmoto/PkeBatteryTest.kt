package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkeBatteryTest {
    @Test fun cleanCodeKeepsDigits() {
        assertEquals("123456789012345", PkeBattery.cleanCode(" 1234 5678-9012345 "))
        assertEquals("", PkeBattery.cleanCode(null))
    }

    @Test fun onlyBuildsTheTwoReadQueries() {
        assertEquals("*BT,1234,10,008#", String(PkeBattery.frame("1234", PkeBattery.Query.HANDSHAKE)))
        assertEquals("*BT,1234,10,001,7#", String(PkeBattery.frame("1234", PkeBattery.Query.STATUS)))
        // The query set is exactly these two; no lock/unlock/etc. exists.
        assertEquals(setOf("008", "001,7"), PkeBattery.Query.values().map { it.cmd }.toSet())
    }

    @Test fun parsesVoltage() {
        assertEquals(12.9f, PkeBattery.voltsFrom("*AT,1234,10,008,A1B2C3D4,129#")!!, 0.01f)
        assertEquals(13.4f, PkeBattery.voltsFrom("*AT,x,y,008,CHAL,134")!!, 0.01f)
        assertNull(PkeBattery.voltsFrom("*AT,x,y,008,CHAL,999#")) // out of range
        assertNull(PkeBattery.voltsFrom("*BT,1234,10,008#"))      // our own request, not a reply
        assertNull(PkeBattery.voltsFrom("garbage"))
    }

    @Test fun parsesLockState() {
        assertEquals(true, PkeBattery.lockedFrom("*AT,x,y,001,7,1#"))
        assertEquals(false, PkeBattery.lockedFrom("*AT,x,y,001,7,2#"))
        assertNull(PkeBattery.lockedFrom("*AT,x,y,001,1,1#")) // action 1 (lock command echo), not status 7
        assertNull(PkeBattery.lockedFrom("*AT,x,y,008,c,129#"))
    }

    @Test fun matchesBoxByCodeOrName() {
        val pke = "123456789012345"
        // The box advertises the code as hex digits inside its scan record (PkeBattery.matches compares hex).
        val adv = hexBytes("aa" + pke + "0")
        assertTrue(PkeBattery.matches(adv, null, pke))
        assertTrue(PkeBattery.matches(null, "PKE-2345", pke))
        assertFalse(PkeBattery.matches(null, "SomeEarbuds", pke))
        assertFalse(PkeBattery.matches(null, null, "1234")) // too short to match on
    }

    @Test fun masksCodeAndChallenge() {
        val m = PkeBattery.masked("*AT,123456789012345,10,008,SECRETCHAL,129#", "123456789012345")
        assertFalse(m.contains("123456789012345"))
        assertFalse(m.contains("SECRETCHAL"))
        assertTrue(m.contains("2345"))
        assertTrue(m.contains("129"))
    }

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
