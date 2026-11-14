// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashNightTest {
    @Test fun readsTheOfficialPayload() {
        assertEquals(true, DashNight.parse("{\"night\":true}"))
        assertEquals(false, DashNight.parse("{\"night\": false}"))
        assertEquals(true, DashNight.parse("{\"night\":1}"))
    }

    @Test fun unreadablePayloadIsNull() {
        assertNull(DashNight.parse(""))
        assertNull(DashNight.parse("{\"day\":true}"))
    }

    @Test fun noteLogsAndRemembers() {
        DashNight.lastNight = null
        val lines = mutableListOf<String>()
        DashNight.note("{\"night\":true}", 1234L) { lines += it }
        assertEquals(listOf("[NIGHT] dash says NIGHT"), lines)
        assertEquals(true, DashNight.lastNight)
        assertEquals(1234L, DashNight.lastAtMs)
    }

    @Test fun aRepeatIsNotLoggedAgain() {
        DashNight.lastNight = null
        val lines = mutableListOf<String>()
        DashNight.note("{\"night\":false}", 1L) { lines += it }
        DashNight.note("{\"night\":false}", 2L) { lines += it }
        DashNight.note("{\"night\":true}", 3L) { lines += it }
        assertEquals(listOf("[NIGHT] dash says DAY", "[NIGHT] dash says NIGHT"), lines)
        assertEquals(3L, DashNight.lastAtMs)
    }

    @Test fun recentAnswerWinsOldAnswerFallsBack() {
        assertEquals(true, DashNight.effective(true, atMs = 1_000L, nowMs = 1_000L + DashNight.VALID_MS))
        assertNull(DashNight.effective(true, atMs = 1_000L, nowMs = 1_001L + DashNight.VALID_MS))
        assertNull(DashNight.effective(null, atMs = 0L, nowMs = 5L))
        assertNull(DashNight.effective(false, atMs = 10_000L, nowMs = 5_000L))   // clock went backwards: not trusted
    }

    @Test fun resetForgetsThePreviousConnection() {
        DashNight.note("{\"night\":true}", 1L) {}
        assertEquals(true, DashNight.lastNight)
        DashNight.reset()
        assertNull(DashNight.lastNight)
        assertNull(DashNight.current(2L))
    }

    @Test fun changeCallbackFiresOnEveryAnswer() {
        var calls = 0
        DashNight.reset()
        DashNight.onChange = { calls++ }
        DashNight.note("{\"night\":false}", 1L) {}
        DashNight.note("{\"night\":true}", 2L) {}
        DashNight.onChange = null
        assertEquals(2, calls)
    }

    @Test fun traceKeepsNightLines() {
        assertTrue(TriggerTraceLogic.wants("[NIGHT] dash says DAY"))
    }
}
