// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRenewalTest {
    @Test fun expiredLockThatIsStillWantedIsRenewed() {
        assertTrue(LockRenewal.due(wanted = true, held = false, nowMs = 10_000L, lastAttemptMs = 0L))
    }

    @Test fun lockReleasedOnPurposeIsNeverRenewed() {
        assertFalse(LockRenewal.due(wanted = false, held = false, nowMs = 10_000L, lastAttemptMs = 0L))
    }

    @Test fun lockStillHeldNeedsNothing() {
        assertFalse(LockRenewal.due(wanted = true, held = true, nowMs = 10_000L, lastAttemptMs = 0L))
    }

    @Test fun retriesAreSpacedByTheMinimumGap() {
        assertFalse(LockRenewal.due(true, false, nowMs = 40_000L, lastAttemptMs = 20_000L))
        assertTrue(LockRenewal.due(true, false, nowMs = 50_000L, lastAttemptMs = 20_000L))
        assertTrue(LockRenewal.due(true, false, nowMs = 60_000L, lastAttemptMs = 20_000L))
    }
}
