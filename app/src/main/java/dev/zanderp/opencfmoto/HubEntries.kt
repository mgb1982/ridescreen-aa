// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** Bottom bar tabs, in display order. */
enum class NavTab { HOME, TRIPS, BIKE, SETTINGS }

/** Everything the Trips and Bike hubs can list. Pure ids: titles, icons and actions live in the activities. */
enum class HubItem {
    // Trips
    TRIPS_LIST, TRIP_NOW, MAP, TRAFFIC_LIGHTS, YEAR, TROPHIES, LIVE, PASSENGER, DASHCAM, HEART_RATE,
    // Bike
    GARAGE, PKE_BATTERY, PKE_LOCK, OBD, CHRONO, MAINTENANCE, FUEL, CRASH,
}

data class HubSlot(val item: HubItem, val enabled: Boolean = true)

/** Which entries each hub shows, and in which order. No Android types, so it is unit tested. */
object HubEntries {
    /** [liveAvailable]: LiveShare has a server configured (BuildConfig.TELEMETRY_URL). Otherwise the row stays but dimmed. */
    fun trips(liveAvailable: Boolean): List<HubSlot> = listOf(
        HubSlot(HubItem.TRIPS_LIST),
        HubSlot(HubItem.TRIP_NOW),
        HubSlot(HubItem.MAP),
        HubSlot(HubItem.TRAFFIC_LIGHTS),
        HubSlot(HubItem.YEAR),
        HubSlot(HubItem.TROPHIES),
        HubSlot(HubItem.LIVE, enabled = liveAvailable),
        HubSlot(HubItem.PASSENGER),
        HubSlot(HubItem.DASHCAM),
        HubSlot(HubItem.HEART_RATE),
    )

    fun bike(): List<HubSlot> = listOf(
        HubSlot(HubItem.GARAGE),
        HubSlot(HubItem.PKE_BATTERY),
        HubSlot(HubItem.PKE_LOCK),
        HubSlot(HubItem.OBD),
        HubSlot(HubItem.CHRONO),
        HubSlot(HubItem.MAINTENANCE),
        HubSlot(HubItem.FUEL),
        HubSlot(HubItem.CRASH),
    )

    /** Tapping [target] while on [current]: hubs close themselves so tabs never pile up (Home stays underneath). */
    fun finishCurrentOnSwitch(current: NavTab, target: NavTab): Boolean =
        current != NavTab.HOME && current != target

    /** Old Help entries start with an emoji ("🔋 Battery…"); the hub rows already have an icon, so drop it. */
    fun cleanTitle(s: String): String {
        val i = s.indexOfFirst { it.isLetterOrDigit() || it == '(' }
        return if (i <= 0) s.trim() else s.substring(i).trim()
    }
}
