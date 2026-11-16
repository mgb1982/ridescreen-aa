// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure rules for everything the live reader asks besides its nine dashboard PIDs (unit tested): which PIDs
 * go once per connection, which ones rotate (one per round), when a silent PID is dropped, and the
 * last / min / max bookkeeping behind the log lines. Nothing here reaches the dashboard or the snapshot.
 */
internal object ObdExtras {
    /** Once per connection, after the speed test: text PIDs. */
    val FIXED = listOf(0x01, 0x03, 0x13, 0x1C)
    /** Once per connection: counters. */
    val COUNTERS = listOf(0x21, 0x30, 0x31, 0x4D)
    /** Rotating while riding: exactly one of these per round. */
    val ROTATING = listOf(0x06, 0x07, 0x0B, 0x0E, 0x11, 0x14, 0x15, 0x1F, 0x2E)
    const val MAX_MISSES = 3

    /** Short log labels, in the order of the "extras" line (the first four are among the nine dashboard PIDs). */
    private val LABELS = listOf(
        0x04 to "load", 0x43 to "absLoad", 0x0F to "intake", 0x33 to "baro", 0x0B to "MAP", 0x0E to "timing",
        0x06 to "STFT", 0x07 to "LTFT", 0x11 to "absThrottle", 0x14 to "O2 B1S1", 0x15 to "O2 B1S2",
        0x1F to "runtime", 0x2E to "purge",
    )
    /** Second value of the O2 PIDs (fuel trim of the sensor) is stored under this key offset. */
    const val TRIM_KEY = 0x100

    /** The ECU's own list, filtered to what the ECU offers, in the order asked. */
    fun fixedFor(supported: Set<Int>) = FIXED.filter { it in supported }
    fun countersFor(supported: Set<Int>) = COUNTERS.filter { it in supported }

    /** The rotating PID for step [step] (a counter that grows by one per round), skipping unsupported or dead PIDs; null if none. */
    fun rotatingPid(supported: Set<Int>, dead: Set<Int>, step: Int): Int? {
        val l = ROTATING.filter { it in supported && it !in dead }
        return if (l.isEmpty()) null else l[Math.floorMod(step, l.size)]
    }

    /** Counts consecutive silent answers per PID; after [MAX_MISSES] the PID is dead for the rest of the connection. */
    class Misses {
        private val n = HashMap<Int, Int>()
        private val deadSet = HashSet<Int>()
        val dead: Set<Int> get() = deadSet
        /** Records an answer; true when this miss made the PID dead. */
        fun record(pid: Int, answered: Boolean): Boolean {
            if (answered) { n[pid] = 0; return false }
            val c = (n[pid] ?: 0) + 1
            n[pid] = c
            return c >= MAX_MISSES && deadSet.add(pid)
        }
    }

    /** Last, minimum and maximum of every value seen on one connection. */
    class Stats {
        class Range(var last: Double, var min: Double, var max: Double)
        private val m = java.util.TreeMap<Int, Range>()
        fun record(key: Int, v: Double) {
            val r = m[key]
            if (r == null) m[key] = Range(v, v, v) else { r.last = v; if (v < r.min) r.min = v; if (v > r.max) r.max = v }
        }
        fun last(key: Int): Double? = m[key]?.last
        fun range(key: Int): Range? = m[key]
        val isEmpty: Boolean get() = m.isEmpty()
        val keys: Set<Int> get() = m.keys
    }

    private fun num(v: Double, unit: String): String =
        (if (v % 1.0 == 0.0 || Math.abs(v) >= 100) "%.0f".format(java.util.Locale.ROOT, v) else "%.1f".format(java.util.Locale.ROOT, v)) + unit

    private fun unitOf(key: Int) = when (key) { 0x14, 0x15 -> "V"; else -> ObdDecode.pid(key)?.unit.orEmpty() }
    private fun unitGlued(key: Int) = unitOf(key).let { if (it == "%" || it == "°C" || it == "°" || it == "kPa" || it == "V") it else if (it.isEmpty()) "" else " $it" }
    private fun label(key: Int): String =
        if (key >= TRIM_KEY) (LABELS.firstOrNull { it.first == key - TRIM_KEY }?.second ?: "%02X".format(key - TRIM_KEY)) + " trim"
        else LABELS.firstOrNull { it.first == key }?.second ?: ObdDecode.pid(key)?.name ?: "%02X".format(key)

    /** "[OBD] extras: …" body with the current value of every labelled PID that was read. */
    fun extrasLine(s: Stats): String? {
        val keys = LABELS.map { it.first } + LABELS.map { it.first + TRIM_KEY }
        val parts = keys.mapNotNull { k -> s.last(k)?.let { label(k) + " " + num(it, if (k >= TRIM_KEY) "%" else unitGlued(k)) } }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    /** One line with min–max of every PID read on the connection, the nine dashboard PIDs included. */
    fun rangesLine(s: Stats): String? {
        if (s.isEmpty) return null
        return s.keys.joinToString(" · ") { k ->
            val r = s.range(k)!!
            val u = if (k >= TRIM_KEY) "%" else unitGlued(k)
            label(k) + " " + (if (r.min == r.max) num(r.min, u) else num(r.min, "") + "–" + num(r.max, u))
        }
    }

    /** Fixed text PIDs and counters on one connect line. */
    fun connectLine(fixed: List<Pair<Int, String>>, counters: List<Pair<Int, Double>>): String? {
        val f = fixed.map { (p, t) -> (if (p == 0x01) "monitor" else if (p == 0x03) "fuel system" else if (p == 0x13) "O2 present" else "standard") + " " + t }
        val c = counters.map { (p, v) -> (ObdDecode.pid(p)?.name ?: "%02X".format(p)) + " " + num(v, unitGlued(p)) }
        val all = f + c
        return if (all.isEmpty()) null else all.joinToString(" · ")
    }

    /** Counts good RPM reads and rounds over a window, to log the real rate (not the theoretical one). */
    class Rate(startMs: Long) {
        private var t0 = startMs
        private var rpmOk = 0
        private var rounds = 0
        fun onRound(rpmOk: Boolean) { rounds++; if (rpmOk) this.rpmOk++ }
        fun due(nowMs: Long, everyMs: Long = 60_000L) = nowMs - t0 >= everyMs
        /** "rpm/speed X.X/s · round Y ms" for the window so far; restarts the window. */
        fun take(nowMs: Long, dashboardOn: Boolean): String {
            val secs = ((nowMs - t0).coerceAtLeast(1L)) / 1000.0
            val round = if (rounds == 0) 0L else (nowMs - t0) / rounds
            val s = "rpm/speed %.1f/s · round %d ms (dashboard %s)".format(java.util.Locale.ROOT, rpmOk / secs, round, if (dashboardOn) "on" else "off")
            t0 = nowMs; rpmOk = 0; rounds = 0
            return s
        }
    }
}
