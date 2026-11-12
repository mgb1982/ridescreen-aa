// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import java.util.Locale

/** 0-60 / 0-100 km/h (or 0-40 / 0-60 mph) timer, pure logic (no Android): the state machine and the history rules. */
internal enum class ChronoPhase { IDLE, ARMED, COUNTDOWN, WAITING_LAUNCH, RUNNING, DONE, CANCELLED }

internal enum class ChronoReason { NONE, MOVED, ROLLING, TIMEOUT, USER }

/**
 * @property count the digit on show during COUNTDOWN (3, 2, 1).
 * @property digitSinceMs when that digit appeared.
 * @property launchMs interpolated instant the speed crossed [ZeroToSpeed.LAUNCH_KMH].
 * @property t1 seconds from launch to the first target (60 km/h or 40 mph), null until crossed.
 * @property t2 seconds from launch to the second target (100 km/h or 60 mph).
 * @property dashVisible false once the 5 s result/notice on the dash has run out.
 */
internal data class ChronoState(
    val phase: ChronoPhase = ChronoPhase.IDLE,
    val imperial: Boolean = false,
    val count: Int = 0,
    val digitSinceMs: Long = 0L,
    val prevSpeed: Double? = null,
    val prevMs: Long = 0L,
    val launchMs: Double? = null,
    val t1: Double? = null,
    val t2: Double? = null,
    val reason: ChronoReason = ChronoReason.NONE,
    val endedAtMs: Long = 0L,
    val dashVisible: Boolean = false,
)

internal object ZeroToSpeed {
    const val STOPPED_KMH = 1.0       // arming and "Go" only below this
    const val LAUNCH_KMH = 2.0        // the clock starts when speed goes above this
    const val CANCEL_KMH = 5.0        // faster than this before the run began: cancelled
    const val COUNT_STEP_MS = 1_000L
    const val SHOW_RESULT_MS = 5_000L
    const val RUN_TIMEOUT_MS = 40_000L
    /** Armed and forgotten: the dash must not stay covered for ever. */
    const val ARMED_TIMEOUT_MS = 180_000L
    /** After GO, not leaving within this long cancels (the green GO would otherwise cover the dash). */
    const val WAIT_TIMEOUT_MS = 30_000L
    private const val KMH_PER_MPH = 1.609344

    fun target1Kmh(imperial: Boolean) = if (imperial) 40.0 * KMH_PER_MPH else 60.0
    fun target2Kmh(imperial: Boolean) = if (imperial) 60.0 * KMH_PER_MPH else 100.0
    fun target1Label(imperial: Boolean) = if (imperial) 40 else 60
    fun target2Label(imperial: Boolean) = if (imperial) 60 else 100

    fun canArm(state: ChronoState, speedKmh: Double?): Boolean =
        speedKmh != null && speedKmh < STOPPED_KMH &&
            (state.phase == ChronoPhase.IDLE || state.phase == ChronoPhase.DONE || state.phase == ChronoPhase.CANCELLED)

    /** ARMED only when stopped; otherwise the state comes back unchanged. [nowMs] > 0 starts the armed timeout. */
    fun arm(state: ChronoState, speedKmh: Double?, imperial: Boolean, nowMs: Long = 0L): ChronoState =
        if (canArm(state, speedKmh)) ChronoState(phase = ChronoPhase.ARMED, imperial = imperial, digitSinceMs = nowMs, dashVisible = true) else state

    /** The manual "Go": starts the 3-2-1 count, only from ARMED and still stopped. */
    fun go(state: ChronoState, speedKmh: Double?, nowMs: Long): ChronoState =
        if (state.phase == ChronoPhase.ARMED && speedKmh != null && speedKmh < STOPPED_KMH)
            state.copy(phase = ChronoPhase.COUNTDOWN, count = 3, digitSinceMs = nowMs, prevSpeed = speedKmh, prevMs = nowMs)
        else state

    fun cancel(state: ChronoState, nowMs: Long, reason: ChronoReason = ChronoReason.USER): ChronoState =
        if (state.phase == ChronoPhase.IDLE || state.phase == ChronoPhase.DONE || state.phase == ChronoPhase.CANCELLED) state
        else state.copy(phase = ChronoPhase.CANCELLED, reason = reason, endedAtMs = nowMs, dashVisible = true)

    fun reset(): ChronoState = ChronoState()

    /** Where the line y = [threshold] is crossed between two samples; the later one if the earlier is no help. */
    fun crossing(prevSpeed: Double?, prevMs: Long, speed: Double, nowMs: Long, threshold: Double): Double {
        if (prevSpeed == null || prevSpeed >= threshold || speed <= prevSpeed || nowMs <= prevMs) return nowMs.toDouble()
        val f = ((threshold - prevSpeed) / (speed - prevSpeed)).coerceIn(0.0, 1.0)
        return prevMs + f * (nowMs - prevMs)
    }

    /**
     * One tick. [speedKmh] is null when no new sample arrived (time still moves: the count goes on, the 5 s
     * result expires). Never produces a measurement before the count reached GO and the bike left standstill.
     */
    fun step(state: ChronoState, speedKmh: Double?, nowMs: Long): ChronoState = when (state.phase) {
        ChronoPhase.IDLE -> state
        ChronoPhase.ARMED -> stepArmed(state, speedKmh, nowMs)
        ChronoPhase.DONE, ChronoPhase.CANCELLED ->
            if (state.dashVisible && nowMs - state.endedAtMs >= SHOW_RESULT_MS) state.copy(dashVisible = false) else state
        ChronoPhase.COUNTDOWN -> stepCount(state, speedKmh, nowMs)
        ChronoPhase.WAITING_LAUNCH -> stepWaiting(state, speedKmh, nowMs)
        ChronoPhase.RUNNING -> stepRunning(state, speedKmh, nowMs)
    }

    /** Armed but not started: moving off (over [CANCEL_KMH]) or waiting too long disarms, so the dash is never left covered. */
    private fun stepArmed(s: ChronoState, speed: Double?, now: Long): ChronoState = when {
        speed != null && speed > CANCEL_KMH -> cancel(s, now, ChronoReason.MOVED)
        s.digitSinceMs > 0L && now - s.digitSinceMs > ARMED_TIMEOUT_MS -> cancel(s, now, ChronoReason.TIMEOUT)
        else -> s
    }

    private fun remember(s: ChronoState, speedKmh: Double?, nowMs: Long) =
        if (speedKmh == null) s else s.copy(prevSpeed = speedKmh, prevMs = nowMs)

    private fun stepCount(s: ChronoState, speed: Double?, now: Long): ChronoState {
        if (speed != null && speed > CANCEL_KMH) return cancel(s, now, ChronoReason.MOVED)
        var cur = remember(s, speed, now)
        while (now - cur.digitSinceMs >= COUNT_STEP_MS) {
            val next = cur.count - 1
            cur = if (next <= 0) cur.copy(phase = ChronoPhase.WAITING_LAUNCH, count = 0, digitSinceMs = cur.digitSinceMs + COUNT_STEP_MS)
            else cur.copy(count = next, digitSinceMs = cur.digitSinceMs + COUNT_STEP_MS)
            if (cur.phase != ChronoPhase.COUNTDOWN) break
        }
        return cur
    }

    private fun stepWaiting(s: ChronoState, speed: Double?, now: Long): ChronoState {
        if (now - s.digitSinceMs > WAIT_TIMEOUT_MS) return cancel(s, now, ChronoReason.TIMEOUT)
        if (speed == null) return s
        if (speed <= LAUNCH_KMH) return remember(s, speed, now)
        val prev = s.prevSpeed
        // Above the threshold with no earlier sample at standstill: it was already rolling, so no fair run.
        if (prev == null || prev > LAUNCH_KMH) return cancel(s, now, ChronoReason.ROLLING)
        val launch = crossing(prev, s.prevMs, speed, now, LAUNCH_KMH)
        return stepRunning(
            s.copy(phase = ChronoPhase.RUNNING, launchMs = launch, prevSpeed = LAUNCH_KMH, prevMs = launch.toLong()),
            speed, now,
        )
    }

    private fun stepRunning(s: ChronoState, speed: Double?, now: Long): ChronoState {
        val launch = s.launchMs ?: return cancel(s, now, ChronoReason.ROLLING)
        if (speed == null) {
            return if (now - launch > RUN_TIMEOUT_MS) cancel(s, now, ChronoReason.TIMEOUT) else s
        }
        var t1 = s.t1
        var t2 = s.t2
        if (t1 == null && speed >= target1Kmh(s.imperial)) {
            t1 = (crossing(s.prevSpeed, s.prevMs, speed, now, target1Kmh(s.imperial)) - launch) / 1000.0
        }
        if (t2 == null && speed >= target2Kmh(s.imperial)) {
            t2 = (crossing(s.prevSpeed, s.prevMs, speed, now, target2Kmh(s.imperial)) - launch) / 1000.0
        }
        val next = s.copy(t1 = t1, t2 = t2, prevSpeed = speed, prevMs = now)
        return when {
            t2 != null -> next.copy(phase = ChronoPhase.DONE, endedAtMs = now, dashVisible = true)
            now - launch > RUN_TIMEOUT_MS -> cancel(next, now, ChronoReason.TIMEOUT)
            else -> next
        }
    }

    /** "6.30" with the locale's decimal separator. */
    fun fmtTime(seconds: Double, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.2f", seconds)
}

/** One finished run; [t1]/[t2] null when that target was not reached (a run is only stored once t1 exists). */
internal data class ChronoRun(val atMs: Long, val imperial: Boolean, val gps: Boolean, val t1: Double?, val t2: Double?)

/** Pure history rules: newest first, at most [MAX] runs, records kept per unit system and target. */
internal object ChronoHistory {
    const val MAX = 20

    fun add(list: List<ChronoRun>, run: ChronoRun): List<ChronoRun> = (listOf(run) + list).take(MAX)

    /** Best time for target [second] (false = first) in the unit system, or null. */
    fun best(list: List<ChronoRun>, imperial: Boolean, second: Boolean): Double? =
        list.filter { it.imperial == imperial }.mapNotNull { if (second) it.t2 else it.t1 }.minOrNull()

    /** Targets (first, second) where [run] beats everything already in [before]. */
    fun newRecords(before: List<ChronoRun>, run: ChronoRun): Pair<Boolean, Boolean> {
        fun beats(t: Double?, old: Double?) = t != null && (old == null || t < old)
        return beats(run.t1, best(before, run.imperial, false)) to beats(run.t2, best(before, run.imperial, true))
    }
}
