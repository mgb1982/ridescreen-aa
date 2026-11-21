// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Where the Android Auto window sits inside the custom dashboard (pure, unit tested).
 * Shared by the compositor (draws Android Auto there) and the renderer (leaves it empty and lays the
 * data out in the column beside it), so both always agree. Top-left origin, pixels of the bike canvas.
 */
object AaWindowLayout {
    /** Share of the canvas width the window takes (left side); the data column gets the rest. */
    const val WINDOW_SHARE = 0.64f

    class Box(val x: Int, val y: Int, val w: Int, val h: Int)

    fun margin(h: Int): Int = maxOf(4, h / 60)

    /** The Android Auto window, left side, full height minus the margin. */
    fun window(canvasW: Int, canvasH: Int): Box {
        val m = margin(canvasH)
        val w = (canvasW * WINDOW_SHARE).toInt() - m
        return Box(m, m, maxOf(1, w - m), maxOf(1, canvasH - 2 * m))
    }

    /** The data column to the right of the window. */
    fun column(canvasW: Int, canvasH: Int): Box {
        val m = margin(canvasH)
        val win = window(canvasW, canvasH)
        val x = win.x + win.w + 2 * m
        return Box(x, m, maxOf(1, canvasW - m - x), maxOf(1, canvasH - 2 * m))
    }

    /**
     * Aspect-fit a [srcW]×[srcH] picture inside [box] (letterbox, nothing cropped), centred.
     * Android Auto draws its own layout for the whole frame, so cropping could cut off the guidance card.
     */
    fun fit(box: Box, srcW: Int, srcH: Int): Box {
        if (srcW <= 0 || srcH <= 0) return box
        val s = minOf(box.w.toFloat() / srcW, box.h.toFloat() / srcH)
        val w = maxOf(1, (srcW * s).toInt())
        val h = maxOf(1, (srcH * s).toInt())
        return Box(box.x + (box.w - w) / 2, box.y + (box.h - h) / 2, w, h)
    }

    /** Where the Android Auto picture goes inside the window: [clip] is visible, [draw] may overflow it. */
    class Placement(val clip: Box, val draw: Box)

    /**
     * Show in the window exactly what the full-screen Android Auto mode shows, only smaller: the rider's
     * usable area ([areaW]x[areaH], the canvas minus the screen margins) is aspect-fit into [win] ([clip]),
     * and the full-screen viewport ([vpX],[vpY] relative to the area, [vpW]x[vpH]) is scaled with it ([draw]).
     * Under "Fill" the viewport is bigger than the area, so the same top/bottom (or sides) are cut off as
     * in full screen — the tester's "recorte" (21 Nov 2026), which hides Android Auto's icon row.
     * Null when the full-screen geometry is not known yet (the caller then letterboxes the whole frame).
     */
    fun placeFullScreen(win: Box, areaW: Int, areaH: Int, vpX: Int, vpY: Int, vpW: Int, vpH: Int): Placement? {
        if (areaW <= 0 || areaH <= 0 || vpW <= 0 || vpH <= 0) return null
        val clip = fit(win, areaW, areaH)
        val sx = clip.w.toDouble() / areaW
        val sy = clip.h.toDouble() / areaH
        val draw = Box(
            clip.x + Math.round(vpX * sx).toInt(),
            clip.y + Math.round(vpY * sy).toInt(),
            maxOf(1, Math.round(vpW * sx).toInt()),
            maxOf(1, Math.round(vpH * sy).toInt()),
        )
        return Placement(clip, draw)
    }

    /**
     * A touch at ([x],[y]) on the canvas, inside [p].clip, back to usable-area pixels of the full-screen
     * mode (so the normal full-screen touch mapping applies). Null outside the visible window.
     */
    fun windowToArea(p: Placement, areaW: Int, areaH: Int, x: Int, y: Int): Pair<Int, Int>? {
        val c = p.clip
        if (x < c.x || y < c.y || x >= c.x + c.w || y >= c.y + c.h) return null
        val ax = ((x - c.x).toLong() * areaW / c.w).toInt().coerceIn(0, areaW - 1)
        val ay = ((y - c.y).toLong() * areaH / c.h).toInt().coerceIn(0, areaH - 1)
        return ax to ay
    }
}
