// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerTraceLogicTest {
    @Test fun keepsOnlyTheTriggerAndWaitLines() {
        assertTrue(TriggerTraceLogic.wants("[BT-TRIG] ZT… connected · not the trigger"))
        assertTrue(TriggerTraceLogic.wants("[WATCH] waiting for 'ZT…' in low-power mode (LONG)"))
        assertTrue(TriggerTraceLogic.wants("→ BT trigger: **:**:**:**:10:66 connected and the bike's Wi-Fi is up — starting Connect"))
        assertTrue(TriggerTraceLogic.wants("[LOCK-HOLD] wifi_seen ignored"))
        assertFalse(TriggerTraceLogic.wants("[AA] decode fps=30"))
        assertFalse(TriggerTraceLogic.wants("[PKE] battery 12,8 V · locked"))
        assertFalse(TriggerTraceLogic.wants("hb#12 probed=true"))
    }

    @Test fun encodeAndDecodeRoundTrip() {
        val e = TriggerTraceLogic.encode(1_794_000_000_000L, "[WATCH] stopped (time limit)")
        assertEquals(1_794_000_000_000L to "[WATCH] stopped (time limit)", TriggerTraceLogic.decode(e))
    }

    @Test fun aNewlineInTheTextCannotSplitAnEntry() =
        assertEquals("[BT-TRIG] a b", TriggerTraceLogic.decode(TriggerTraceLogic.encode(1L, "[BT-TRIG] a\nb"))!!.second)

    @Test fun junkLinesAreIgnored() {
        assertNull(TriggerTraceLogic.decode("no tab here"))
        assertNull(TriggerTraceLogic.decode("abc\ttext"))
        assertNull(TriggerTraceLogic.decode("\ttext"))
    }

    @Test fun trimKeepsTheNewest() {
        val l = (1..100).map { TriggerTraceLogic.encode(it.toLong(), "[WATCH] $it") }
        val t = TriggerTraceLogic.trim(l, 60)
        assertEquals(60, t.size)
        assertEquals(41L, TriggerTraceLogic.decode(t.first())!!.first)
        assertEquals(100L, TriggerTraceLogic.decode(t.last())!!.first)
    }

    @Test fun replayOnlyTakesWhatIsNewer() {
        val l = listOf(5L, 10L, 15L).map { TriggerTraceLogic.encode(it, "[WATCH] $it") } + "garbage"
        assertEquals(listOf(10L, 15L), TriggerTraceLogic.newer(l, 5L).map { it.first })
        assertTrue(TriggerTraceLogic.newer(l, 15L).isEmpty())
    }
}
