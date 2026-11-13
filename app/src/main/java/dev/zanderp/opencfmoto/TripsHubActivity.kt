// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.widget.Toast

/** "Trips" tab: everything about rides. Only changes where each screen is opened from, not what it does. */
class TripsHubActivity : HubActivity() {
    override val tab = NavTab.TRIPS
    override val titleRes = R.string.tab_trips

    override fun slots() = HubEntries.trips(LiveShare.available)

    override fun describe(item: HubItem): HubRow = when (item) {
        HubItem.TRIPS_LIST -> HubRow(R.drawable.ic_line_trips, 0xFF5B8DEF.toInt(), getString(R.string.home_tile_trips), getString(R.string.hub_sub_trips_list))
        HubItem.TRIP_NOW -> HubRow(R.drawable.ic_line_watch, 0xFF5FD07A.toInt(), getString(R.string.home_big_trip), getString(R.string.home_big_trip_sub))
        HubItem.MAP -> HubRow(R.drawable.ic_line_dash, 0xFF4FB6A6.toInt(), getString(R.string.hub_map), getString(R.string.hub_sub_map))
        HubItem.TRAFFIC_LIGHTS -> HubRow(R.drawable.ic_line_traffic, 0xFFE05A5A.toInt(), getString(R.string.home_tile_tl))
        HubItem.YEAR -> HubRow(R.drawable.ic_line_check, 0xFFD1A955.toInt(), getString(R.string.hub_year), getString(R.string.hub_sub_year))
        HubItem.TROPHIES -> HubRow(R.drawable.ic_line_check, 0xFFD1A955.toInt(), getString(R.string.tr_screen_title))
        HubItem.LIVE -> HubRow(
            R.drawable.ic_line_live, 0xFF5FD07A.toInt(), getString(R.string.home_tile_live),
            getString(if (LiveShare.active) R.string.home_tile_live_on else R.string.live_title),
        )
        HubItem.PASSENGER -> HubRow(R.drawable.ic_line_pad, 0xFFA98BE0.toInt(), clean(R.string.pax_entry))
        HubItem.DASHCAM -> HubRow(R.drawable.ic_line_camera, 0xFFD1A955.toInt(), clean(R.string.dashcam_entry))
        HubItem.HEART_RATE -> HubRow(R.drawable.ic_line_watch, 0xFFE05A5A.toInt(), clean(R.string.help_heart_rate))
        else -> HubRow(R.drawable.ic_line_check, 0xFF5B8DEF.toInt(), item.name)
    }

    override fun onItem(item: HubItem) {
        when (item) {
            HubItem.TRIPS_LIST -> TripsListActivity.start(this)
            HubItem.TRIP_NOW -> TripActivity.start(this)
            HubItem.MAP -> {
                // Same as MainActivity.onMapPressed (private there): drop a stale NAV_TO unless projecting.
                if (ConnectionState.phase != Phase.STREAMING && ConnectionState.phase != Phase.MIRRORING) {
                    GpxSession.abandonStaleNavigation("map hub")
                }
                GpxActivity.start(this)
            }
            HubItem.TRAFFIC_LIGHTS -> TrafficLightsActivity.start(this)
            HubItem.YEAR -> YearInReviewActivity.start(this)
            HubItem.TROPHIES -> TrophiesActivity.start(this)
            HubItem.LIVE -> {
                // Same as MainActivity.renderLive: tap starts, or stops while sharing.
                if (LiveShare.active) {
                    LiveShare.stop()
                    Toast.makeText(this, R.string.live_stopped, Toast.LENGTH_SHORT).show()
                    render()
                } else LiveShare.begin(this)
            }
            HubItem.PASSENGER -> CompanionActivity.start(this)
            HubItem.DASHCAM -> {
                val stop = HubHooks.stopEverything
                val projecting = ConnectionState.phase.let { it == Phase.STREAMING || it == Phase.MIRRORING || it.busy }
                if (stop == null && projecting) {
                    // Hook not wired by Home yet: never import over a live projection.
                    Toast.makeText(this, R.string.dashcam_stop_first_msg, Toast.LENGTH_LONG).show()
                } else DashcamImport.showForTrips(this) { stop?.invoke() }
            }
            HubItem.HEART_RATE -> HeartRateStore.show(this)
            else -> Unit
        }
    }

    override fun onItemLong(item: HubItem): Boolean {
        if (item == HubItem.LIVE && LiveShare.active) { LiveShare.shareLink(this); return true }
        return false
    }
}
