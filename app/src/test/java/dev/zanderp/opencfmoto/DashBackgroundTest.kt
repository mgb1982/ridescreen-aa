package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.DashBackground.Fit
import dev.zanderp.opencfmoto.DashBackground.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashBackgroundTest {
    @Test fun veilIsClampedTo0to80() {
        assertEquals(0, DashBackground.clampVeil(-5))
        assertEquals(80, DashBackground.clampVeil(100))
        assertEquals(40, DashBackground.clampVeil(40))
    }

    @Test fun veilAlphaFollowsPercent() {
        assertEquals(0, DashBackground.veilAlpha(0))
        assertEquals(102, DashBackground.veilAlpha(40))
        assertEquals(204, DashBackground.veilAlpha(80))
        assertEquals(204, DashBackground.veilAlpha(500))   // clamped
    }

    @Test fun fillCoversTheCanvasAndIsCentred() {
        val b = DashBackground.destBox(Fit.FILL, 1000, 1000, 800, 416)   // square on a wide canvas
        assertEquals(800f, b.width, 0.01f)
        assertEquals(800f, b.height, 0.01f)
        assertEquals(0f, b.left, 0.01f)
        assertEquals(-192f, b.top, 0.01f)
        assertTrue(b.top <= 0f && b.bottom >= 416f)
    }

    @Test fun fitKeepsTheWholePictureCentred() {
        val b = DashBackground.destBox(Fit.FIT, 1000, 1000, 800, 416)
        assertEquals(416f, b.width, 0.01f)
        assertEquals(416f, b.height, 0.01f)
        assertEquals(192f, b.left, 0.01f)
        assertEquals(0f, b.top, 0.01f)
    }

    @Test fun usesTheRealCanvasSize() {
        val b = DashBackground.destBox(Fit.FIT, 1280, 720, 1920, 1080)
        assertEquals(1920f, b.width, 0.01f)
        assertEquals(0f, b.top, 0.01f)
    }

    @Test fun degenerateSourceFillsTheCanvas() {
        val b = DashBackground.destBox(Fit.FIT, 0, 0, 800, 416)
        assertEquals(800f, b.right, 0.01f)
        assertEquals(416f, b.bottom, 0.01f)
    }

    @Test fun cacheKeyChangesWithEachPart() {
        val base = DashBackground.cacheKey(Mode.IMAGE, Fit.FILL, 40, 1L)
        assertEquals(base, DashBackground.cacheKey(Mode.IMAGE, Fit.FILL, 40, 1L))
        assertNotEquals(base, DashBackground.cacheKey(Mode.IMAGE, Fit.FIT, 40, 1L))
        assertNotEquals(base, DashBackground.cacheKey(Mode.IMAGE, Fit.FILL, 41, 1L))
        assertNotEquals(base, DashBackground.cacheKey(Mode.IMAGE, Fit.FILL, 40, 2L))
        assertNotEquals(base, DashBackground.cacheKey(Mode.DEFAULT, Fit.FILL, 40, 1L))
        assertEquals(base, DashBackground.Setting(Mode.IMAGE, Fit.FILL, 40, 1L).key())
    }

    @Test fun defaultModeIgnoresTheOtherParts() {
        assertEquals(DashBackground.cacheKey(Mode.DEFAULT, Fit.FILL, 10, 5L), DashBackground.cacheKey(Mode.DEFAULT, Fit.FIT, 70, 9L))
    }

    @Test fun fileNameIsPerBikeAndSafe() {
        assertEquals("b1a2b.jpg", DashBackground.fileName("b1a2b"))
        assertEquals("global.jpg", DashBackground.fileName(null))
        assertEquals("global.jpg", DashBackground.fileName(""))
        assertEquals("bab.jpg", DashBackground.fileName("b/../ab"))
        assertNotEquals(DashBackground.fileName("b1"), DashBackground.fileName("b2"))
    }

    @Test fun idsRoundTripAndUnknownFallsBack() {
        assertEquals(Mode.IMAGE, Mode.byId("image"))
        assertEquals(Mode.DEFAULT, Mode.byId("zzz"))
        assertEquals(Mode.DEFAULT, Mode.byId(null))
        assertEquals(Fit.FIT, Fit.byId("fit"))
        assertEquals(Fit.FILL, Fit.byId(null))
    }

    @Test fun storedSizeCapsWidthAndNeverEnlarges() {
        assertEquals(1280 to 720, DashBackground.storedSize(4000, 2250))
        assertEquals(800 to 600, DashBackground.storedSize(800, 600))
        val (w, h) = DashBackground.storedSize(1000, 5000)   // very tall: height cap applies
        assertTrue(h <= 2048 && w <= 1280)
    }

    @Test fun sampleSizeStaysAtOrAboveTarget() {
        assertEquals(1, DashBackground.sampleSize(1000, 800, 1280, 720))
        assertEquals(2, DashBackground.sampleSize(4000, 3000, 1280, 960))
        assertEquals(4, DashBackground.sampleSize(8000, 6000, 1280, 960))
        assertEquals(1, DashBackground.sampleSize(0, 0, 100, 100))
    }

    @Test fun exifOrientationToTransform() {
        assertEquals(DashBackground.Orientation(0, false), DashBackground.exifTransform(1))
        assertEquals(DashBackground.Orientation(90, false), DashBackground.exifTransform(6))
        assertEquals(DashBackground.Orientation(180, false), DashBackground.exifTransform(3))
        assertEquals(DashBackground.Orientation(270, false), DashBackground.exifTransform(8))
        assertEquals(DashBackground.Orientation(0, false), DashBackground.exifTransform(0))
    }

    @Test fun mirroredExifValuesFlip() {
        assertEquals(DashBackground.Orientation(0, true), DashBackground.exifTransform(2))
        assertEquals(DashBackground.Orientation(180, true), DashBackground.exifTransform(4))
        assertEquals(DashBackground.Orientation(270, true), DashBackground.exifTransform(5))
        assertEquals(DashBackground.Orientation(90, true), DashBackground.exifTransform(7))
    }

    @Test fun transparencyChoosesPng() {
        assertTrue(DashBackground.anyTransparent(intArrayOf(0xFFFFFFFF.toInt(), 0x80112233.toInt())))
        assertTrue(!DashBackground.anyTransparent(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())))
        assertTrue(!DashBackground.anyTransparent(intArrayOf(0x00000000, 0xFF000000.toInt()), 0))
        assertTrue(DashBackground.savesAsPng(true))
        assertTrue(!DashBackground.savesAsPng(false))
        assertEquals("b1.png", DashBackground.fileName("b1", true))
        assertTrue(DashBackground.isPngName("/x/dash_bg/b1.png"))
        assertTrue(!DashBackground.isPngName("/x/dash_bg/b1.jpg"))
        assertTrue(!DashBackground.isPngName(null))
    }

    @Test fun missingFileMeansOriginalBackground() {
        assertEquals(Mode.DEFAULT, DashBackground.effectiveMode(Mode.IMAGE, false))
        assertEquals(Mode.IMAGE, DashBackground.effectiveMode(Mode.IMAGE, true))
        assertEquals(Mode.DEFAULT, DashBackground.effectiveMode(Mode.DEFAULT, true))
    }

    @Test fun averageColorOfBorders() {
        assertEquals(0xFF000000.toInt(), DashBackground.averageColor(IntArray(0)))
        assertEquals(0xFF7F4020.toInt(), DashBackground.averageColor(intArrayOf(0xFF000000.toInt(), 0xFFFF8040.toInt())))
        assertEquals(0xFF112233.toInt(), DashBackground.averageColor(intArrayOf(0xFF112233.toInt(), 0xFF112233.toInt())))
    }
}
