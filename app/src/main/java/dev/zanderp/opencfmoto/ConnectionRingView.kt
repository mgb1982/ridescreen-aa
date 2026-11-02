// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Glowing circular progress for the four connection steps. Display only.
 */
class ConnectionRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val arc = RectF()
    private var sweep = 12f
    private var accent = 0xFF3DFF9A.toInt()
    private var trackColor = 0xFF1C2A22.toInt()

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        accent = ContextCompat.getColor(context, R.color.brand_orange)
        trackColor = ContextCompat.getColor(context, R.color.surface_high)
    }

    fun setMarks(marks: List<ConnectionGuide.Mark>) {
        var units = 0f
        for (mark in marks) {
            units += when (mark) {
                ConnectionGuide.Mark.DONE -> 1f
                ConnectionGuide.Mark.CURRENT -> 0.55f
                ConnectionGuide.Mark.FAILED -> 0.35f
                ConnectionGuide.Mark.TODO -> 0f
            }
        }
        val failed = marks.any { it == ConnectionGuide.Mark.FAILED }
        accent = ContextCompat.getColor(
            context,
            if (failed) R.color.status_error else R.color.brand_orange,
        )
        sweep = (units / marks.size.coerceAtLeast(1) * 360f).coerceIn(8f, 360f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = width * 0.045f
        track.strokeWidth = stroke
        track.color = trackColor
        arcPaint.strokeWidth = stroke
        arcPaint.color = accent
        glow.strokeWidth = stroke * 2.4f
        glow.color = accent
        glow.alpha = 90
        glow.maskFilter = BlurMaskFilter(stroke * 1.6f, BlurMaskFilter.Blur.NORMAL)
        val inset = stroke * 1.8f
        arc.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(arc, 0f, 360f, false, track)
        canvas.drawArc(arc, -90f, sweep, false, glow)
        canvas.drawArc(arc, -90f, sweep, false, arcPaint)
    }
}
