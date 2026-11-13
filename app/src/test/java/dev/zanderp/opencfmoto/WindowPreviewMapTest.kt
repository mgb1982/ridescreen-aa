package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowPreviewMapTest {
    private val all = listOf(
        WindowPreviewMap.MAJOR, WindowPreviewMap.MINOR, WindowPreviewMap.DIAGONAL,
        WindowPreviewMap.PARKS, WindowPreviewMap.WATER, WindowPreviewMap.BLOCKS, WindowPreviewMap.ROUTE,
    )

    @Test fun everythingIsNormalised() {
        for (a in all) {
            assertTrue(a.size % 2 == 0)
            for (v in a) assertTrue(v in 0f..1f)
        }
        assertTrue(WindowPreviewMap.POS_X in 0f..1f && WindowPreviewMap.POS_Y in 0f..1f)
    }

    @Test fun segmentsAndRectsComeInFours() {
        for (a in listOf(WindowPreviewMap.MAJOR, WindowPreviewMap.MINOR, WindowPreviewMap.DIAGONAL,
            WindowPreviewMap.PARKS, WindowPreviewMap.WATER, WindowPreviewMap.BLOCKS)) assertEquals(0, a.size % 4)
        val r = WindowPreviewMap
        for (a in listOf(r.PARKS, r.WATER, r.BLOCKS)) for (i in a.indices step 4) {
            assertTrue(a[i] < a[i + 2] && a[i + 1] < a[i + 3])
        }
    }

    @Test fun routeHasAtLeastOneTurn() {
        assertTrue(WindowPreviewMap.turns(WindowPreviewMap.ROUTE) >= 1)
        assertEquals(0, WindowPreviewMap.turns(floatArrayOf(0f, 0f, .5f, .5f, 1f, 1f)))
    }

    @Test fun scaleRespectsRectangle() {
        val out = WindowPreviewMap.scale(floatArrayOf(0f, 0f, 1f, 1f, .5f, .25f), 10f, 20f, 200f, 100f)
        assertEquals(10f, out[0], 0f); assertEquals(20f, out[1], 0f)
        assertEquals(210f, out[2], 0f); assertEquals(120f, out[3], 0f)
        assertEquals(110f, out[4], 0f); assertEquals(45f, out[5], 0f)
    }

    @Test fun scaledRouteStaysInsideWindow() {
        val win = AaWindowLayout.window(1024, 464)
        val s = WindowPreviewMap.scale(WindowPreviewMap.ROUTE, win.x.toFloat(), win.y.toFloat(), win.w.toFloat(), win.h.toFloat())
        for (i in s.indices step 2) {
            assertTrue(s[i] >= win.x && s[i] <= win.x + win.w)
            assertTrue(s[i + 1] >= win.y && s[i + 1] <= win.y + win.h)
        }
    }
}
