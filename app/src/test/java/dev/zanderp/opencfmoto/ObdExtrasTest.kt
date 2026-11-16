package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdExtrasDecodeTest {
    private fun v(pid: Int, vararg b: Int) = ObdDecode.value("41 %02X ".format(pid) + b.joinToString(" ") { "%02X".format(it) }, pid)!!

    @Test fun fuelTrims() { assertEquals(0.0, v(0x06, 128), 1e-9); assertEquals(-100.0, v(0x07, 0), 1e-9); assertEquals(99.2, v(0x06, 255), 0.05) }
    @Test fun mapKpa() = assertEquals(35.0, v(0x0B, 35), 0.0)
    @Test fun timing() = assertEquals(12.0, v(0x0E, 152), 1e-9)
    @Test fun absThrottle() = assertEquals(100.0, v(0x11, 255), 1e-9)
    @Test fun o2VoltAndTrim() {
        assertEquals(0.45, v(0x14, 90, 128), 1e-9)
        assertEquals(0.0, ObdDecode.o2Trim(intArrayOf(90, 128))!!, 1e-9)
        assertEquals(50.0, ObdDecode.o2Trim(intArrayOf(90, 192))!!, 1e-9)
        assertNull(ObdDecode.o2Trim(intArrayOf(90, 0xFF)))
    }
    @Test fun runTime() = assertEquals(300.0, v(0x1F, 1, 44), 0.0)
    @Test fun counters() {
        assertEquals(1234.0, v(0x21, 4, 210), 0.0)
        assertEquals(65535.0, v(0x31, 255, 255), 0.0)
        assertEquals(7.0, v(0x30, 7), 0.0)
        assertEquals(258.0, v(0x4D, 1, 2), 0.0)
    }
    @Test fun purge() = assertEquals(50.2, v(0x2E, 128), 0.05)
    @Test fun shortReplyIsNull() = assertNull(ObdDecode.value("41 31 04", 0x31))

    @Test fun monitorStatus() {
        assertEquals("MIL off, 0 DTC, monitors ready", ObdDecode.monitorText(intArrayOf(0, 0x07, 0, 0)))
        assertEquals("MIL on, 2 DTC, monitors ready", ObdDecode.monitorText(intArrayOf(0x82, 0x07, 0, 0)))
        // misfire not complete (bit 4), catalyst supported and not complete
        assertEquals("MIL off, 0 DTC, not ready: misfire,catalyst", ObdDecode.monitorText(intArrayOf(0, 0x17, 0x01, 0x01)))
        assertNull(ObdDecode.monitorText(intArrayOf(0, 0)))
    }
    @Test fun fuelSystem() {
        assertEquals("closed loop", ObdDecode.fixedText(0x03, "41 03 02 00"))
        assertEquals("open loop (cold) / closed loop", ObdDecode.fuelSystemText(intArrayOf(1, 2)))
    }
    @Test fun o2Present() {
        assertEquals("B1S1 B1S2", ObdDecode.fixedText(0x13, "41 13 03"))
        assertEquals("none", ObdDecode.o2PresentText(intArrayOf(0)))
        assertEquals("B2S1", ObdDecode.o2PresentText(intArrayOf(0x10)))
    }
    @Test fun obdStandard() {
        assertEquals("EOBD", ObdDecode.fixedText(0x1C, "41 1C 06"))
        assertEquals("standard 99", ObdDecode.obdStandardText(intArrayOf(99)))
    }
    @Test fun unreadableFixed() = assertNull(ObdDecode.fixedText(0x03, "NO DATA"))
}

class ObdExtrasRotationTest {
    private val all = setOf(0x06, 0x07, 0x0B, 0x0E, 0x11, 0x14, 0x15, 0x1F, 0x2E, 0x0C, 0x31)

    @Test fun oneAtATimeInOrderThenWraps() {
        val seq = (0..9).map { ObdExtras.rotatingPid(all, emptySet(), it) }
        assertEquals(listOf(0x06, 0x07, 0x0B, 0x0E, 0x11, 0x14, 0x15, 0x1F, 0x2E, 0x06), seq)
    }
    @Test fun onlySupportedOnes() {
        val s = setOf(0x0B, 0x1F, 0x0C)
        assertEquals(listOf(0x0B, 0x1F, 0x0B), (0..2).map { ObdExtras.rotatingPid(s, emptySet(), it) })
    }
    @Test fun noneSupportedMeansNoExtra() = assertNull(ObdExtras.rotatingPid(setOf(0x0C, 0x0D), emptySet(), 0))
    @Test fun deadPidsAreSkipped() {
        val seq = (0..3).map { ObdExtras.rotatingPid(setOf(0x06, 0x07, 0x0B), setOf(0x07), it) }
        assertEquals(listOf(0x06, 0x0B, 0x06, 0x0B), seq)
        assertNull(ObdExtras.rotatingPid(setOf(0x06), setOf(0x06), 5))
    }
    @Test fun fixedAndCountersFilteredBySupport() {
        val s = setOf(0x01, 0x13, 0x1C, 0x31, 0x4D, 0x0C)
        assertEquals(listOf(0x01, 0x13, 0x1C), ObdExtras.fixedFor(s))
        assertEquals(listOf(0x31, 0x4D), ObdExtras.countersFor(s))
    }
    @Test fun nineDashboardPidsAreNotRotated() {
        for (p in listOf(0x0C, 0x0D, 0x05, 0x42, 0x04, 0x0F, 0x33, 0x43, 0x45)) assertFalse(p in ObdExtras.ROTATING)
    }
    @Test fun threeMissesInARowKillsThePid() {
        val m = ObdExtras.Misses()
        assertFalse(m.record(0x14, false)); assertFalse(m.record(0x14, false))
        assertTrue(m.record(0x14, false))
        assertTrue(0x14 in m.dead)
        assertFalse(m.record(0x14, false))   // already dead: not announced twice
    }
    @Test fun anAnswerResetsTheCount() {
        val m = ObdExtras.Misses()
        m.record(0x14, false); m.record(0x14, false); m.record(0x14, true)
        assertFalse(m.record(0x14, false)); assertFalse(m.record(0x14, false))
        assertTrue(m.dead.isEmpty())
    }
}

class ObdExtrasLogTest {
    @Test fun statsKeepLastMinMax() {
        val s = ObdExtras.Stats()
        s.record(0x0D, 40.0); s.record(0x0D, 80.0); s.record(0x0D, 10.0)
        val r = s.range(0x0D)!!
        assertEquals(10.0, r.last, 0.0); assertEquals(10.0, r.min, 0.0); assertEquals(80.0, r.max, 0.0)
    }
    @Test fun extrasLine() {
        val s = ObdExtras.Stats()
        s.record(0x04, 27.4); s.record(0x43, 30.0); s.record(0x0F, 22.0); s.record(0x33, 101.0)
        s.record(0x0B, 35.0); s.record(0x0E, 12.0)
        assertEquals("load 27.4% · absLoad 30% · intake 22°C · baro 101kPa · MAP 35kPa · timing 12°", ObdExtras.extrasLine(s))
        assertNull(ObdExtras.extrasLine(ObdExtras.Stats()))
    }
    @Test fun rangesLine() {
        val s = ObdExtras.Stats()
        s.record(0x0C, 1200.0); s.record(0x0C, 5400.0); s.record(0x05, 80.0)
        assertEquals("coolant 80°C · rpm 1200–5400 rpm", ObdExtras.rangesLine(s))
        assertNull(ObdExtras.rangesLine(ObdExtras.Stats()))
    }
    @Test fun connectLine() {
        val l = ObdExtras.connectLine(listOf(0x01 to "MIL off, 0 DTC, monitors ready", 0x1C to "EOBD"), listOf(0x31 to 1234.0, 0x30 to 7.0))
        assertEquals("monitor MIL off, 0 DTC, monitors ready · standard EOBD · km since clear 1234 km · warm-ups since clear 7", l)
        assertNull(ObdExtras.connectLine(emptyList(), emptyList()))
    }
    @Test fun rateMeasuresRealReads() {
        val r = ObdExtras.Rate(0L)
        repeat(300) { r.onRound(it % 6 != 0) }   // 250 good reads
        assertFalse(r.due(59_999L)); assertTrue(r.due(60_000L))
        assertEquals("rpm/speed 4.2/s · round 200 ms (dashboard off)", r.take(60_000L, false))
        assertFalse(r.due(60_001L))   // window restarted
        assertNotNull(r.take(61_000L, true))
    }
}
