package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryLogTest {
    @Test fun fpsRounds() = assertEquals(24, BatteryLog.fps(719, 30_000))
    @Test fun fpsBadWindowIsZero() {
        assertEquals(0, BatteryLog.fps(10, 0))
        assertEquals(0, BatteryLog.fps(-3, 30_000))
    }
    @Test fun videoLine() =
        assertEquals("[VIDEO] encoded fps=24 (cap 24fps, mode BALANCED)", BatteryLog.videoLine(24, 24, "BALANCED"))
    @Test fun durations() {
        assertEquals("45s", BatteryLog.duration(45_000))
        assertEquals("20m", BatteryLog.duration(20 * 60_000L))
        assertEquals("1h05m", BatteryLog.duration(65 * 60_000L))
        assertEquals("0s", BatteryLog.duration(-5))
    }
    @Test fun deltas() {
        assertEquals("(-4%)", BatteryLog.delta(87, 83))
        assertEquals("(+1%)", BatteryLog.delta(80, 81))
        assertEquals("(0%)", BatteryLog.delta(80, 80))
        assertEquals("(?)", BatteryLog.delta(-1, 80))
    }
    @Test fun pwrExample() =
        assertEquals("[PWR] projecting 20m level=83% (-4%) charging=no", BatteryLog.pwrLine("projecting", 20 * 60_000L, 87, 83, false))
    @Test fun pwrUnknownLevel() =
        assertEquals("[PWR] ended 5s level=? (?) charging=yes", BatteryLog.pwrLine("ended", 5_000, 87, -1, true))
    @Test fun dueRules() {
        assertTrue(BatteryLog.due(0, 30_000, BatteryLog.VIDEO_EVERY_MS))
        assertFalse(BatteryLog.due(1_000, 20_000, BatteryLog.VIDEO_EVERY_MS))
        assertTrue(BatteryLog.due(50_000, 1_000, BatteryLog.VIDEO_EVERY_MS))
    }
    @Test fun percent() {
        assertEquals(83, BatteryLog.percent(83, 100))
        assertEquals(50, BatteryLog.percent(1500, 3000))
        assertEquals(-1, BatteryLog.percent(-1, 100))
        assertEquals(-1, BatteryLog.percent(50, 0))
    }
}
