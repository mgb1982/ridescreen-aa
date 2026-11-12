// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Runs [ZeroToSpeed] on a main-thread Handler: 10 Hz with OBD speed, one speed sample a second with GPS
 * (the clock still ticks at 10 Hz so the count is smooth). It only ticks while a run is in progress or its
 * result is on the dash; ARMED and the finished state cost nothing.
 */
internal object ChronoController {
    private const val TICK_MS = 100L
    private const val GPS_SAMPLE_MS = 1_000L
    /** Armed and waiting: a slow tick is enough to notice the bike moving off or the armed timeout. */
    private const val ARMED_TICK_MS = 1_000L

    @Volatile var state: ChronoState = ChronoState()
        private set
    /** Set after a run that beat a record: (first target, second target). Cleared on the next arm. */
    @Volatile var lastRecords: Pair<Boolean, Boolean> = false to false
        private set
    @Volatile var gpsSource: Boolean = false
        private set
    @Volatile var onChange: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var lastSampleMs = 0L
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            val ctx = app ?: return
            val now = SystemClock.elapsedRealtime()
            val d = DashboardMode.collect(ctx)
            gpsSource = d.speedFromGps
            val speed = d.speedKmh?.takeIf { !d.speedFromGps || now - lastSampleMs >= GPS_SAMPLE_MS }
            if (speed != null) lastSampleMs = now
            val before = state
            val after = ZeroToSpeed.step(before, speed, now)
            state = after
            if (before.phase != ChronoPhase.DONE && after.phase == ChronoPhase.DONE) finished(ctx, after)
            if (after != before) onChange?.invoke()
            if (needsTick(after)) handler.postDelayed(this, if (after.phase == ChronoPhase.ARMED) ARMED_TICK_MS else TICK_MS) else ticking = false
        }
    }

    private fun needsTick(s: ChronoState) = when (s.phase) {
        ChronoPhase.ARMED, ChronoPhase.COUNTDOWN, ChronoPhase.WAITING_LAUNCH, ChronoPhase.RUNNING -> true
        ChronoPhase.DONE, ChronoPhase.CANCELLED -> s.dashVisible
        else -> false
    }

    private fun ensureTicking() {
        if (ticking) return
        ticking = true
        handler.post(tick)
    }

    private fun currentSpeed(ctx: Context): Double? = DashboardMode.collect(ctx).also { gpsSource = it.speedFromGps }.speedKmh

    /** @return null when armed, else the string-resource id of why not. */
    fun arm(ctx: Context): Int? {
        app = ctx.applicationContext
        val speed = currentSpeed(ctx)
        if (speed == null) return R.string.chr_no_speed
        if (!ZeroToSpeed.canArm(state, speed)) return R.string.chr_not_stopped
        lastRecords = false to false
        state = ZeroToSpeed.arm(state, speed, Fmt.imperial(ctx), SystemClock.elapsedRealtime())
        LogBus.log("[CHRONO] armed")
        onChange?.invoke()
        ensureTicking()
        return null
    }

    fun go(ctx: Context): Int? {
        app = ctx.applicationContext
        val speed = currentSpeed(ctx)
        val next = ZeroToSpeed.go(state, speed, SystemClock.elapsedRealtime())
        if (next == state) return if (speed == null) R.string.chr_no_speed else R.string.chr_not_stopped
        state = next
        lastSampleMs = SystemClock.elapsedRealtime()
        LogBus.log("[CHRONO] countdown")
        ensureTicking()
        onChange?.invoke()
        return null
    }

    /** Cancel a run in progress; from ARMED, or from a finished/cancelled state, go back to IDLE. */
    fun cancel() {
        val s = state
        state = when (s.phase) {
            ChronoPhase.COUNTDOWN, ChronoPhase.WAITING_LAUNCH, ChronoPhase.RUNNING ->
                ZeroToSpeed.cancel(s, SystemClock.elapsedRealtime())
            else -> ZeroToSpeed.reset()
        }
        LogBus.log("[CHRONO] cancelled")
        onChange?.invoke()
        if (needsTick(state)) ensureTicking()
    }

    private fun finished(ctx: Context, s: ChronoState) {
        val run = ChronoRun(System.currentTimeMillis(), s.imperial, gpsSource, s.t1, s.t2)
        lastRecords = ChronoStore.add(ctx, run)
        LogBus.log("[CHRONO] done t1=${s.t1} t2=${s.t2} records=$lastRecords")
        if (lastRecords.first || lastRecords.second) {
            try {
                ctx.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
        }
    }

    /** Part of the dash redraw signature: changes with the phase, the digit and the shown times. */
    fun signature(): String {
        val s = state
        return if (ChronoOverlay.active(s)) "${s.phase}|${s.count}|${s.t1?.let { Math.round(it * 100) }}|${s.t2?.let { Math.round(it * 100) }}" else "-"
    }

    /** Draws the overlay on [bmp] when the timer wants the dash; returns [bmp]. */
    fun overlay(ctx: Context, bmp: android.graphics.Bitmap): android.graphics.Bitmap {
        val s = state
        if (!ChronoOverlay.active(s)) return bmp
        val r = ctx.resources
        ChronoOverlay.draw(
            android.graphics.Canvas(bmp), bmp.width, bmp.height, s, s.imperial,
            ChronoLabels(r.getString(R.string.chr_state_armed), r.getString(R.string.chr_dash_go),
                r.getString(R.string.chr_state_running), r.getString(R.string.chr_state_cancelled)),
        )
        return bmp
    }
}
