// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure file housekeeping of the black box (unit tested).
 *
 * Field log 16 Nov 2026: five bumps of 4.2-5.6 g in seven minutes, all followed by the rider moving on
 * ("moving"), were each saved, so a bad road could push real events out of the last [keep]. Bumps that end
 * with the rider moving now go to their own small pool ([KEEP_MOVING], file suffix `_m`) and never evict
 * the other events. The crash detector's threshold is not touched.
 */
object BlackBoxPrune {
    const val KEEP_MOVING = 3

    fun fileName(impactAtMs: Long, outcome: String): String =
        if (outcome == "moving") "bb_${impactAtMs}_m.json" else "bb_$impactAtMs.json"

    fun isMoving(name: String) = name.endsWith("_m.json")

    /** Which of [names] to delete: oldest beyond [keep] among the other events, beyond [KEEP_MOVING] among "moving". */
    fun toDelete(names: List<String>, keep: Int): List<String> {
        val events = names.filter { it.startsWith("bb_") && it.endsWith(".json") }
        val (moving, others) = events.partition { isMoving(it) }
        fun at(n: String) = n.removePrefix("bb_").removeSuffix(".json").removeSuffix("_m").toLongOrNull() ?: 0L
        return others.sortedByDescending { at(it) }.drop(keep) + moving.sortedByDescending { at(it) }.drop(KEEP_MOVING)
    }
}
