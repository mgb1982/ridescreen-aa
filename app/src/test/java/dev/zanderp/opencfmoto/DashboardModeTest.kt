// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModeTest {
    @Test fun obdSpeedWinsOverGps() {
        assertEquals(62.0 to false, DashboardMode.pickSpeed(62.0, 70.0))
    }

    @Test fun gpsSpeedIsUsedAndFlaggedWhenObdIsGone() {
        assertEquals(55.0 to true, DashboardMode.pickSpeed(null, 55.0))
    }

    @Test fun gpsJitterAtStandstillShowsZero() {
        assertEquals(0.0 to true, DashboardMode.pickSpeed(null, 1.4))
    }

    @Test fun noSpeedAtAll() {
        assertNull(DashboardMode.pickSpeed(null, null))
    }

    @Test fun coolantLevels() {
        assertEquals(DashboardMode.Level.NORMAL, DashboardMode.coolantLevel(86.0))
        assertEquals(DashboardMode.Level.WARN, DashboardMode.coolantLevel(105.0))
        assertEquals(DashboardMode.Level.ALERT, DashboardMode.coolantLevel(115.0))
    }

    @Test fun rpmBarSegments() {
        assertEquals(0, DashboardMode.litSegments(null, 40))
        assertEquals(0, DashboardMode.litSegments(0.0, 40))
        assertEquals(19, DashboardMode.litSegments(4_800.0, 40))
        assertEquals(40, DashboardMode.litSegments(12_000.0, 40))   // above the scale: bar is full, not overflowing
    }

    @Test fun themeIdsRoundTrip() {
        for (t in DashboardMode.Theme.entries) assertEquals(t, DashboardMode.Theme.byId(t.id))
        assertEquals(DashboardMode.Theme.AUTO, DashboardMode.Theme.byId("nonsense"))
        assertTrue(DashboardMode.Theme.byId(null) == DashboardMode.Theme.AUTO)
    }
}
