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
}
