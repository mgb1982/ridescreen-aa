// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PkeReplyTest {
    private val pke = "000000000000046" // made-up code

    @Test fun lockAnsweredZeroIsRejected() {
        assertTrue(PkeReply.rejected("*AT,$pke,10,001,1,0#", "1"))
    }

    @Test fun lockAnsweredOneIsAccepted() {
        assertFalse(PkeReply.rejected("*AT,$pke,10,001,1,1#", "1"))
    }

    @Test fun otherActionOrStateReadIsNotARejection() {
        assertFalse(PkeReply.rejected("*AT,$pke,10,001,2,0#", "1"))
        assertFalse(PkeReply.rejected("*AT,$pke,10,001,7,2#", "1"))
        assertFalse(PkeReply.rejected("*AT,$pke,10,008,abcd,117,1#", "1"))
    }

    @Test fun missingOrGarbledReplyIsNotARejection() {
        assertFalse(PkeReply.rejected(null, "1"))
        assertFalse(PkeReply.rejected("", "1"))
        assertFalse(PkeReply.rejected("*AT,$pke,10,001,1#", "1"))
        assertFalse(PkeReply.rejected("*BT,$pke,10,001,1,0#", "1"))
    }

    @Test fun readBackWinsOverTheAnswer() {
        // rejected lock on a bike that was already locked: not a problem
        assertFalse(PkeReply.mismatch(want = true, lockedAfter = true, rejected = true))
        // Viti 20261113: rejected and read back unlocked
        assertTrue(PkeReply.mismatch(want = true, lockedAfter = false, rejected = true))
    }

    @Test fun rejectedWithoutReadBackCounts() {
        assertTrue(PkeReply.mismatch(want = true, lockedAfter = null, rejected = true))
        assertFalse(PkeReply.mismatch(want = true, lockedAfter = null, rejected = false))
        assertFalse(PkeReply.mismatch(want = null, lockedAfter = null, rejected = true))
    }

    @Test fun stateShownToTheRider() {
        assertFalse(PkeReply.leftLocked(want = true, lockedAfter = null))
        assertTrue(PkeReply.leftLocked(want = false, lockedAfter = null))
        assertTrue(PkeReply.leftLocked(want = true, lockedAfter = true))
    }
}
