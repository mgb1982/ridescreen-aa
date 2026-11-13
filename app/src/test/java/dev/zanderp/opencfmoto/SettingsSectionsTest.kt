// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionsTest {
    @Test fun everySectionHasBlocks() {
        for (s in SettingsSection.entries) assertTrue(s.name, SettingsSections.blocksFor(s).isNotEmpty())
    }

    @Test fun blocksLiveInTheirSectionsHost() {
        for (b in SettingsBlock.entries) {
            val s = b.section ?: continue
            assertEquals(b.name, s.host, b.host)
        }
    }

    @Test fun keysAreUniqueAndRoundTrip() {
        assertEquals(SettingsSection.entries.size, SettingsSection.entries.map { it.key }.toSet().size)
        for (s in SettingsSection.entries) assertEquals(s, SettingsSection.fromKey(s.key))
        assertNull(SettingsSection.fromKey("nope"))
        assertNull(SettingsSection.fromKey(null))
    }

    @Test fun noSectionSetupShowsEverything() {
        for (b in SettingsBlock.entries.filter { it.host == SettingsHost.SETUP }) {
            assertTrue(SettingsSections.visible(b, null))
        }
    }

    @Test fun noSectionControlsIsMandoOnly() {
        for (b in SettingsBlock.entries.filter { it.host == SettingsHost.CONTROLS }) {
            val expected = b == SettingsBlock.NAV || b == SettingsBlock.PAD
            assertEquals(b.name, expected, SettingsSections.visible(b, null))
        }
    }

    @Test fun sectionShowsOnlyItsBlocks() {
        assertTrue(SettingsSections.visible(SettingsBlock.THEME, SettingsSection.DISPLAY))
        assertFalse(SettingsSections.visible(SettingsBlock.THEME, SettingsSection.ADVANCED))
        assertFalse(SettingsSections.visible(SettingsBlock.NAV, SettingsSection.HANDLEBAR))
        assertTrue(SettingsSections.visible(SettingsBlock.PRESS_DELAYS, SettingsSection.HANDLEBAR))
    }

    @Test fun themeIsInOnePlaceOnly() {
        val holders = SettingsBlock.entries.filter { it.name.contains("THEME") }
        assertEquals(listOf(SettingsBlock.THEME), holders)
    }
}
