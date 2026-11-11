// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * 2.5 · layout engine of the on-dash dashboard: where the telemetry cards go, how big their text is,
 * the rider's card order and accent colour. Pure Kotlin (no Android types), unit tested on the JVM.
 *
 * Every style hands over the [Area] its cards may use. [cards] tries every way of splitting the cards
 * into rows and keeps the one whose cards come out biggest, so hiding fields makes the others grow.
 * A row reaching below [Area.stepBottom] must end before [Area.stepX]: on the 125X the dash's own
 * speed readout steps up there (see [DashboardRenderer.VIS_BOTTOM_RIGHT]).
 */
internal object DashLayout {

    /** Card fields in the order of the reference designs (also the default order). */
    val DEFAULT_CARDS = listOf("coolant", "volt", "load", "intake", "baro")

    data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
        val right: Float get() = x + w
        val bottom: Float get() = y + h
    }

    /** Where a style's cards may go, in 800×416 frame units. */
    data class Area(
        val left: Float, val top: Float, val right: Float,
        /** Lowest readable line; 2 px above [DashboardRenderer.VIS_BOTTOM]. */
        val bottom: Float = 324f,
        val stepX: Float = 676f, val stepBottom: Float = 284f,
        val gapX: Float = 10f, val gapY: Float = 6f,
        /** Tallest card: one or two cards stay cards, not posters. */
        val maxH: Float = 92f,
    )

    val SPORT = Area(left = 360f, top = 152f, right = 785f, gapX = 13f)
    val RACING = Area(left = 30f, top = 216f, right = 770f, gapX = 10f)
    val TOURING = Area(left = 22f, top = 214f, right = 778f, gapX = 9f)

    // A card needs about this much width for its height: icon, title and a value such as "105 °C".
    private const val WIDTH_PER_H = 2.4f
    private const val WIDTH_PAD = 12f
    /** Clearance between a low row and the dash's raised speed readout. */
    private const val STEP_MARGIN = 6f
    private const val MAX_ROWS = 4

    private class Plan(
        val rows: Int, val counts: List<Int>, val widths: List<Float>,
        /** Card height. */ val size: Float,
        /** Height the narrowest card's width would allow: room left for its text. */ val room: Float,
    )

    /** Boxes for [n] cards, in reading order (row by row, left to right). */
    fun cards(area: Area, n: Int): List<Box> {
        if (n <= 0) return emptyList()
        var best: Plan? = null
        for (rows in 1..minOf(n, MAX_ROWS)) {
            val h = minOf(area.maxH, (area.bottom - area.top - (rows - 1) * area.gapY) / rows)
            if (h <= 0f) continue
            val widths = (0 until rows).map { rowWidth(area, area.top + it * (h + area.gapY) + h) }
            for (counts in splits(n, rows)) {
                val room = counts.indices.minOf { i -> fitHeight(cardWidth(widths[i], counts[i], area.gapX)) }
                val plan = Plan(rows, counts, widths, minOf(h, room), room)
                if (best == null || better(plan, best)) best = plan
            }
        }
        val plan = best ?: return emptyList()
        // Every card gets the plan's size; rows only move up from where they were checked, so a row
        // that cleared the step still clears it.
        val h = plan.size
        val out = ArrayList<Box>(n)
        for (row in 0 until plan.rows) {
            val count = plan.counts[row]
            val w = cardWidth(plan.widths[row], count, area.gapX)
            val y = area.top + row * (h + area.gapY)
            for (i in 0 until count) out.add(Box(area.left + i * (w + area.gapX), y, w, h))
        }
        return out
    }

    /** Usable width of a row whose bottom is at [rowBottom]. */
    private fun rowWidth(area: Area, rowBottom: Float): Float {
        val right = if (rowBottom > area.stepBottom) minOf(area.right, area.stepX - STEP_MARGIN) else area.right
        return right - area.left
    }

    private fun cardWidth(rowWidth: Float, count: Int, gap: Float) = (rowWidth - (count - 1) * gap) / count

    /** Tallest card whose text still fits in [width]. */
    private fun fitHeight(width: Float) = (width - WIDTH_PAD) / WIDTH_PER_H

    /**
     * Bigger cards win; then wider ones (an even split such as 3+2 over 4+1); then fewer rows; then
     * more cards on the upper rows, which are the wide ones.
     */
    private fun better(a: Plan, b: Plan): Boolean {
        if (a.size > b.size + 0.01f) return true
        if (a.size < b.size - 0.01f) return false
        if (a.room > b.room + 0.01f) return true
        if (a.room < b.room - 0.01f) return false
        if (a.rows != b.rows) return a.rows < b.rows
        for (i in a.counts.indices) if (a.counts[i] != b.counts[i]) return a.counts[i] > b.counts[i]
        return false
    }

    /** Every way of putting [n] cards into [rows] non-empty rows. */
    internal fun splits(n: Int, rows: Int): List<List<Int>> {
        if (rows == 1) return listOf(listOf(n))
        val out = ArrayList<List<Int>>()
        for (first in 1..n - rows + 1) for (rest in splits(n - first, rows - 1)) out.add(listOf(first) + rest)
        return out
    }

    // ── text inside a card

    /** Positions and sizes of a card's icon, title and value, all following its height. */
    data class CardText(
        val iconX: Float, val iconR: Float, val textX: Float,
        val titleY: Float, val titleSize: Float, val valueY: Float, val valueSize: Float,
    )

    /** At 50 px this is the 2.4 card: icon at +23 (r 10), text at +46, title 13, value 24. */
    fun cardText(b: Box): CardText {
        val h = b.h
        return CardText(
            iconX = b.x + .46f * h, iconR = .2f * h, textX = b.x + .92f * h,
            titleY = b.y + .40f * h, titleSize = (.26f * h).coerceIn(12f, 24f),
            valueY = b.y + h - .14f * h, valueSize = .48f * h,
        )
    }

    /** Factor (≤ 1) that shrinks text measuring [needed] into [available]. */
    fun fitScale(available: Float, needed: Float): Float =
        if (needed <= 0f || needed <= available) 1f else (available / needed).coerceAtLeast(.4f)

    // ── card order

    /**
     * The visible cards in the rider's order. Ids missing from [order] follow in the default order,
     * so a field added in a later version still shows; unknown ids and duplicates are dropped.
     */
    fun orderedCards(order: List<String>, visible: Set<String>, all: List<String> = DEFAULT_CARDS): List<String> {
        val known = all.toSet()
        val head = order.filter { it in known }.distinct()
        return (head + all.filter { it !in head }).filter { it in visible }
    }

    fun parseOrder(s: String?): List<String> =
        s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun encodeOrder(order: List<String>): String = order.joinToString(",")

    /** [id] moved one place up ([delta] = -1) or down (+1) in the full order; ends stay put. */
    fun move(order: List<String>, id: String, delta: Int, all: List<String> = DEFAULT_CARDS): List<String> {
        val full = orderedCards(order, all.toSet(), all).toMutableList()
        val i = full.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in full.indices) return full
        full[i] = full[j].also { full[j] = full[i] }
        return full
    }

    // ── accent colour

    /** Picker choices; 0 keeps the style's own colour. Red zones stay red whatever is chosen. */
    val ACCENT_PRESETS: List<Int> = listOf(
        0,
        0xFFFF8A1E.toInt(), // orange
        0xFFE8192C.toInt(), // red
        0xFF2FCB9E.toInt(), // teal
        0xFF3D8BFF.toInt(), // blue
        0xFFA35CFF.toInt(), // purple
        0xFFF2C230.toInt(), // yellow
        0xFF3CC35A.toInt(), // green
    )

    data class Accent(val accent: Int, val accent2: Int, val edge: Int)

    /** Accent, its lighter companion and a card edge tinted towards it, for a [night] or day palette. */
    fun accentFor(color: Int, night: Boolean, tile: Int): Accent {
        val a = if (night) color or OPAQUE else blend(color or OPAQUE, BLACK, .14f) // a touch darker on light
        return Accent(a, blend(a, WHITE, .40f), blend(tile, a, .40f))
    }

    /** "#RRGGBB" → opaque colour; anything else → 0 (the style's own). */
    fun parseColor(s: String?): Int {
        val hex = s?.trim()?.removePrefix("#") ?: return 0
        if (hex.length != 6) return 0
        return hex.toIntOrNull(16)?.let { it or OPAQUE } ?: 0
    }

    fun encodeColor(c: Int): String = if (c == 0) "" else String.format("#%06X", c and 0xFFFFFF)

    fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).toInt() and 0xFF
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private const val OPAQUE = 0xFF000000.toInt()
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
