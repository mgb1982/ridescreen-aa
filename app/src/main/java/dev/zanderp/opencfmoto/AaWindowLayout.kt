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
}
