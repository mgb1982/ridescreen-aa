// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Manufacturer-style schedules: first service at X km, then every Y km on the odometer (Zontes 125X manual). */
class MaintenanceScheduleTest {
    private fun task(first: Int?, every: Int?, last: Double?) =
        Maintenance.Task("t", "t", every, null, last, null, firstKm = first)

    private fun next(first: Int?, every: Int?, last: Double?, odo: Double) =
        Maintenance.kmWindow(task(first, every, last), odo)?.second

    @Test fun service_1000_then_every_6000() {
        assertEquals(listOf(1000.0, 6000.0, 12000.0, 18000.0, 24000.0), Maintenance.milestones(1000, 6000, 20000.0).take(5))
        assertEquals(1000.0, next(1000, 6000, null, 800.0))
        assertEquals(6000.0, next(1000, 6000, 1000.0, 1200.0))
        assertEquals(6000.0, next(1000, 6000, 1150.0, 1200.0))   // done a bit late: still due at 6,000
        assertEquals(12000.0, next(1000, 6000, 5800.0, 5900.0))  // done a bit early: covers 6,000
        assertEquals(12000.0, next(1000, 6000, 6400.0, 6500.0))  // done a bit late: covers 6,000
    }

    @Test fun gearbox_oil_6000_then_18000() {
        assertEquals(listOf(6000.0, 18000.0, 30000.0), Maintenance.milestones(6000, 12000, 25000.0).take(3))
        assertEquals(18000.0, next(6000, 12000, 6100.0, 7000.0))
    }

    @Test fun oil_filter_1000_then_12000() {
        assertEquals(12000.0, next(1000, 12000, 1000.0, 2000.0))
        assertEquals(24000.0, next(1000, 12000, 12100.0, 13000.0))
    }

    @Test fun never_logged() {
        assertEquals(1000.0, next(1000, 6000, null, 1400.0))  // overdue by 400: real
        assertNull(next(1000, 6000, null, 9000.0))             // far past: ask instead of "overdue by 8,000"
    }

    @Test fun plain_interval_unchanged() {
        assertEquals(9000.0, next(null, 3000, 6000.0, 7000.0))
        assertNull(next(null, 3000, null, 7000.0))
    }
}
