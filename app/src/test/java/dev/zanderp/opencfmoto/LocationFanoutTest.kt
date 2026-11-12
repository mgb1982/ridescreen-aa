// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.LocationFanout.Plan
import dev.zanderp.opencfmoto.LocationFanout.Spec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFanoutTest {
    private val gps = "gps"
    private val trip = Spec(gps, 1000L, 0f)
    private val live = Spec(gps, 5000L, 10f)
    private val dash = Spec(gps, 800L, 1.5f)

    @Test fun planEmptyIsNull() = assertNull(LocationFanout.plan(emptyList()))

    @Test fun planZeroDistanceWins() {
        assertEquals(Plan(800L, 0f), LocationFanout.plan(listOf(trip, live, dash)))
    }

    @Test fun planMinimumTimesAndDistances() {
        assertEquals(Plan(800L, 1.5f), LocationFanout.plan(listOf(live, dash)))
        assertEquals(Plan(5000L, 10f), LocationFanout.plan(listOf(live)))
    }

    @Test fun firstFixAlwaysDelivered() {
        assertTrue(LocationFanout.shouldDeliver(live, 800L, 0L, 123L, 0f))
    }

    @Test fun oneSecondWithJitterAlwaysDelivers() {
        assertTrue(LocationFanout.shouldDeliver(trip, 800L, 10_000L, 10_990L, null))
        assertTrue(LocationFanout.shouldDeliver(trip, 800L, 10_000L, 11_010L, 0f))
    }

    @Test fun liveShareFiveSecondsTenMeters() {
        assertTrue(LocationFanout.shouldDeliver(live, 800L, 10_000L, 14_750L, 10f))
        assertFalse(LocationFanout.shouldDeliver(live, 800L, 10_000L, 13_000L, 50f))
        assertFalse(LocationFanout.shouldDeliver(live, 800L, 10_000L, 15_000L, 4f))
        assertFalse(LocationFanout.shouldDeliver(live, 800L, 10_000L, 14_700L, 50f))
        assertTrue(LocationFanout.shouldDeliver(live, 800L, 10_000L, 15_000L, null))
    }

    @Test fun gpxDashEightHundredMsOnePointFiveM() {
        assertFalse(LocationFanout.shouldDeliver(dash, 800L, 10_000L, 11_000L, 1f))
        assertTrue(LocationFanout.shouldDeliver(dash, 800L, 10_000L, 11_000L, 2f))
    }
}
