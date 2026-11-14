// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** "Bike" tab: garage, keyless key, OBD, 0-100 timer, upkeep. Only changes where each screen is opened from. */
class BikeHubActivity : HubActivity() {
    override val tab = NavTab.BIKE
    override val titleRes = R.string.tab_bike

    override fun slots() = HubEntries.bike()

    override fun describe(item: HubItem): HubRow = when (item) {
        HubItem.GARAGE -> HubRow(R.drawable.ic_line_seat, 0xFF5B8DEF.toInt(), getString(R.string.garage_garage), getString(R.string.hub_sub_garage))
        HubItem.PKE_BATTERY -> HubRow(R.drawable.ic_line_power, 0xFF5FD07A.toInt(), clean(R.string.pke_entry), getString(R.string.hub_key_sub))
        HubItem.PKE_LOCK -> HubRow(R.drawable.ic_line_lock, 0xFFD1A955.toInt(), clean(R.string.pke_unlock_entry), getString(R.string.hub_key_sub))
        HubItem.OBD -> HubRow(R.drawable.ic_line_dash, 0xFF4FB6A6.toInt(), clean(R.string.obd_entry), getString(R.string.hub_sub_obd))
        HubItem.CHRONO -> HubRow(R.drawable.ic_line_gauge, 0xFFE5774E.toInt(), getString(R.string.chr_open))
        HubItem.MAINTENANCE -> HubRow(R.drawable.ic_line_maint, 0xFF4FB6A6.toInt(), getString(R.string.home_tile_maint))
        HubItem.FUEL -> HubRow(R.drawable.ic_line_fuel, 0xFFE5774E.toInt(), getString(R.string.home_tile_fuel), getString(R.string.home_tile_fuel_sub))
        HubItem.CRASH -> HubRow(R.drawable.ic_line_alert, 0xFFE05A5A.toInt(), getString(R.string.crash_title), getString(R.string.hub_sub_crash))
        else -> HubRow(R.drawable.ic_line_check, 0xFF5B8DEF.toInt(), item.name)
    }

    override fun onItem(item: HubItem) {
        when (item) {
            HubItem.GARAGE -> GarageActivity.start(this)
            HubItem.PKE_BATTERY -> PkeBatteryUi.show(this)
            HubItem.PKE_LOCK -> PkeUnlockUi.show(this)
            HubItem.OBD -> ObdProbeUi.show(this)
            HubItem.CHRONO -> startActivity(android.content.Intent(this, ChronoActivity::class.java))
            HubItem.MAINTENANCE -> MaintenanceActivity.start(this)
            HubItem.FUEL -> FuelActivity.start(this)
            HubItem.CRASH -> CrashSettingsActivity.start(this)
            else -> Unit
        }
    }
}
