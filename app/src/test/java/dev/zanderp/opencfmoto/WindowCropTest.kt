// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowCropTest {
    @Test fun defaultIsAnOptionAndInRange() {
        assertEquals(25, WindowCrop.DEFAULT)
        assertEquals(2, WindowCrop.indexOf(WindowCrop.DEFAULT))
        assertTrue(WindowCrop.OPTIONS.all { it == WindowCrop.coerce(it) })
    }
    @Test fun coerceClampsToZeroAndMax() {
        assertEquals(0, WindowCrop.coerce(-5))
        assertEquals(40, WindowCrop.coerce(90))
        assertEquals(15, WindowCrop.coerce(15))
    }
    @Test fun indexOfMatchesAndFallsBackToNearest() {
        assertEquals(0, WindowCrop.indexOf(0))
        assertEquals(1, WindowCrop.indexOf(15))
        assertEquals(3, WindowCrop.indexOf(35))
        assertEquals(3, WindowCrop.indexOf(40))
        assertEquals(1, WindowCrop.indexOf(14))
    }
}
