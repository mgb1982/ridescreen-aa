// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TlPickTest {
    @Test fun emptyListGivesNull() {
        assertNull(TlPick.next(emptyList(), null))
        assertNull(TlPick.next(emptyList(), 3))
    }
    @Test fun singleCandidateIsPicked() {
        assertEquals(4, TlPick.next(listOf(4), null))
        assertEquals(4, TlPick.next(listOf(4), 4))
    }
    @Test fun severalWithoutSelectionPicksFirst() = assertEquals(7, TlPick.next(listOf(7, 2, 9), null))
    @Test fun firstSelectedPicksSecond() = assertEquals(2, TlPick.next(listOf(7, 2, 9), 7))
    @Test fun lastSelectedWrapsToFirst() = assertEquals(7, TlPick.next(listOf(7, 2, 9), 9))
    @Test fun selectedNotInListPicksFirst() = assertEquals(7, TlPick.next(listOf(7, 2, 9), 5))
}
