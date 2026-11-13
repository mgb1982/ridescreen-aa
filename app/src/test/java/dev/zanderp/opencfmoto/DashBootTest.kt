// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashBootTest {
    @Test fun bootIsReceivedMinusUptime() =
        assertEquals(1_000_000L - 85_200L, DashBoot.bootEpochMs(1_000_000L, 85_200L))

    @Test fun noPreviousBootIsNew() = assertTrue(DashBoot.isNewBoot(null, 1_000L))

    @Test fun farApartIsNewBoot() = assertTrue(DashBoot.isNewBoot(1_000_000L, 1_060_000L))

    @Test fun sameBootWithJitterOfThreeSecondsIsNotNew() {
        assertFalse(DashBoot.isNewBoot(1_000_000L, 1_003_000L))
        assertFalse(DashBoot.isNewBoot(1_000_000L, 997_000L))
    }

    @Test fun toleranceEdgeIsNotNewButBeyondIs() {
        assertFalse(DashBoot.isNewBoot(1_000_000L, 1_005_000L))
        assertTrue(DashBoot.isNewBoot(1_000_000L, 1_005_001L))
    }

    @Test fun uptimeNowAdvancesWithThePhoneClock() =
        assertEquals(87_700L, DashBoot.uptimeNowMs(85_200L, 2_000_000L, 2_002_500L))

    @Test fun formatsSecondsWithOneDecimal() {
        assertEquals("85.2 s", DashBoot.fmtSeconds(85_200L))
        assertEquals("0.0 s", DashBoot.fmtSeconds(0L))
    }
}
