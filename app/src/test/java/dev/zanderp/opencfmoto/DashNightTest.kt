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

    @Test fun traceKeepsNightLines() {
        assertTrue(TriggerTraceLogic.wants("[NIGHT] dash says DAY"))
    }
}
