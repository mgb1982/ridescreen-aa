// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdBackoffTest {
    @Test fun startsShortAfterAFirstFailure() = assertEquals(5_000L, ObdBackoff.waitAfter(1))
    @Test fun noFailureUsesTheShortestWait() = assertEquals(5_000L, ObdBackoff.waitAfter(0))
    @Test fun growsAndNeverShrinks() {
        var prev = 0L
        for (n in 1..20) { val w = ObdBackoff.waitAfter(n); assertTrue("n=$n", w >= prev); prev = w }
    }
    @Test fun capsAtFiveMinutes() = assertEquals(300_000L, ObdBackoff.waitAfter(50))
    @Test fun aParkedBikeIsTriedFarLessOften() {
        // ~13 s per failed try. 17 min with the bike off: count tries.
        var t = 0L; var n = 0
        while (t < 17 * 60_000L) { n++; t += 13_000L + ObdBackoff.waitAfter(n) }
        assertTrue("tries=$n", n <= 10)   // was ~25 with the 30 s cap
    }
    @Test fun whileProjectingTheOld30sCapStays() {
        assertEquals(30_000L, ObdBackoff.waitAfter(50, projecting = true))
        assertEquals(10_000L, ObdBackoff.waitAfter(2, projecting = true))
    }
    @Test fun duringGraceAtMostOneTryPerMinute() {
        for (n in 0..20) assertTrue("n=$n", ObdBackoff.waitAfter(n, projecting = true, linkLost = true) >= 60_000L)
        assertEquals(60_000L, ObdBackoff.waitAfter(1, projecting = true, linkLost = true))
        assertEquals(300_000L, ObdBackoff.waitAfter(50, projecting = true, linkLost = true))
    }
    @Test fun graceDoesNotChangeTheDefaults() {
        assertEquals(30_000L, ObdBackoff.waitAfter(50, projecting = true, linkLost = false))
        assertEquals(300_000L, ObdBackoff.waitAfter(50))
    }
    @Test fun aLongWaitIsCutShortOnlyWhenProjectingWithTheLinkUp() {
        assertTrue(ObdBackoff.cutShort(30_000L, projecting = true, linkLost = false))
        assertTrue(!ObdBackoff.cutShort(29_999L, projecting = true, linkLost = false))
        assertTrue(!ObdBackoff.cutShort(120_000L, projecting = true, linkLost = true))
        assertTrue(!ObdBackoff.cutShort(120_000L, projecting = false, linkLost = false))
    }
}
