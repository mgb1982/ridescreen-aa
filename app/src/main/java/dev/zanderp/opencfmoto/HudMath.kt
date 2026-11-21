// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure numbers behind the HUD video style (beating heart, lean peak, record flashes, highlight banners). */
object HudMath {
    /** 0..1 heartbeat shape at [tMs] for [bpm]: a strong beat then a weaker one, then rest. 0 when there is no pulse. */
    fun beat(tMs: Long, bpm: Int?): Float {
        if (bpm == null || bpm < 30 || bpm > 220) return 0f
        val period = 60_000.0 / bpm
        val ph = ((tMs % period.toLong().coerceAtLeast(1L)).toDouble() / period).toFloat().coerceIn(0f, 1f)
        return when {
            ph < 0.18f -> sin(ph / 0.18f * PI).toFloat()
            ph in 0.28f..0.42f -> 0.5f * sin((ph - 0.28f) / 0.14f * PI).toFloat()
            else -> 0f
        }
    }

    /** The lean (signed) with the biggest angle among the samples in the [windowMs] before [tMs]; null if none. */
    fun peakLean(samples: List<MotionTripSample>, tMs: Long, windowMs: Long = 10_000L): Double? {
        var best: Double? = null
        for (s in samples) {
            if (s.timeMs > tMs) break
            if (s.timeMs < tMs - windowMs) continue
            val l = s.leanDeg ?: continue
            if (best == null || abs(l) > abs(best)) best = l
        }
        return best
    }

    /** Times where the lean beats everything before it by 1° or more (and is at least [minDeg]). */
    fun leanRecords(samples: List<MotionTripSample>, minDeg: Double = 15.0): List<Long> {
        val out = ArrayList<Long>()
        var top = 0.0
        for (s in samples) {
            val l = abs(s.leanDeg ?: continue)
            if (l >= minDeg && l >= top + 1.0) out += s.timeMs
            if (l > top) top = l
        }
        return out
    }

    /** 1 right after the latest record before [tMs], fading to 0 over [durMs]. */
    fun flash(records: List<Long>, tMs: Long, durMs: Long = 1_200L): Float {
        var last = -1L
        for (r in records) { if (r <= tMs) last = r else break }
        if (last < 0) return 0f
        val age = tMs - last
        return if (age in 0 until durMs) 1f - age.toFloat() / durMs else 0f
    }

    enum class Kind { TOP_SPEED, MAX_LEAN, MAX_G }
    data class Banner(val atMs: Long, val kind: Kind, val value: Double, val right: Boolean = false)

    /** The ride's peaks, worth a short banner when the video passes them. Small peaks are left out. */
    fun banners(
        speedKmh: List<Pair<Long, Double>>, motion: List<MotionTripSample>,
        minKmh: Double = 40.0, minLean: Double = 15.0, minG: Double = 0.4,
    ): List<Banner> {
        val out = ArrayList<Banner>()
        speedKmh.maxByOrNull { it.second }?.takeIf { it.second >= minKmh }?.let { out += Banner(it.first, Kind.TOP_SPEED, it.second) }
        motion.filter { it.leanDeg != null }.maxByOrNull { abs(it.leanDeg!!) }?.takeIf { abs(it.leanDeg!!) >= minLean }
            ?.let { out += Banner(it.timeMs, Kind.MAX_LEAN, abs(it.leanDeg!!), right = it.leanDeg > 0) }
        motion.mapNotNull { s -> combinedG(s)?.let { s.timeMs to it } }.maxByOrNull { it.second }?.takeIf { it.second >= minG }
            ?.let { out += Banner(it.first, Kind.MAX_G, it.second) }
        return out.sortedBy { it.atMs }
    }

    fun combinedG(s: MotionTripSample): Double? =
        if (s.gLat == null && s.gLon == null) null else sqrt((s.gLat ?: 0.0).let { it * it } + (s.gLon ?: 0.0).let { it * it })

    /** Banner opacity at [tMs]: 0.4 s before the peak to 2.6 s after, with 0.3 s fades. */
    fun bannerAlpha(b: Banner, tMs: Long): Float {
        val start = b.atMs - 400L; val end = b.atMs + 2_600L
        if (tMs < start || tMs > end) return 0f
        val fade = 300f
        return minOf(1f, (tMs - start) / fade, (end - tMs) / fade).coerceIn(0f, 1f)
    }

    /** Lit segments of an [n]-segment bar for [value] of [max]. */
    fun litSegments(value: Double, max: Double, n: Int = 20): Int =
        if (max <= 0.0) 0 else Math.round((value / max).coerceIn(0.0, 1.0) * n).toInt()
}
