// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** Dash texts (the controller fills them from resources; English defaults keep [ChronoOverlay.draw] context-free). */
internal data class ChronoLabels(
    val armed: String = "ARMED · stop to start",
    val go: String = "GO",
    val running: String = "Timing…",
    val cancelled: String = "Cancelled",
)

/** Paints the 3-2-1 count, GO and the result over the finished dashboard bitmap, inside the visible zone. */
internal object ChronoOverlay {
    private val shade = Paint().apply { color = Color.argb(185, 0, 0, 0) }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    /** Whether the dash needs the overlay for [s] (also what the redraw signature keys on). */
    fun active(s: ChronoState) = s.phase != ChronoPhase.IDLE && s.dashVisible

    internal fun draw(
        c: Canvas, w: Int, h: Int, state: ChronoState, imperial: Boolean, labels: ChronoLabels = ChronoLabels(),
    ) {
        if (!active(state)) return
        // Visible zone: y < 326, and right of x = 676 only down to y = 284. Stay inside the left block.
        val right = minOf(w, DashboardRenderer.VIS_RIGHT_STEP_X.toInt()).toFloat()
        val bottom = minOf(h.toFloat(), DashboardRenderer.VIS_BOTTOM)
        val cx = right / 2f
        val cy = bottom / 2f
        // Running: nothing on top, the rider needs the speed. Armed: only a small banner, not the whole dash.
        if (state.phase == ChronoPhase.RUNNING) return
        if (state.phase == ChronoPhase.ARMED) {
            c.drawRoundRect(cx - 270f, 70f, cx + 270f, 130f, 18f, 18f, shade)
            text(c, small, labels.armed, cx, 100f, 30f)
            return
        }
        c.drawRect(0f, 0f, right, bottom, shade)
        when (state.phase) {
            ChronoPhase.ARMED -> Unit
            ChronoPhase.COUNTDOWN -> text(c, big, state.count.toString(), cx, cy, 220f)
            ChronoPhase.WAITING_LAUNCH -> {
                big.color = Color.rgb(80, 220, 120); text(c, big, labels.go, cx, cy, 150f); big.color = Color.WHITE
            }
            ChronoPhase.RUNNING -> Unit
            ChronoPhase.DONE -> {
                val lines = listOfNotNull(
                    state.t1?.let { "0–${ZeroToSpeed.target1Label(imperial)}  ${ZeroToSpeed.fmtTime(it)} s" },
                    state.t2?.let { "0–${ZeroToSpeed.target2Label(imperial)}  ${ZeroToSpeed.fmtTime(it)} s" },
                )
                lines.forEachIndexed { i, t -> text(c, small, t, cx, cy + (i - (lines.size - 1) / 2f) * 80f, 64f) }
            }
            ChronoPhase.CANCELLED -> text(c, small, labels.cancelled, cx, cy, 52f)
            ChronoPhase.IDLE -> Unit
        }
    }

    private fun text(c: Canvas, p: Paint, s: String, cx: Float, cy: Float, size: Float) {
        p.textSize = size
        val fm = p.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, p)
    }
}
