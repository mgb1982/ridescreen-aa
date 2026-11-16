// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private typealias A = PkeUnlock.Action

class PkeVoicePolicyTest {

    @Test fun parsesTheFourActions() {
        for (a in A.values()) assertEquals(a, PkeVoicePolicy.parse(a.name))
    }

    @Test fun rejectsUnknownOrMissing() {
        assertNull(PkeVoicePolicy.parse(null))
        assertNull(PkeVoicePolicy.parse(""))
        assertNull(PkeVoicePolicy.parse("lock"))
        assertNull(PkeVoicePolicy.parse("REBOOT"))
    }

    @Test fun lockNeverNeedsAuth() {
        assertFalse(PkeVoicePolicy.needsAuth(A.LOCK, true))
        assertFalse(PkeVoicePolicy.needsAuth(A.LOCK, false))
    }

    @Test fun otherActionsNeedAuthOnlyWhenPhoneLocked() {
        for (a in listOf(A.UNLOCK, A.CUSHION, A.OILBOX)) {
            assertTrue(PkeVoicePolicy.needsAuth(a, true))
            assertFalse(PkeVoicePolicy.needsAuth(a, false))
        }
    }

    @Test fun logLines() {
        assertEquals("[PKE-voice] unlock requested (phone locked=true)", PkeVoicePolicy.requestedLine(A.UNLOCK, true))
        assertEquals("[PKE-voice] cushion cancelled (phone locked, not authenticated)", PkeVoicePolicy.cancelledLine(A.CUSHION))
    }
}
