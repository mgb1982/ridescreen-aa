// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun unitLabelsFollowTheLanguage() {
        assertEquals("km", Units.km("es"))
        assertEquals("км", Units.km("ru"))
        assertEquals("min", Units.min("de"))
        assertEquals("мин", Units.min("ru"))
        assertEquals("λεπ.", Units.min("el"))
    }
}
