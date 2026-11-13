// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AaWindowLayoutTest {
    @Test fun windowAndColumnDoNotOverlapAndStayInside() {
        for ((w, h) in listOf(1024 to 464, 1280 to 535, 800 to 416, 1024 to 425)) {
            val win = AaWindowLayout.window(w, h)
            val col = AaWindowLayout.column(w, h)
            assertTrue(win.x >= 0 && win.y >= 0 && win.x + win.w <= w && win.y + win.h <= h)
            assertTrue(col.x >= win.x + win.w)
            assertTrue(col.x + col.w <= w && col.y + col.h <= h)
            assertTrue("column too narrow at ${w}x$h", col.w >= w / 4)
        }
    }

    @Test fun fitLetterboxesWithoutCropping() {
        val box = AaWindowLayout.Box(10, 10, 600, 400)
        val f = AaWindowLayout.fit(box, 800, 480)   // 5:3 into 3:2 → full width, bars top/bottom
        assertEquals(600, f.w)
        assertEquals(360, f.h)
        assertEquals(10, f.x)
        assertEquals(30, f.y)
    }

    @Test fun fitWithUnknownSourceKeepsTheBox() {
        val box = AaWindowLayout.Box(1, 2, 3, 4)
        assertEquals(3, AaWindowLayout.fit(box, 0, 0).w)
    }
}
