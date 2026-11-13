// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat

val SettingsSection.titleRes: Int
    get() = when (this) {
        SettingsSection.CONNECTION -> R.string.settings_sec_connection
        SettingsSection.DISPLAY -> R.string.settings_sec_display
        SettingsSection.DASHBOARD -> R.string.settings_sec_dashboard
        SettingsSection.HANDLEBAR -> R.string.settings_sec_handlebar
        SettingsSection.ROUTE -> R.string.settings_sec_route
        SettingsSection.WATCH -> R.string.settings_sec_watch
        SettingsSection.PRIVACY -> R.string.settings_sec_privacy
        SettingsSection.ADVANCED -> R.string.settings_sec_advanced
    }

val SettingsSection.subtitleRes: Int
    get() = when (this) {
        SettingsSection.CONNECTION -> R.string.settings_sec_connection_sub
        SettingsSection.DISPLAY -> R.string.settings_sec_display_sub
        SettingsSection.DASHBOARD -> R.string.settings_sec_dashboard_sub
        SettingsSection.HANDLEBAR -> R.string.settings_sec_handlebar_sub
        SettingsSection.ROUTE -> R.string.settings_sec_route_sub
        SettingsSection.WATCH -> R.string.settings_sec_watch_sub
        SettingsSection.PRIVACY -> R.string.settings_sec_privacy_sub
        SettingsSection.ADVANCED -> R.string.settings_sec_advanced_sub
    }

/** Emoji used as the section icon (the app already uses emoji instead of icon drawables). */
val SettingsSection.icon: String
    get() = when (this) {
        SettingsSection.CONNECTION -> "🔗"
        SettingsSection.DISPLAY -> "🎯"
        SettingsSection.DASHBOARD -> "📊"
        SettingsSection.HANDLEBAR -> "🎮"
        SettingsSection.ROUTE -> "🛣️"
        SettingsSection.WATCH -> "⌚"
        SettingsSection.PRIVACY -> "🔒"
        SettingsSection.ADVANCED -> "🛠️"
    }

/** Settings hub: one big card per section; each opens Setup or Controls showing only that section. */
class SettingsHubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_hub)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.hub_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(v.paddingLeft, b.top, v.paddingRight, b.bottom)
            insets
        }
        val list = findViewById<LinearLayout>(R.id.hub_list)
        for (section in SettingsSection.entries) {
            val row = LayoutInflater.from(this).inflate(R.layout.row_settings_section, list, false)
            row.findViewById<TextView>(R.id.row_icon).text = section.icon
            row.findViewById<TextView>(R.id.row_title).setText(section.titleRes)
            row.findViewById<TextView>(R.id.row_sub).setText(section.subtitleRes)
            row.setOnClickListener {
                val target = when (section.host) {
                    SettingsHost.SETUP -> SetupActivity::class.java
                    SettingsHost.CONTROLS -> ControlsActivity::class.java
                }
                startActivity(Intent(this, target).putExtra(SettingsSection.EXTRA, section.key))
            }
            list.addView(row)
        }
    }
}
