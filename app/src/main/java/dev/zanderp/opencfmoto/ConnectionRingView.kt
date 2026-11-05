// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

/**
 * Glowing circular progress for the four connection steps. Display only.
 */
class ConnectionRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val arc = RectF()
    private var sweep = 12f
    private var failed = false
    private val mint = intArrayOf(
        0xFF0B6B45.toInt(),
        0xFF1FA971.toInt(),
        0xFFB6FFD8.toInt(),
        0xFF3DFF9A.toInt(),
        0xFF0B6B45.toInt(),
    )
    private val errorSweep = intArrayOf(
        0xFF8A3030.toInt(),
        0xFFFF8A80.toInt(),
        0xFFFF6B6B.toInt(),
        0xFF8A3030.toInt(),
    )

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
        val nextFailed = marks.any { it == ConnectionGuide.Mark.FAILED }
        if (nextFailed != failed) {
            failed = nextFailed
            rebuildShader()
        }
        sweep = (units / marks.size.coerceAtLeast(1) * 360f).coerceIn(8f, 360f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    private fun rebuildShader() {
        if (width == 0 || height == 0) return
        val cx = width / 2f
        val cy = height / 2f
        val shader = SweepGradient(cx, cy, if (failed) errorSweep else mint, null)
        shader.setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) })
        arcPaint.shader = shader
        glow.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = width * 0.05f
        track.strokeWidth = stroke
        track.shader = arcPaint.shader
        track.alpha = 220
        arcPaint.strokeWidth = stroke
        arcPaint.alpha = 255
        glow.strokeWidth = stroke * 3.1f
        glow.alpha = 140
        glow.maskFilter = BlurMaskFilter(stroke * 2f, BlurMaskFilter.Blur.NORMAL)
        val inset = stroke * 1.8f
        arc.set(inset, inset, width - inset, height - inset)
        canvas.drawArc(arc, -90f, 360f, false, track)
        canvas.drawArc(arc, -90f, sweep, false, glow)
        canvas.drawArc(arc, -90f, sweep, false, arcPaint)
    }
}
