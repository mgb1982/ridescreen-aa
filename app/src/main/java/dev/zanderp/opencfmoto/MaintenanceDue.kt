// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure pick of the most urgent logbook task for the dash welcome card (no Android imports).
 * Due soon = under [SOON_KM] km or [SOON_DAYS] days left; overdue = 0 or less left.
 */
object MaintenanceDue {
    const val SOON_KM = 300
    const val SOON_DAYS = 14
    private const val DAY_MS = 86_400_000L
    private const val MONTH_DAYS = 30.4375

    data class Task(val name: String, val everyKm: Int?, val everyMonths: Int?, val lastKm: Double?, val lastAtMs: Long?)

    enum class Kind { OVERDUE, KM_LEFT, DAYS_LEFT }

    /** [amount]: km or days left (KM_LEFT / DAYS_LEFT), or how far past due (OVERDUE, in [byDays] units). */
    data class Due(val name: String, val kind: Kind, val amount: Int, val byDays: Boolean = false)

    private class Cand(val due: Due, val score: Double, val raw: Double)

    /** The most urgent task, or null when nothing is overdue or close. Tasks without a last km/date are skipped. */
    fun pick(tasks: List<Task>, nowKm: Double?, nowMs: Long): Due? {
        var best: Cand? = null
        for (t in tasks) {
            val c = candidate(t, nowKm, nowMs) ?: continue
            val b = best
            if (b == null || c.score < b.score || (c.score == b.score && c.raw < b.raw)) best = c
        }
        return best?.due
    }

    private fun candidate(t: Task, nowKm: Double?, nowMs: Long): Cand? {
        var kmLeft: Double? = null
        var daysLeft: Double? = null
        if (t.everyKm != null && t.everyKm > 0 && t.lastKm != null && nowKm != null) kmLeft = t.lastKm + t.everyKm - nowKm
        if (t.everyMonths != null && t.everyMonths > 0 && t.lastAtMs != null) {
            daysLeft = (t.lastAtMs + t.everyMonths * MONTH_DAYS * DAY_MS - nowMs) / DAY_MS
        }
        val kmScore = kmLeft?.let { it / SOON_KM }
        val dayScore = daysLeft?.let { it / SOON_DAYS }
        val byKm = kmScore != null && (dayScore == null || kmScore <= dayScore)
        val score = (if (byKm) kmScore else dayScore) ?: return null
        if (score > 1.0) return null
        val left = (if (byKm) kmLeft else daysLeft)!!
        val due = when {
            left <= 0.0 -> Due(t.name, Kind.OVERDUE, Math.round(-left).toInt(), byDays = !byKm)
            byKm -> Due(t.name, Kind.KM_LEFT, Math.round(left).toInt())
            else -> Due(t.name, Kind.DAYS_LEFT, Math.round(left).toInt())
        }
        return Cand(due, score, left)
    }
}
