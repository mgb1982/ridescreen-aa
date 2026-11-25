package dev.zanderp.opencfmoto

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogStoreLogicTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun e(day: String, size: Long = 100) = LogStoreLogic.Entry("log-$day.txt", size)

    @Test fun fileNameUsesLocalDay() {
        val madrid = TimeZone.getTimeZone("Europe/Madrid")
        val t = 1_700_000_000_000L // 2023-11-14 22:13 UTC = 23:13 Madrid
        assertEquals("log-20231114.txt", LogStoreLogic.fileName(t, madrid))
        assertEquals("log-20231115.txt", LogStoreLogic.fileName(t + 2 * 3600_000L, madrid))
        assertEquals("20231114", LogStoreLogic.dayOf("log-20231114.txt"))
        assertNull(LogStoreLogic.dayOf("last_session.log"))
    }

    @Test fun minusDaysCrossesMonth() {
        assertEquals("20240229", LogStoreLogic.minusDays("20240301", 1, utc))
        assertEquals("20231225", LogStoreLogic.minusDays("20240101", 7, utc))
    }

    @Test fun pruneDropsOlderThanSevenDays() {
        val files = listOf(e("20240101"), e("20240102"), e("20240105"), e("20240108"))
        // today 20240108: keeps 0102..0108 (7 days)
        assertEquals(listOf("log-20240101.txt"), LogStoreLogic.toPrune(files, "20240108", tz = utc))
    }

    @Test fun pruneBySizeOldestFirstKeepsNewest() {
        val mb = 1024L * 1024
        val files = listOf(e("20240106", 9 * mb), e("20240107", 9 * mb), e("20240108", 9 * mb))
        assertEquals(listOf("log-20240106.txt"), LogStoreLogic.toPrune(files, "20240108", tz = utc))
        val huge = listOf(e("20240108", 50 * mb))
        assertTrue(LogStoreLogic.toPrune(huge, "20240108", tz = utc).isEmpty())
    }

    @Test fun pruneIgnoresForeignFiles() {
        assertTrue(LogStoreLogic.toPrune(listOf(LogStoreLogic.Entry("x.bin", 1L shl 40)), "20240108", tz = utc).isEmpty())
    }

    @Test fun shareTakesTodayAndPreviousExistingDay() {
        val names = listOf("log-20240101.txt", "log-20240104.txt", "log-20240108.txt", "log-20240109.txt")
        assertEquals(listOf("log-20240104.txt", "log-20240108.txt"), LogStoreLogic.shareFiles(names.take(3), "20240108"))
        assertEquals(listOf("log-20240104.txt"), LogStoreLogic.shareFiles(names.take(2), "20240108"))
        assertEquals(listOf("log-20240108.txt"), LogStoreLogic.shareFiles(listOf("log-20240108.txt"), "20240108"))
    }

    @Test fun skipBytesDropsOldestEnd() {
        assertEquals(listOf(0L, 0L), LogStoreLogic.skipBytes(listOf(10, 20), 100))
        assertEquals(listOf(5L, 0L), LogStoreLogic.skipBytes(listOf(10, 20), 25))
        assertEquals(listOf(10L, 10L), LogStoreLogic.skipBytes(listOf(10, 20), 10))
        assertEquals(listOf(10L, 20L), LogStoreLogic.skipBytes(listOf(10, 20), -5))
    }

    private val banner = "10:00:00.000  [BUILD] versionName=1 versionCode=1 git=x flags=y\n" +
        "10:00:00.001  [BUILD] phone=P android=14 (sdk 34)\n10:00:00.002  [CLOCK-LAB] q\n" +
        "10:00:00.003  [BUILD] --- session start ---\n10:00:00.004  [LOGS] on disk: 1 file(s), 1 KB, oldest 2024-01-08\n"

    @Test fun cutSessionStartedToday() {
        val today = "09:00:00.000  old session\n09:59:00.000  [NAV] guidance stopped\n09:59:59.000  [NAV] x\n" + banner + "10:01:00.000  now\n"
        val out = LogStoreLogic.cutCurrentSession(listOf("y\n", today))
        assertEquals("y\n", out[0])
        assertEquals("09:00:00.000  old session\n", out[1])
    }

    @Test fun cutSessionStartedYesterdayCrossedMidnight() {
        val yesterday = "a\n" + banner + "23:59:00.000  ride\n"
        val out = LogStoreLogic.cutCurrentSession(listOf(yesterday, "00:01:00.000  ride goes on\n"))
        assertEquals(listOf("a\n", ""), out)
    }

    @Test fun cutUsesLastBannerAndKeepsNavNotAdjacent() {
        val t = banner + "[NAV] keep\nmid\n" + banner + "tail\n"
        assertEquals(banner + "[NAV] keep\nmid\n", LogStoreLogic.cutCurrentSession(listOf(t))[0])
    }

    @Test fun cutWithoutBannerKeepsEverything() {
        val l = listOf("a\n", "b\n")
        assertEquals(l, LogStoreLogic.cutCurrentSession(l))
        assertEquals(listOf(""), LogStoreLogic.cutCurrentSession(listOf("")))
    }

    @Test fun startupLine() {
        assertEquals("[LOGS] on disk: 2 file(s), 2 KB, oldest 2024-01-05",
            LogStoreLogic.startupLine(listOf(e("20240106", 1024), e("20240105", 1024))))
        assertEquals("[LOGS] on disk: 0 file(s), 0 KB, oldest -", LogStoreLogic.startupLine(emptyList()))
    }

    @Test fun appStatusDedupe() {
        val d = LogStoreLogic.AppStatusDedupe(10 * 60_000L)
        assertTrue(d.shouldLog("#1 {\"mode\":2}", 0))
        assertFalse(d.shouldLog("#2 {\"mode\":2}", 1000))
        assertFalse(d.shouldLog("#3 {\"mode\":2}", 599_000))
        assertTrue(d.shouldLog("#4 {\"mode\":2}", 600_000))
        assertTrue(d.shouldLog("#5 {\"mode\":1}", 601_000))
    }
}
