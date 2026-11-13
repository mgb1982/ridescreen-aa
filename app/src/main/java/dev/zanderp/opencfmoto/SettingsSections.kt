// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** Which activity draws a settings block: the old Setup screen or the old Controls screen. */
enum class SettingsHost { SETUP, CONTROLS }

/**
 * The sections of the Settings hub ([SettingsHubActivity]). Pure data, no Android types, so the
 * mapping "which block belongs to which section" is unit-tested.
 */
enum class SettingsSection(val key: String, val host: SettingsHost) {
    CONNECTION("connection", SettingsHost.SETUP),
    DISPLAY("display", SettingsHost.SETUP),
    DASHBOARD("dashboard", SettingsHost.CONTROLS),
    HANDLEBAR("handlebar", SettingsHost.CONTROLS),
    ROUTE("route", SettingsHost.SETUP),
    WATCH("watch", SettingsHost.SETUP),
    PRIVACY("privacy", SettingsHost.SETUP),
    ADVANCED("advanced", SettingsHost.SETUP),
    ;

    companion object {
        const val EXTRA = "section"
        fun fromKey(key: String?): SettingsSection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Cards of `activity_setup.xml` / `activity_controls.xml`. Each block is one card (ids `card_*`) and
 * belongs to exactly one section. `section == null` means "only in the legacy full screen"
 * (Controls' navigate-to and on-screen pad). Order = order of the cards in the XML.
 */
enum class SettingsBlock(val section: SettingsSection?, val host: SettingsHost) {
    // Setup
    START(SettingsSection.CONNECTION, SettingsHost.SETUP),
    CONNECTION_OPTIONS(SettingsSection.CONNECTION, SettingsHost.SETUP),
    RESUME(SettingsSection.CONNECTION, SettingsHost.SETUP),
    DISPLAY_MAIN(SettingsSection.DISPLAY, SettingsHost.SETUP),
    THEME(SettingsSection.DISPLAY, SettingsHost.SETUP),
    POWER(SettingsSection.DISPLAY, SettingsHost.SETUP),
    ROUTE_EXTRAS(SettingsSection.ROUTE, SettingsHost.SETUP),
    WATCH(SettingsSection.WATCH, SettingsHost.SETUP),
    PRIVACY(SettingsSection.PRIVACY, SettingsHost.SETUP),
    ADV_PROFILE(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_LAYOUT(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_RESOLUTION(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_TOUCH(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_TRANSPORT(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_RECOVERY(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_CLOCKLAB(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_DIAGNOSTICS(SettingsSection.ADVANCED, SettingsHost.SETUP),
    ADV_BIKES(SettingsSection.ADVANCED, SettingsHost.SETUP),

    // Controls
    DASHBOARD(SettingsSection.DASHBOARD, SettingsHost.CONTROLS),
    HANDLEBAR_BUTTONS(SettingsSection.HANDLEBAR, SettingsHost.CONTROLS),
    PRESS_DELAYS(SettingsSection.HANDLEBAR, SettingsHost.CONTROLS),
    BT_HELPER(SettingsSection.HANDLEBAR, SettingsHost.CONTROLS),
    SPEED_VOLUME(SettingsSection.HANDLEBAR, SettingsHost.CONTROLS),
    NAV(null, SettingsHost.CONTROLS),
    PAD(null, SettingsHost.CONTROLS),
    ;
}

object SettingsSections {
    /** Blocks drawn for [section]; [host] is a sanity filter (a section lives in one activity). */
    fun blocksFor(section: SettingsSection): List<SettingsBlock> =
        SettingsBlock.entries.filter { it.section == section }

    /**
     * True if [block] stays visible when its host opens with [section]. With no section the Setup
     * screen shows everything (legacy full screen) and the Controls screen is "Mando": only the
     * destination search and the D-pad.
     */
    fun visible(block: SettingsBlock, section: SettingsSection?): Boolean = when {
        section != null -> block.section == section
        block.host == SettingsHost.CONTROLS -> block == SettingsBlock.NAV || block == SettingsBlock.PAD
        else -> true
    }
}
