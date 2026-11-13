// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.content.Intent
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Wires the `include_bottom_nav` bar that the activity already contains: highlights [NavTab] `current`
 * and switches tabs without stacking activities (REORDER_TO_FRONT, no animation). A hub closes itself
 * when you leave it, so Back from any tab lands on Home.
 */
object BottomNav {
    private const val SETTINGS_HUB = "dev.zanderp.opencfmoto.SettingsHubActivity"

    fun bind(activity: Activity, current: NavTab) {
        val ids = listOf(
            NavTab.HOME to Triple(R.id.nav_home, R.id.nav_home_icon, R.id.nav_home_label),
            NavTab.TRIPS to Triple(R.id.nav_trips, R.id.nav_trips_icon, R.id.nav_trips_label),
            NavTab.BIKE to Triple(R.id.nav_bike, R.id.nav_bike_icon, R.id.nav_bike_label),
            NavTab.SETTINGS to Triple(R.id.nav_settings, R.id.nav_settings_icon, R.id.nav_settings_label),
        )
        val on = ContextCompat.getColor(activity, R.color.brand_gold)
        val off = ContextCompat.getColor(activity, R.color.text_secondary)
        for ((tab, v) in ids) {
            val box = activity.findViewById<android.view.View?>(v.first) ?: continue
            val color = if (tab == current) on else off
            activity.findViewById<ImageView?>(v.second)?.setColorFilter(color)
            activity.findViewById<TextView?>(v.third)?.setTextColor(color)
            box.isSelected = tab == current
            box.setOnClickListener { if (tab != current) open(activity, current, tab) }
        }
    }

    @Suppress("DEPRECATION")
    private fun open(activity: Activity, current: NavTab, target: NavTab) {
        val intent = when (target) {
            NavTab.HOME -> Intent(activity, MainActivity::class.java)
            NavTab.TRIPS -> Intent(activity, TripsHubActivity::class.java)
            NavTab.BIKE -> Intent(activity, BikeHubActivity::class.java)
            // SettingsHubActivity lives in another branch: by name, and SetupActivity until it exists.
            NavTab.SETTINGS -> Intent().setClassName(activity.packageName, SETTINGS_HUB).let { i ->
                if (i.resolveActivity(activity.packageManager) != null) i else Intent(activity, SetupActivity::class.java)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        activity.startActivity(intent)
        if (HubEntries.finishCurrentOnSwitch(current, target)) activity.finish()
        activity.overridePendingTransition(0, 0)
    }
}
