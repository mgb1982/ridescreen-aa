package dev.zanderp.opencfmoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDimTest {
    private val far = Long.MAX_VALUE / 2

    private fun cover(
        enabled: Boolean = true,
        gpx: Boolean = true,
        draw: Boolean = true,
        bg: Long = 10_000L,
        since: Long = far,
        covering: Boolean = false,
    ) = PocketDim.shouldCover(enabled, gpx, draw, bg, since, covering)

    private fun uncover(
        enabled: Boolean = true,
        gpx: Boolean = true,
        draw: Boolean = true,
        fg: Boolean = false,
        covering: Boolean = true,
    ) = PocketDim.shouldUncover(enabled, gpx, draw, fg, covering)

    @Test fun coversWhenAllConditionsHold() = assertTrue(cover())
    @Test fun notWhenDisabled() = assertFalse(cover(enabled = false))
    @Test fun notWithoutMapMode() = assertFalse(cover(gpx = false))
    @Test fun notWithoutOverlayPermission() = assertFalse(cover(draw = false))
    @Test fun notWhenAlreadyCovering() = assertFalse(cover(covering = true))

    @Test fun delayEdge() {
        assertFalse(cover(bg = 7_999L))
        assertTrue(cover(bg = 8_000L))
    }

    @Test fun foregroundNeverCovers() {
        assertFalse(cover(bg = -1L))
        assertFalse(cover(bg = 0L))
    }

    @Test fun pauseAfterTouch() {
        assertFalse(cover(since = 0L))
        assertFalse(cover(since = 19_999L))
        assertTrue(cover(since = 20_000L))
    }

    @Test fun uncoverNeedsCovering() {
        assertFalse(uncover(enabled = false, covering = false))
        assertFalse(uncover(fg = true, covering = false))
    }

    @Test fun keepsCoverWhenAllGood() = assertFalse(uncover())
    @Test fun uncoverWhenDisabled() = assertTrue(uncover(enabled = false))
    @Test fun uncoverWhenMapEnds() = assertTrue(uncover(gpx = false))
    @Test fun uncoverWhenPermissionLost() = assertTrue(uncover(draw = false))
    @Test fun uncoverWhenAppForeground() = assertTrue(uncover(fg = true))
}
