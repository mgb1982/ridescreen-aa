// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * How the data column beside the Android Auto window is split into rows (pure, unit tested):
 * big speed on top, a thin RPM bar (with its label just above) and up to [MAX_CARDS] stacked cards.
 * Pixels of the bike canvas, top-left origin, same space as [AaWindowLayout.column].
 */
internal object AaColumnLayout {
    const val MAX_CARDS = 4

    class Rows(val speed: DashLayout.Box, val rpmLabelY: Float, val rpmLabelSize: Float,
               val bar: DashLayout.Box, val cards: List<DashLayout.Box>)

    fun rows(col: AaWindowLayout.Box, cardCount: Int): Rows {
        val x = col.x.toFloat(); val y = col.y.toFloat(); val w = col.w.toFloat(); val h = col.h.toFloat()
        val n = cardCount.coerceIn(0, MAX_CARDS)
        val speed = DashLayout.Box(x, y, w, h * .32f)
        val labelSize = (h * .045f).coerceAtLeast(11f)
        val barY = speed.bottom + h * .07f
        val bar = DashLayout.Box(x, barY, w, (h * .06f).coerceAtLeast(8f))
        val gap = (h * .018f).coerceAtLeast(4f)
        val top = bar.bottom + h * .035f
        val room = y + h - top
        val cardH = if (n == 0) 0f else minOf(h * .2f, (room - gap * (n - 1)) / n).coerceAtLeast(1f)
        val cards = List(n) { i -> DashLayout.Box(x, top + i * (cardH + gap), w, cardH) }
        return Rows(speed, barY - h * .012f, labelSize, bar, cards)
    }
}
