// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Pure rules of the durable on-disk log (file names, retention, share selection, APPSTATUS dedupe). */
object LogStoreLogic {
    const val KEEP_DAYS = 7
    const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
    const val SHARE_CAP_BYTES = 8L * 1024 * 1024
    private val NAME = Regex("""log-(\d{8})\.txt""")

    data class Entry(val name: String, val size: Long)

    /** `log-YYYYMMDD.txt` for the local day of [millis] in [tz]. */
    fun fileName(millis: Long, tz: TimeZone = TimeZone.getDefault()): String =
        "log-" + dayKey(millis, tz) + ".txt"

    fun dayKey(millis: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }.format(Date(millis))

    /** The yyyyMMdd of a log file name, or null if it is not one of ours. */
    fun dayOf(name: String): String? = NAME.matchEntire(name)?.groupValues?.get(1)

    fun prettyDay(key: String): String = "${key.substring(0, 4)}-${key.substring(4, 6)}-${key.substring(6, 8)}"

    /** Day key [days] days before [key] (calendar arithmetic in [tz]). */
    fun minusDays(key: String, days: Int, tz: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
        val c = java.util.Calendar.getInstance(tz)
        c.time = f.parse(key) ?: return key
        c.add(java.util.Calendar.DAY_OF_MONTH, -days)
        return f.format(c.time)
    }

    /**
     * Files to delete: older than [keepDays] days (today counts as day 1), then the oldest ones until the
     * total fits [maxBytes]. The newest file is never deleted for size (it is being written).
     */
    fun toPrune(
        files: List<Entry>, todayKey: String, keepDays: Int = KEEP_DAYS, maxBytes: Long = MAX_TOTAL_BYTES,
        tz: TimeZone = TimeZone.getDefault(),
    ): List<String> {
        val ours = files.filter { dayOf(it.name) != null }.sortedBy { it.name }
        val oldestKept = minusDays(todayKey, keepDays - 1, tz)
        val out = ArrayList<String>()
        val rest = ArrayList<Entry>()
        for (e in ours) if (dayOf(e.name)!! < oldestKept) out.add(e.name) else rest.add(e)
        var total = rest.sumOf { it.size }
        var i = 0
        while (total > maxBytes && i < rest.size - 1) { total -= rest[i].size; out.add(rest[i].name); i++ }
        return out
    }

    /** Names for Share (oldest first): the previous day that has a file, then today's. */
    fun shareFiles(names: List<String>, todayKey: String): List<String> {
        val ours = names.filter { dayOf(it) != null }.sorted()
        val today = ours.filter { dayOf(it) == todayKey }
        val prev = ours.lastOrNull { dayOf(it)!! < todayKey }
        return listOfNotNull(prev) + today
    }

    /**
     * Given the sizes of the share files (oldest first) and a [budget] of bytes, how many leading bytes of
     * each to skip so the newest data is kept. A nonzero skip means "write the ellipsis marker first".
     */
    fun skipBytes(sizes: List<Long>, budget: Long): List<Long> {
        var left = budget.coerceAtLeast(0L)
        val skips = MutableList(sizes.size) { 0L }
        for (i in sizes.indices.reversed()) {
            val keep = minOf(sizes[i], left)
            skips[i] = sizes[i] - keep
            left -= keep
        }
        return skips
    }

    /** `[LOGS] on disk: <n> file(s), <KB> KB, oldest <yyyy-mm-dd>` */
    fun startupLine(files: List<Entry>): String {
        val ours = files.filter { dayOf(it.name) != null }
        val oldest = ours.minOfOrNull { dayOf(it.name)!! }?.let { prettyDay(it) } ?: "-"
        return "[LOGS] on disk: ${ours.size} file(s), ${ours.sumOf { it.size } / 1024} KB, oldest $oldest"
    }

    /** Logs an APPSTATUS line only when its payload changed (counter ignored) or after [heartbeatMs]. */
    class AppStatusDedupe(private val heartbeatMs: Long = 10 * 60_000L) {
        private var last: String? = null
        private var lastAt = 0L
        @Synchronized fun shouldLog(payload: String, nowMs: Long): Boolean {
            val key = payload.replace(Regex("""#\d+"""), "#")
            if (key == last && nowMs - lastAt < heartbeatMs) return false
            last = key; lastAt = nowMs
            return true
        }
        @Synchronized fun reset() { last = null; lastAt = 0L }
    }
}
