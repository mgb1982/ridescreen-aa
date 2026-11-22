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

    // What the dash really shows of the canvas: it lays its own speed/fuel strip over the bottom (and a
    // higher one right of VIS_RIGHT_STEP_X). DashboardRenderer defines these for its 800x416 design space;
    // here they are mapped to the real canvas the same way the other designs are (scaled, centred).
    private const val DESIGN_W = 800f
    private const val DESIGN_H = 416f

    private fun scale(canvasW: Int, canvasH: Int) = minOf(canvasW / DESIGN_W, canvasH / DESIGN_H)

    /** Lowest visible canvas row in the left and centre part of the dash. */
    fun visibleBottom(canvasW: Int, canvasH: Int): Int {
        val k = scale(canvasW, canvasH)
        val dy = (canvasH - DESIGN_H * k) / 2f
        return minOf(canvasH.toFloat(), dy + DashboardRenderer.VIS_BOTTOM * k).toInt()
    }

    /** Lowest visible row right of [visibleStepX], where the dash's strip steps up. */
    fun visibleBottomRight(canvasW: Int, canvasH: Int): Int {
        val k = scale(canvasW, canvasH)
        val dy = (canvasH - DESIGN_H * k) / 2f
        return minOf(canvasH.toFloat(), dy + DashboardRenderer.VIS_BOTTOM_RIGHT * k).toInt()
    }

    fun visibleStepX(canvasW: Int, canvasH: Int): Int {
        val k = scale(canvasW, canvasH)
        val dx = (canvasW - DESIGN_W * k) / 2f
        return (dx + DashboardRenderer.VIS_RIGHT_STEP_X * k).toInt()
    }

    /**
     * The Android Auto window, left side. It ends above the dash's own bottom strip: before, it ran the
     * full canvas height, so the picture was centred in a box that reached under the strip, sat low with
     * a dead band on top and had its bottom row hidden (tester photos, 22 Nov 2026).
     */
    fun window(canvasW: Int, canvasH: Int): Box {
        val m = margin(canvasH)
        val w = (canvasW * WINDOW_SHARE).toInt() - m
        return Box(m, m, maxOf(1, w - m), maxOf(1, visibleBottom(canvasW, canvasH) - 2 * m))
    }

    /** The data column to the right of the window; its lower edge clears the strip's raised part too. */
    fun column(canvasW: Int, canvasH: Int): Box {
        val m = margin(canvasH)
        val win = window(canvasW, canvasH)
        val x = win.x + win.w + 2 * m
        val w = maxOf(1, canvasW - m - x)
        val bottom = if (x + w > visibleStepX(canvasW, canvasH)) visibleBottomRight(canvasW, canvasH)
            else visibleBottom(canvasW, canvasH)
        return Box(x, m, w, maxOf(1, bottom - 2 * m))
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

    /**
     * Share (percent) of the Android Auto picture hidden at the bottom of the window, where Android Auto
     * draws its shortcut bar. At full screen the 125X's own strip covers that part; in the window nothing
     * does, so the picture is zoomed and cut. Set from the dashboard settings ([DashboardMode]); 0 = off.
     */
    @Volatile var bottomCropPct: Int = DEFAULT_BOTTOM_CROP_PCT
    const val DEFAULT_BOTTOM_CROP_PCT = 25
    const val MAX_BOTTOM_CROP_PCT = 40

    /** Most of the width (percent, both sides together) that zooming may cut to fill the window's height. */
    const val MAX_SIDE_CROP_PCT = 10

    /** [clip] is the visible part, [draw] is the whole Android Auto picture (it overflows [clip]). */
    class Placement(val clip: Box, val draw: Box)

    /**
     * Zoom the [frameW]x[frameH] Android Auto picture into [win]: the bottom [cropPct]% is cut off, and the
     * rest is enlarged to fill the window's height as far as [MAX_SIDE_CROP_PCT] of the width may be cut,
     * otherwise it is simply fitted (nothing cropped sideways, so the guidance card stays whole).
     * With [cropPct] 0 and no side crop it is the plain [fit].
     */
    fun placeZoomed(win: Box, frameW: Int, frameH: Int, cropPct: Int, maxSideCropPct: Int = MAX_SIDE_CROP_PCT): Placement {
        if (frameW <= 0 || frameH <= 0) return Placement(win, win)
        val c = cropPct.coerceIn(0, MAX_BOTTOM_CROP_PCT)
        val side = maxSideCropPct.coerceIn(0, 40)
        val keepH = frameH * (100 - c) / 100.0
        val sFit = minOf(win.w / frameW.toDouble(), win.h / keepH)
        val sCover = maxOf(win.w / frameW.toDouble(), win.h / keepH)
        val s = minOf(sCover, sFit / (1.0 - side / 100.0))
        val drawW = maxOf(1, Math.round(frameW * s).toInt())
        val drawH = maxOf(1, Math.round(frameH * s).toInt())
        val clipW = minOf(win.w, drawW)
        val clipH = minOf(win.h, maxOf(1, Math.round(keepH * s).toInt()))
        val clip = Box(win.x + (win.w - clipW) / 2, win.y + (win.h - clipH) / 2, clipW, clipH)
        // centred sideways, top-aligned: the cut is at the bottom
        return Placement(clip, Box(clip.x + (clipW - drawW) / 2, clip.y, drawW, drawH))
    }

    /** A touch at ([x],[y]) on the canvas to a pixel of the [srcW]x[srcH] source; null outside the visible clip. */
    fun windowToSource(p: Placement, srcW: Int, srcH: Int, x: Int, y: Int): Pair<Int, Int>? {
        val c = p.clip
        if (x < c.x || y < c.y || x >= c.x + c.w || y >= c.y + c.h) return null
        val d = p.draw
        val sx = ((x - d.x).toLong() * srcW / d.w).toInt().coerceIn(0, srcW - 1)
        val sy = ((y - d.y).toLong() * srcH / d.h).toInt().coerceIn(0, srcH - 1)
        return sx to sy
    }
}
