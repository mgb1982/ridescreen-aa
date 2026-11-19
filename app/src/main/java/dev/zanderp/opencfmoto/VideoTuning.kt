// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock

/*
 * v2.5 · video encoder tuning (branch feature/video-encoder-tuning). Four independent options, each with
 * its own switch in Setup ▸ Video tuning; everything here is pure (unit-tested) except [HeatGuard].
 *
 *  1. heat cap          — a ceiling on bitrate and fps from the phone's thermal state, on top of the adaptive control
 *  2. repeat-100 ms     — the encoder repeats the last picture every 100 ms on a still screen
 *  3. loss split        — link down / link saturated / dash re-attached are counted apart; only "saturated" may
 *                         lower the bitrate (audit A2)
 *  4. intra-refresh     — lab option: periodic intra refresh instead of big periodic keyframes
 */

/** Option 1 · what the phone's heat allows. Same table AUTO already uses, now also for the fixed power modes. */
object HeatPolicy {
    const val SAMPLE_EVERY_MS = 30_000L

    /** [maxFps] null = no cap. */
    data class Cap(val bitrateFactor: Float, val maxFps: Int?) {
        val active: Boolean get() = bitrateFactor < 1f || maxFps != null
    }

    val NONE = Cap(1.0f, null)

    fun capFor(thermalStatus: Int): Cap = when {
        thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> Cap(0.5f, 12)
        thermalStatus == PowerManager.THERMAL_STATUS_SEVERE -> Cap(0.6f, 15)
        thermalStatus == PowerManager.THERMAL_STATUS_MODERATE -> Cap(0.8f, 20)
        else -> NONE
    }

    /** The lower of what the adaptive control wants and what the heat allows; never below the project's floor. */
    fun clampBitrate(wanted: Int, base: Int, cap: Cap): Int {
        if (!cap.active || base <= 0) return wanted
        val ceiling = (base * cap.bitrateFactor).toInt()
            .coerceIn(AdaptivePolicy.MIN_BITRATE.coerceAtMost(base), base)
        return minOf(wanted, ceiling)
    }

    fun clampFps(wanted: Int, cap: Cap): Int = cap.maxFps?.let { minOf(wanted, it) } ?: wanted

    fun thermalName(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> "?$status"
    }

    fun pluggedName(plugged: Int): String = when {
        plugged <= 0 -> "no"
        plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "ac"
        plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "usb"
        plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "wireless"
        else -> "other"
    }

    /** `[PWR] heat thermal=moderate headroom=0.85 battery=38.5°C plugged=no` (n/a where Android says nothing). */
    fun line(thermalStatus: Int, headroom: Float, batteryTenthsC: Int?, plugged: Int): String {
        val h = if (headroom.isNaN()) "n/a" else String.format(java.util.Locale.US, "%.2f", headroom)
        val t = if (batteryTenthsC == null) "n/a" else String.format(java.util.Locale.US, "%.1f°C", batteryTenthsC / 10f)
        return "[PWR] heat thermal=${thermalName(thermalStatus)} headroom10s=$h battery=$t plugged=${pluggedName(plugged)}"
    }

    fun capLine(cap: Cap, thermalStatus: Int): String =
        if (!cap.active) "[PWR] heat cap released (thermal=${thermalName(thermalStatus)})"
        else "[PWR] heat cap → bitrate ≤${(cap.bitrateFactor * 100).toInt()}% fps ≤${cap.maxFps ?: "-"} " +
            "(thermal=${thermalName(thermalStatus)})"
}

/** Option 1 · reads the heat every 30 s, logs it, and holds the current [cap]. Runs on the service watchdog tick. */
class HeatGuard(private val context: Context, private val log: (String) -> Unit) {
    private val powerManager by lazy {
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private var lastSampleAt = 0L
    private var sampled = false
    private var status = PowerManager.THERMAL_STATUS_NONE

    /** What the heat allows right now (NONE while the option is off). */
    @Volatile var cap: HeatPolicy.Cap = HeatPolicy.NONE
        private set

    fun reset() {
        sampled = false
        cap = HeatPolicy.NONE
    }

    fun tick(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (sampled && !BatteryLog.due(lastSampleAt, nowMs, HeatPolicy.SAMPLE_EVERY_MS)) return
        sampled = true
        lastSampleAt = nowMs
        status = try { powerManager.currentThermalStatus } catch (_: Exception) { PowerManager.THERMAL_STATUS_NONE }
        val headroom = if (Build.VERSION.SDK_INT >= 30) {
            try { powerManager.getThermalHeadroom(10) } catch (_: Exception) { Float.NaN }
        } else Float.NaN
        val battery = try {
            context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) { null }
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        log(HeatPolicy.line(status, headroom, temp, plugged))

        val wanted = if (AppSettings.heatCap(context)) HeatPolicy.capFor(status) else HeatPolicy.NONE
        if (wanted != cap) {
            cap = wanted
            log(HeatPolicy.capLine(wanted, status))
        }
    }
}

/**
 * Sizes of the encoded frames over one window (30 s), to compare the encoder set-ups (keyframes every second vs
 * intra-refresh): one short log line per window. Used from the encoder drain thread only.
 */
class FrameSizeStats(private val windowMs: Long = 30_000L) {
    private var started = false
    private var startedAt = 0L
    private var n = 0
    private var sum = 0L
    private var max = 0
    private var keyMax = 0

    fun add(size: Int, key: Boolean) {
        n++; sum += size
        if (size > max) max = size
        if (key && size > keyMax) keyMax = size
    }

    /** The line for the window that just ended (and a fresh window), or null while it is still running / empty. */
    fun take(nowMs: Long, intraRefresh: Boolean): String? {
        if (!started) { started = true; startedAt = nowMs; return null }
        if (nowMs - startedAt < windowMs) return null
        val line = if (n == 0) null else
            "[VIDEO] frame sizes ${(nowMs - startedAt) / 1000}s: n=$n avg=${sum / n}B max=${max}B keyframe-max=${keyMax}B " +
                "intra-refresh=${if (intraRefresh) "on" else "off"}"
        startedAt = nowMs; n = 0; sum = 0; max = 0; keyMax = 0
        return line
    }
}

/** Option 2 · how often the encoder repeats the last picture when nothing new arrives. */
object EncoderTuning {
    const val REPEAT_FAST_US = 100_000L
    /** Option off: the 0.5 s floor of the mirror fix (fix/mirror-repeat-frame), ≈2 fps on a still picture. */
    const val REPEAT_STILL_US = 500_000L
    /** Battery saver and all-intra streams: ≈1 fps on a still picture. */
    const val REPEAT_SLOW_US = 900_000L

    /**
     * 100 ms keeps a picture arriving on a still screen (350E mirror, task 43). With the option off it is
     * 0.5 s (the mirror fix); in Battery saver (that mode's floor on a fixed picture is ~1 fps by design) and
     * for an all-intra stream (every repeat would be a full keyframe) it stays 0.9 s.
     */
    fun repeatAfterUs(fastOn: Boolean, saver: Boolean, keyframeIntervalSec: Int): Long = when {
        keyframeIntervalSec <= 0 -> REPEAT_SLOW_US      // all-intra: every repeat would be a full keyframe
        saver -> REPEAT_SLOW_US                          // Battery saver keeps its ~1 fps floor on a fixed picture
        fastOn -> REPEAT_FAST_US
        else -> REPEAT_STILL_US
    }

    /** Option 4 · true when intra-refresh should be configured: the lab option is on and the codec supports it. */
    fun useIntraRefresh(optionOn: Boolean, codecSupports: Boolean): Boolean = optionOn && codecSupports

    const val INTRA_REFRESH_KEYFRAME_SEC = 10
}

/** Option 3 · what a full encoder queue means. */
enum class LossKind { LINK_DOWN, SATURATED }

object LossPolicy {
    /**
     * The encoder output did not fit in the queue. Link down (nobody pulling, or Android already told us the
     * bike Wi-Fi is gone) is not congestion; only a link that is alive but too slow is "saturated".
     * [useLinkSignal] false reproduces the old rule (idle ≥ 2 s counts as down, everything else as drops).
     */
    fun classify(linkPaused: Boolean, linkKnownDown: Boolean, msSinceLastPull: Long, useLinkSignal: Boolean): LossKind = when {
        linkPaused -> LossKind.LINK_DOWN
        useLinkSignal && linkKnownDown -> LossKind.LINK_DOWN
        // msSinceLastPull <= 0 means the dash never pulled yet: not idle, just not started.
        msSinceLastPull >= LinkIdle.IDLE_MS -> LossKind.LINK_DOWN
        else -> LossKind.SATURATED
    }

    /** Only saturation may lower the bitrate (AIMD in [AdaptivePolicy]). */
    fun countsTowardCongestion(kind: LossKind) = kind == LossKind.SATURATED
}

/**
 * Option 3 · the three counters of a session. "Rejected" is the dash re-opening its video client while the
 * link was alive and it had been pulling: our protocol has no explicit reject message, so this is the
 * observable proxy. Pure, driven by the pipeline.
 */
class LossCounters {
    @Volatile var linkDown = 0L; private set
    @Volatile var saturated = 0L; private set
    @Volatile var rejected = 0L; private set
    @Volatile var served = 0L; private set
    private var summarised = false

    /** The summary line once per session (stop() can run more than once); null if nothing to say or already said. */
    fun summaryOnce(): String? {
        if (summarised || (!any && served == 0L)) return null
        summarised = true
        return summary()
    }
    private var reattachAt = 0L
    private var recoveries = 0
    private var recoveryTotalMs = 0L
    private var recoveryMaxMs = 0L

    fun onLinkDown(frames: Long = 1L) { linkDown += frames }
    fun onSaturated(frames: Long) { saturated += frames }

    /** The dash re-attached its video client. [alive]: it was pulling within the last 2 s and the pipeline wasn't paused. */
    fun onReattach(nowMs: Long, alive: Boolean) {
        if (served > 0L && alive) { rejected++; reattachAt = nowMs }
    }

    /** A frame was handed to the dash. Returns the recovery time in ms the first time after a re-attach, else null. */
    fun onServed(nowMs: Long): Long? {
        served++
        if (reattachAt == 0L) return null
        val ms = (nowMs - reattachAt).coerceAtLeast(0L)
        reattachAt = 0L
        recoveries++; recoveryTotalMs += ms; if (ms > recoveryMaxMs) recoveryMaxMs = ms
        return ms
    }

    val any: Boolean get() = linkDown + saturated + rejected > 0L

    fun summary(): String {
        val rec = if (recoveries == 0) "" else " (re-attach recovery avg ${recoveryTotalMs / recoveries} ms, max $recoveryMaxMs ms)"
        return "[VIDEO] session loss summary: link-down=$linkDown saturated=$saturated rejected=$rejected$rec"
    }
}
