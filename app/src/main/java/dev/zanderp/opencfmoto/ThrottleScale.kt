// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Display scale for OBD PID 0x45 (relative throttle). On the 125X the value never passes ~65 % at full grip
 * (inferred: the ECU normalises to the sensor's 0-100 % range, the plate opens to ~68.6 % absolute), so the
 * shown value is raw * 100 / cap. The cap only goes up (peak hold), so a bike that really reaches 100 is not clipped for long.
 */
object ThrottleScale {
    const val DEFAULT_CAP = 65.0
    const val PREF_KEY = "throttle_cap"
    /** Minimum time between two saves of a raised cap. */
    const val SAVE_EVERY_MS = 10_000L

    fun scale(raw: Double, cap: Double): Double = (raw * 100.0 / cap).coerceIn(0.0, 100.0)

    fun nextCap(cap: Double, raw: Double): Double = if (raw > cap) raw.coerceAtMost(100.0) else cap
}

/** Running max of raw relative throttle (PID 0x45) over a window, with the absolute throttle (0x11) of the same round when it was read. */
class ThrottlePeak {
    class Result(val raw: Double, val abs: Double?)
    private var maxRaw: Double? = null
    private var absAtMax: Double? = null

    fun add(raw: Double, abs: Double?) {
        val m = maxRaw
        if (m == null || raw > m) { maxRaw = raw; absAtMax = abs }
    }

    /** The window's peak, or null if no sample was seen; resets the window. */
    fun take(): Result? {
        val m = maxRaw ?: return null
        val r = Result(m, absAtMax)
        maxRaw = null; absAtMax = null
        return r
    }

    companion object {
        fun line(r: Result, cap: Double): String =
            "throttle peak 60 s: raw ${"%.1f".format(java.util.Locale.ROOT, r.raw)}%, scaled ${"%.1f".format(java.util.Locale.ROOT, ThrottleScale.scale(r.raw, cap))}%, cap ${"%.1f".format(java.util.Locale.ROOT, cap)}" +
                (r.abs?.let { ", abs ${"%.1f".format(java.util.Locale.ROOT, it)}" } ?: "")
    }
}
