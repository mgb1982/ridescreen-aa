package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.aa.FpsLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FpsLogTest {
    @Test fun firstValueLogs() = assertTrue(FpsLog.shouldLog(-100, 30, 0))
    @Test fun smallWobbleIsQuiet() = assertFalse(FpsLog.shouldLog(30, 28, 1_000))
    @Test fun bigChangeLogs() = assertTrue(FpsLog.shouldLog(30, 20, 1_000))
    @Test fun thirtySecondsLogs() = assertTrue(FpsLog.shouldLog(30, 30, FpsLog.EVERY_MS))
}
