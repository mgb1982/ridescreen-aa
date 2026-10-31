// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdDecodeTest {
    @Test
    fun rpmFromAPlainReply() {
        assertEquals(1780.0, ObdDecode.value("41 0C 1B D0", 0x0C)!!, 0.01)
        assertEquals(1780.0, ObdDecode.value("SEARCHING...\n410C1BD0", 0x0C)!!, 0.01)
    }

    @Test
    fun coolantSpeedAndThrottle() {
        assertEquals(85.0, ObdDecode.value("41 05 7D", 0x05)!!, 0.01)
        assertEquals(47.0, ObdDecode.value("41 0D 2F", 0x0D)!!, 0.01)
        assertEquals(100.0, ObdDecode.value("41 11 FF", 0x11)!!, 0.01)
    }

    @Test
    fun adapterErrorsGiveNothing() {
        assertNull(ObdDecode.value("NO DATA", 0x0C))
        assertNull(ObdDecode.value("UNABLE TO CONNECT", 0x0C))
        assertNull(ObdDecode.value("?", 0x0C))
    }

    @Test
    fun supportedPidsBitmask() {
        // BE 1F A8 13: the classic example from SAE J1979.
        val s = ObdDecode.supported("41 00 BE 1F A8 13", 0x00)
        assertEquals(listOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20), s)
    }

    @Test
    fun troubleCodesCanAndLegacy() {
        assertEquals(listOf("P0133"), ObdDecode.troubleCodes("43 01 01 33"))
        assertEquals(listOf("P0133", "C0123"), ObdDecode.troubleCodes("43 01 33 41 23 00 00"))
        assertTrue(ObdDecode.troubleCodes("43 00").isEmpty())
    }

    @Test
    fun milAndCount() {
        assertEquals(Pair(true, 2), ObdDecode.milAndCount("41 01 82 07 E5 00"))
    }

    @Test
    fun multiPidReplyInOrder() {
        val m = ObdDecode.multi("41 0C 1C 9E 0D 00 05 58 11 1A", listOf(0x0C, 0x0D, 0x05, 0x11))!!
        assertEquals(1831.5, m[0x0C]!!, 0.01)
        assertEquals(0.0, m[0x0D]!!, 0.01)
        assertEquals(48.0, m[0x05]!!, 0.01)
        assertEquals(10.2, m[0x11]!!, 0.05)
    }

    @Test
    fun multiPidFramedCanReplyIgnoresTheLengthLine() {
        val reply = "00A\n0: 41 0C 1C 9E 0D 00\n1: 05 58 11 1A"
        val m = ObdDecode.multi(reply, listOf(0x0C, 0x0D, 0x05, 0x11))!!
        assertEquals(48.0, m[0x05]!!, 0.01)
    }

    @Test
    fun multiPidNeedsEveryPid() {
        assertNull(ObdDecode.multi("41 0C 1C 9E", listOf(0x0C, 0x0D)))
        assertNull(ObdDecode.multi("NO DATA", listOf(0x0C)))
        assertNull(ObdDecode.multi("41 0D 00 0C 1C 9E", listOf(0x0C, 0x0D)))
    }

    @Test
    fun onlyReadOnlyCommandsAreAllowed() {
        for (ok in listOf("ATZ", "ATSP0", "0100", "010C", "010C1", "010C0D0511", "03"))
            assertTrue(ok, ObdProbe.SAFE.matches(ok))
        for (bad in listOf("04", "0900", "0A", "2101", "010C0D05110A1F2E", "3E00", "ATZ;04"))
            assertTrue(bad, !ObdProbe.SAFE.matches(bad))
    }
}
