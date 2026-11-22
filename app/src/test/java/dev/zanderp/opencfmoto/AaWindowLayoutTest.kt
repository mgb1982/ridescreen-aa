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

    @Test fun windowAndColumnStayAboveTheDashStrip() {
        for ((w, h) in listOf(800 to 416, 1024 to 464, 1280 to 535)) {
            val win = AaWindowLayout.window(w, h)
            val col = AaWindowLayout.column(w, h)
            assertTrue("window runs under the strip at ${w}x$h", win.y + win.h <= AaWindowLayout.visibleBottom(w, h))
            assertTrue("column runs under the raised strip at ${w}x$h", col.y + col.h <= AaWindowLayout.visibleBottomRight(w, h))
            assertTrue(win.h > h / 2 && col.h > h / 2)
        }
    }

    @Test fun the125xWindowFitsAndroidAutoAlmostExactly() {
        // 800x416 canvas: window ~500x314 (aspect 1.59) for a 800x480 Android Auto (1.67): nearly no dead band.
        val win = AaWindowLayout.window(800, 416)
        assertEquals(320, AaWindowLayout.visibleBottom(800, 416) - 6)
        val f = AaWindowLayout.fit(win, 800, 480)
        assertTrue("dead band ${win.h - f.h}px", win.h - f.h <= 20)
        assertEquals(win.w, f.w)
    }

    private val win125 = AaWindowLayout.window(800, 416)

    @Test fun noCropIsThePlainFit() {
        val p = AaWindowLayout.placeZoomed(win125, 800, 480, 0, 0)
        val f = AaWindowLayout.fit(win125, 800, 480)
        assertEquals(f.w, p.draw.w); assertEquals(f.h, p.draw.h); assertEquals(f.x, p.clip.x); assertEquals(f.y, p.clip.y)
    }

    @Test fun bottomBarIsCutAndNothingLeavesTheWindow() {
        val p = AaWindowLayout.placeZoomed(win125, 800, 480, 25)
        assertTrue(p.clip.x >= win125.x && p.clip.y >= win125.y)
        assertTrue(p.clip.x + p.clip.w <= win125.x + win125.w && p.clip.y + p.clip.h <= win125.y + win125.h)
        val hidden = (p.draw.y + p.draw.h) - (p.clip.y + p.clip.h)
        assertTrue("hidden ${hidden}px of ${p.draw.h}", hidden >= p.draw.h * 22 / 100 && hidden <= p.draw.h * 28 / 100)
        assertTrue("top of the picture stays", p.draw.y == p.clip.y)
    }

    @Test fun zoomFillsMoreOfTheWindowButCutsLittleSideways() {
        val plain = AaWindowLayout.placeZoomed(win125, 800, 480, 25, 0)
        val zoom = AaWindowLayout.placeZoomed(win125, 800, 480, 25)
        assertTrue(zoom.clip.h > plain.clip.h)
        val cutSide = (zoom.draw.w - zoom.clip.w) * 100 / zoom.draw.w
        assertTrue("cut $cutSide% sideways", cutSide <= AaWindowLayout.MAX_SIDE_CROP_PCT)
    }

    @Test fun touchesMapThroughTheZoom() {
        val p = AaWindowLayout.placeZoomed(win125, 800, 480, 25)
        val c = p.clip
        val top = AaWindowLayout.windowToSource(p, 800, 480, c.x + c.w / 2, c.y)!!
        assertTrue(top.second <= 2)
        val bottom = AaWindowLayout.windowToSource(p, 800, 480, c.x + c.w / 2, c.y + c.h - 1)!!
        assertTrue("bottom of the visible part is ${bottom.second} of 480", bottom.second in 340..365)
        assertEquals(null, AaWindowLayout.windowToSource(p, 800, 480, c.x - 1, c.y))
        assertEquals(null, AaWindowLayout.windowToSource(p, 800, 480, c.x, c.y + c.h))
    }

    @Test fun unknownFrameKeepsTheWindow() {
        assertEquals(win125.w, AaWindowLayout.placeZoomed(win125, 0, 0, 25).clip.w)
    }
}
