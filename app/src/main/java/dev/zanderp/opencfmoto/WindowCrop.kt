// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

/** Choices for how much of the Android Auto window's bottom (its shortcut bar) is hidden. Pure, JVM-testable. */
object WindowCrop {
    const val DEFAULT = AaWindowLayout.DEFAULT_BOTTOM_CROP_PCT
    /** Off, Small, Medium (default), Large. */
    val OPTIONS: List<Int> = listOf(0, 15, 25, 35)

    fun coerce(pct: Int): Int = pct.coerceIn(0, AaWindowLayout.MAX_BOTTOM_CROP_PCT)

    /** Dialog index of the option closest to [pct] (a stored value is always one of OPTIONS, but be safe). */
    fun indexOf(pct: Int): Int = OPTIONS.indices.minByOrNull { kotlin.math.abs(OPTIONS[it] - pct) } ?: 0

    /** Log line written once when the fine-tune dialog closes. */
    fun logLine(pct: Int): String = "[DASHBOARD] AA window crop = ${coerce(pct)} %"
}
