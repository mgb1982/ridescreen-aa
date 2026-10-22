// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto.wear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Speed ring around the edge of the round main page: a 270° arc (gap at the bottom, where the page
 * dots sit) that fills with the speed, with tick marks at 50 / 90 / 120 km/h and a white dot at
 * the ride's top speed so far.
 *
 * There is NO road speed-limit data on the phone, so the colours do not mean "over the limit": the
 * ring is gold and only turns red past [RED_FROM_KMH], the usual top limit on Spanish roads.
 *
 * [setSpeed] with a null speed hides the ring; [setSummary] draws it full and dim (last-ride card).
 */
class SpeedRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val stroke = 5f * density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = stroke
        color = Color.parseColor("#26FFFFFF")
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = stroke
    }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.parseColor("#80FFFFFF")
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val rect = RectF()

    private var speed: Int? = null
    private var topKmh = 0
    private var summary = false

    /** [kmh] = current speed (null = no ring); [top] = the ride's max speed so far (0 = none). */
    fun setSpeed(kmh: Int?, top: Int) {
        if (kmh == speed && top == topKmh && !summary) return
        speed = kmh
        topKmh = top
        summary = false
        invalidate()
    }

    /** Last-ride card: a full, dim gold ring. */
    fun setSummary() {
        if (summary) return
        summary = true
        speed = null
        invalidate()
    }

    fun clear() {
        if (speed == null && !summary) return
        speed = null
        summary = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val kmh = speed
        if (kmh == null && !summary) return
        val side = min(width, height).toFloat()
        val inset = 4f * density + stroke / 2f
        rect.set(
            (width - side) / 2f + inset, (height - side) / 2f + inset,
            (width + side) / 2f - inset, (height + side) / 2f - inset,
        )
        canvas.drawArc(rect, START_DEG, SWEEP_DEG, false, track)
        if (summary) {
            arc.color = GOLD_DIM
            canvas.drawArc(rect, START_DEG, SWEEP_DEG, false, arc)
            return
        }
        val v = kmh ?: return
        if (v > 0) {
            arc.color = if (v >= RED_FROM_KMH) RED else GOLD
            canvas.drawArc(rect, START_DEG, SWEEP_DEG * frac(v), false, arc)
        }
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        for (t in TICKS) {
            val a = Math.toRadians((START_DEG + SWEEP_DEG * frac(t)).toDouble())
            val r1 = r - stroke * 0.9f
            val r2 = r + stroke * 0.9f
            canvas.drawLine(
                cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), tick,
            )
        }
        if (topKmh > 0) {
            val a = Math.toRadians((START_DEG + SWEEP_DEG * frac(topKmh)).toDouble())
            canvas.drawCircle(
                cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat(), stroke * 0.6f, dot,
            )
        }
    }

    private fun frac(kmh: Int): Float = (kmh.toFloat() / FULL_KMH).coerceIn(0f, 1f)

    companion object {
        /** Android arc angles: 0° = 3 o'clock, clockwise. 135° = bottom-left, 270° sweep to bottom-right. */
        private const val START_DEG = 135f
        private const val SWEEP_DEG = 270f
        private const val FULL_KMH = 140
        private const val RED_FROM_KMH = 120
        private val TICKS = intArrayOf(50, 90, 120)
        private val GOLD = Color.parseColor("#D1A955")
        private val GOLD_DIM = Color.parseColor("#80D1A955")
        private val RED = Color.parseColor("#E5484D")
    }
}
