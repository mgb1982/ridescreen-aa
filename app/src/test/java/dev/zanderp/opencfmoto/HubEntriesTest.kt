// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubEntriesTest {
    @Test fun tabsAreInTheAgreedOrder() {
        assertEquals(listOf(NavTab.HOME, NavTab.TRIPS, NavTab.BIKE, NavTab.SETTINGS), NavTab.values().toList())
    }

    @Test fun tripsHubHasTenEntriesWithoutDuplicates() {
        val items = HubEntries.trips(true).map { it.item }
        assertEquals(10, items.size)
        assertEquals(items.size, items.toSet().size)
        assertEquals(HubItem.TRIPS_LIST, items.first())
        assertEquals(HubItem.HEART_RATE, items.last())
    }

    @Test fun liveShareIsDimmedOnlyWithoutServer() {
        assertTrue(HubEntries.trips(true).single { it.item == HubItem.LIVE }.enabled)
        assertFalse(HubEntries.trips(false).single { it.item == HubItem.LIVE }.enabled)
        assertTrue(HubEntries.trips(false).filter { it.item != HubItem.LIVE }.all { it.enabled })
    }

    @Test fun bikeHubHasEightEntriesAndKeyRowsStayTogether() {
        val items = HubEntries.bike().map { it.item }
        assertEquals(8, items.size)
        assertEquals(items.size, items.toSet().size)
        assertEquals(items.indexOf(HubItem.PKE_BATTERY) + 1, items.indexOf(HubItem.PKE_LOCK))
        assertTrue(HubEntries.bike().all { it.enabled })
    }

    @Test fun hubsDoNotShareEntries() {
        val a = HubEntries.trips(true).map { it.item }.toSet()
        assertTrue(a.intersect(HubEntries.bike().map { it.item }.toSet()).isEmpty())
    }

    @Test fun hubsCloseThemselvesOnSwitchButHomeStays() {
        assertFalse(HubEntries.finishCurrentOnSwitch(NavTab.HOME, NavTab.TRIPS))
        assertTrue(HubEntries.finishCurrentOnSwitch(NavTab.TRIPS, NavTab.BIKE))
        assertTrue(HubEntries.finishCurrentOnSwitch(NavTab.BIKE, NavTab.HOME))
        assertFalse(HubEntries.finishCurrentOnSwitch(NavTab.BIKE, NavTab.BIKE))
    }

    @Test fun cleanTitleDropsLeadingEmojiOnly() {
        assertEquals("Battery (keyless box)", HubEntries.cleanTitle("🔋 Battery (keyless box)"))
        assertEquals("Heart rate (watch)", HubEntries.cleanTitle("❤️ Heart rate (watch)"))
        assertEquals("OBD reader (test)", HubEntries.cleanTitle("🔌 OBD reader (test)"))
        assertEquals("Passenger mode", HubEntries.cleanTitle("Passenger mode"))
        assertEquals("0–100 timer", HubEntries.cleanTitle("0–100 timer"))
        assertEquals("", HubEntries.cleanTitle(""))
    }
}
