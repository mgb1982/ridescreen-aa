// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.MaintenanceDue.Kind
import dev.zanderp.opencfmoto.MaintenanceDue.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceDueTest {
    private val day = 86_400_000L
    private val now = 1_000L * day

    @Test fun overdueByKm() {
        val d = MaintenanceDue.pick(listOf(Task("Brake pads", 6000, null, 1000.0, null)), 7100.0, now)!!
        assertEquals(MaintenanceDue.Due("Brake pads", Kind.OVERDUE, 100), d)
    }

    @Test fun overdueByDate() {
        val d = MaintenanceDue.pick(listOf(Task("ITV", null, 12, null, now - 400 * day)), null, now)!!
        assertEquals(Kind.OVERDUE, d.kind)
        assertEquals(true, d.byDays)
        assertTrue(Math.abs(d.amount - 35) <= 1)
    }

    @Test fun soonByKm() {
        val d = MaintenanceDue.pick(listOf(Task("Oil change", 3000, null, 1000.0, null)), 3720.0, now)!!
        assertEquals(MaintenanceDue.Due("Oil change", Kind.KM_LEFT, 280), d)
    }

    @Test fun soonByDays() {
        val last = now - (365 - 10) * day
        val d = MaintenanceDue.pick(listOf(Task("Insurance", null, 12, null, last)), null, now)!!
        assertEquals(Kind.DAYS_LEFT, d.kind)
        assertTrue(Math.abs(d.amount - 10) <= 1)
    }

    @Test fun nothingDue() {
        val tasks = listOf(Task("Oil", 3000, 12, 1000.0, now - 30 * day), Task("Unknown", 6000, null, null, null))
        assertNull(MaintenanceDue.pick(tasks, 1500.0, now))
        assertNull(MaintenanceDue.pick(emptyList(), 1500.0, now))
        assertNull(MaintenanceDue.pick(listOf(Task("Oil", 3000, null, 1000.0, null)), null, now))
    }

    @Test fun overdueBeatsSoonAndMostOverdueWins() {
        val tasks = listOf(
            Task("Soon", 3000, null, 1000.0, null),
            Task("A little late", 3000, null, 0.0, null),
            Task("Very late", 3000, null, -2000.0, null),
        )
        val d = MaintenanceDue.pick(tasks, 3100.0, now)!!
        assertEquals("Very late", d.name)
        assertEquals(Kind.OVERDUE, d.kind)
        assertEquals(2100, d.amount)
    }

    @Test fun tieKeepsOneLine() {
        val tasks = listOf(Task("First", 3000, null, 0.0, null), Task("Second", 3000, null, 0.0, null))
        assertEquals("First", MaintenanceDue.pick(tasks, 3100.0, now)!!.name)
    }
}
