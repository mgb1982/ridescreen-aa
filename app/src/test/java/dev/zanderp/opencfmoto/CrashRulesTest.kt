// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRulesTest {
    @Test fun ridingOnAfterABumpEndsTheWatch() {
        // Times are elapsedRealtime, never 0 (0 means "not moving").
        var since = 0L
        for (t in 1_000L..7_000L step 1_000L) since = CrashRules.movingSince(since, 30f, 15f, t)
        assertEquals(1_000L, since)
        assertFalse(CrashRules.rodeOn(since, 5_000L, 5_000L))
        assertTrue(CrashRules.rodeOn(since, 6_000L, 5_000L))
    }

    @Test fun slowingDownRestartsTheCount() {
        var since = CrashRules.movingSince(0L, 30f, 15f, 1_000L)
        since = CrashRules.movingSince(since, 30f, 15f, 3_000L)
        since = CrashRules.movingSince(since, 4f, 15f, 4_000L)   // stopped: maybe the crash
        assertEquals(0L, since)
        since = CrashRules.movingSince(since, 30f, 15f, 5_000L)
        assertFalse(CrashRules.rodeOn(since, 9_000L, 5_000L))
        assertTrue(CrashRules.rodeOn(since, 10_000L, 5_000L))
    }

    @Test fun noSpeedNeverDismisses() {
        var since = 0L
        for (t in 0L..20_000L step 1_000L) since = CrashRules.movingSince(since, null, 15f, t)
        assertFalse(CrashRules.rodeOn(since, 20_000L, 5_000L))
    }
}
