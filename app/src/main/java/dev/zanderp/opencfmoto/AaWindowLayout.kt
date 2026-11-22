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
}
