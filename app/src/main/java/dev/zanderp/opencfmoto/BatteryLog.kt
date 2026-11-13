// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure format + maths for the battery MEASUREMENT log lines (no Android imports, unit tested). Only
 * numbers and fixed words go in: no MAC, McuID, PKE code, QR or password can reach these lines.
 * A battery level below 0 means "unknown" (no sticky battery intent) and prints as `?`.
 */
object BatteryLog {
    const val VIDEO_EVERY_MS = 30_000L
    const val PROJECTING_EVERY_MS = 10 * 60_000L
    const val PARKED_AT_MS = 25 * 60_000L

    /** Frames the encoder produced in a window → whole frames per second (0 for a bad window). */
    fun fps(framesDelta: Long, windowMs: Long): Int {
        if (framesDelta <= 0L || windowMs <= 0L) return 0
        return Math.round(framesDelta * 1000.0 / windowMs).toInt()
    }

    fun videoLine(fps: Int, capFps: Int, mode: String): String =
        "[VIDEO] encoded fps=$fps (cap ${capFps}fps, mode $mode)"

    /** True when [intervalMs] has passed since [lastAtMs] (also if the clock went backwards). */
    fun due(lastAtMs: Long, nowMs: Long, intervalMs: Long): Boolean =
        nowMs < lastAtMs || nowMs - lastAtMs >= intervalMs

    /** `45s`, `20m`, `1h05m`. */
    fun duration(ms: Long): String {
        val s = (if (ms < 0) 0L else ms) / 1000
        if (s < 60) return "${s}s"
        val m = s / 60
        if (m < 60) return "${m}m"
        return "${m / 60}h${(m % 60).toString().padStart(2, '0')}m"
    }

    /** `(-4%)`, `(+1%)`, `(0%)`, or `(?)` if either level is unknown. */
    fun delta(startLevel: Int, level: Int): String {
        if (startLevel < 0 || level < 0) return "(?)"
        val d = level - startLevel
        return if (d > 0) "(+$d%)" else "($d%)"
    }

    /**
     * [label]: `projecting`, `parked`, `parked-wait`, `abandoned` or `ended`. [sinceMs] counts from the
     * start of the projection (or from parking for the parked-wait lines). Example:
     * `[PWR] projecting 20m level=83% (-4%) charging=no`.
     */
    fun pwrLine(label: String, sinceMs: Long, startLevel: Int, level: Int, charging: Boolean): String {
        val lv = if (level < 0) "?" else "$level%"
        return "[PWR] $label ${duration(sinceMs)} level=$lv ${delta(startLevel, level)} charging=${if (charging) "yes" else "no"}"
    }

    /** Battery percent from the sticky intent extras; -1 when missing. */
    fun percent(level: Int, scale: Int): Int =
        if (level < 0 || scale <= 0) -1 else (level * 100 / scale).coerceIn(0, 100)
}
