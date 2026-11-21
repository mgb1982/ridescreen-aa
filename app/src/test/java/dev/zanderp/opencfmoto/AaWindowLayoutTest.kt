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

    @Test fun fillCropIsKeptInTheWindow() {
        // 125X-like: area 1024x464, Android Auto 800x480 under "Fill" → viewport 1024x614, 75 px cut top and bottom.
        val win = AaWindowLayout.Box(8, 8, 640, 448)
        val p = AaWindowLayout.placeFullScreen(win, 1024, 464, 0, -75, 1024, 614)!!
        assertTrue(p.clip.x >= win.x && p.clip.x + p.clip.w <= win.x + win.w)
        assertTrue(p.clip.y >= win.y && p.clip.y + p.clip.h <= win.y + win.h)
        assertEquals(640, p.clip.w)                       // area aspect fits the window by width
        assertTrue("picture overflows the clip like full screen", p.draw.h > p.clip.h && p.draw.y < p.clip.y)
        assertEquals(p.clip.w, p.draw.w)
    }

    @Test fun windowTouchMapsBackToFullScreenArea() {
        val win = AaWindowLayout.Box(8, 8, 640, 448)
        val p = AaWindowLayout.placeFullScreen(win, 1024, 464, 0, -75, 1024, 614)!!
        val centre = AaWindowLayout.windowToArea(p, 1024, 464, p.clip.x + p.clip.w / 2, p.clip.y + p.clip.h / 2)!!
        assertEquals(512, centre.first)
        assertEquals(232, centre.second)
        assertEquals(null, AaWindowLayout.windowToArea(p, 1024, 464, p.clip.x - 1, p.clip.y))
    }

    @Test fun unknownGeometryFallsBack() {
        assertEquals(null, AaWindowLayout.placeFullScreen(AaWindowLayout.Box(0, 0, 10, 10), 0, 0, 0, 0, 0, 0))
    }
}
