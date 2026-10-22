// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.PowerManager

/**
 * Pure adaptation math for [PowerMode.AUTO] (no Android dependencies, so it is unit-testable).
 *
 * Two independent pressures pull the video quality down, and the more aggressive one wins:
 *  - **Thermal** (idea 1): the phone's `PowerManager` thermal status maps to a bitrate factor and an
 *    fps cap. This is a safety response — a hot phone stutters and eventually thermal-throttles the
 *    encoder/Wi-Fi, so we pre-emptively shed load and keep the dash smooth-at-a-lower-rate.
 *  - **Link** (idea 2): sustained encoded-frame drops mean the bike can't pull as fast as we encode
 *    (Wi-Fi congestion / distance). We back the bitrate off multiplicatively and recover it additively
 *    (AIMD, like TCP), so marginal links degrade to a softer picture instead of hitting the bike's
 *    media-socket timeout and dropping the whole projection.
 *
 * The two combine on bitrate by taking the smaller factor; fps is driven by thermal only (the link
 * fix is fewer bits per frame, not fewer frames).
 */
data class AdaptiveDecision(val bitrate: Int, val fps: Int, val linkFactor: Float)

object AdaptivePolicy {
    /** AIMD bounds for the link factor. */
    const val LINK_MIN = 0.4f          // never shed more than 60% for link reasons alone
    const val LINK_MAX = 1.0f          // recover up to (never past) the user's target
    const val LINK_DECREASE = 0.8f     // multiplicative back-off on a congested tick
    const val LINK_INCREASE_STEP = 0.05f // additive recovery on a clean tick

    /** Encoded-frame drops within one tick window at/above which the link counts as congested. */
    const val DROP_CONGESTION_THRESHOLD = 8

    /** Absolute bitrate floor (bps) — below this the map is too soft to be useful, so we stop shedding. */
    const val MIN_BITRATE = 600_000

    /** Bitrate multiplier for a given `PowerManager` thermal status (THERMAL_STATUS_*). */
    fun thermalBitrateFactor(status: Int): Float = when {
        status <= PowerManager.THERMAL_STATUS_LIGHT -> 1.0f     // NONE / LIGHT: no throttle
        status == PowerManager.THERMAL_STATUS_MODERATE -> 0.8f
        status == PowerManager.THERMAL_STATUS_SEVERE -> 0.6f
        else -> 0.5f                                            // CRITICAL / EMERGENCY / SHUTDOWN
    }

    /** fps cap for a given thermal status, never above the mode's starting [baseFps]. */
    fun thermalFpsCap(status: Int, baseFps: Int): Int = when {
        status <= PowerManager.THERMAL_STATUS_LIGHT -> baseFps
        status == PowerManager.THERMAL_STATUS_MODERATE -> minOf(baseFps, 20)
        status == PowerManager.THERMAL_STATUS_SEVERE -> minOf(baseFps, 15)
        else -> minOf(baseFps, 12)
    }

    /** AIMD step: shrink hard on congestion, grow gently when clean. */
    fun nextLinkFactor(prev: Float, dropsThisTick: Int): Float =
        if (dropsThisTick >= DROP_CONGESTION_THRESHOLD)
            (prev * LINK_DECREASE).coerceAtLeast(LINK_MIN)
        else
            (prev + LINK_INCREASE_STEP).coerceAtMost(LINK_MAX)

    /**
     * Compute the next decision from the current inputs and the previous link factor. Deterministic
     * and side-effect-free — the controller owns the state, this owns the arithmetic.
     */
    fun decide(
        baseBitrate: Int,
        baseFps: Int,
        thermalStatus: Int,
        prevLinkFactor: Float,
        dropsThisTick: Int,
    ): AdaptiveDecision {
        val link = nextLinkFactor(prevLinkFactor, dropsThisTick)
        val factor = minOf(thermalBitrateFactor(thermalStatus), link)
        val bitrate = (baseBitrate * factor).toInt().coerceIn(MIN_BITRATE.coerceAtMost(baseBitrate), baseBitrate)
        val fps = thermalFpsCap(thermalStatus, baseFps)
        return AdaptiveDecision(bitrate, fps, link)
    }
}

/**
 * Runs [AdaptivePolicy] against the live [VideoPipeline] once per watchdog tick (see
 * [AndroidAutoService]). With [PowerMode.AUTO] it adapts bitrate and fps (thermal + link); in the
 * fixed power modes (v2.3) only the link back-off runs, which leaves a clean link untouched.
 */
class AdaptiveVideoController(
    private val context: Context,
    private val log: (String) -> Unit,
) {
    private val powerManager by lazy {
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private var linkFactor = AdaptivePolicy.LINK_MAX
    private var lastDropTotal = 0L
    private var appliedBitrate = -1
    private var appliedFps = -1
    /** True once we have adapted this session, so a switch away from AUTO can restore the fixed rate. */
    private var adapting = false
    /** The AUTO path (thermal fps cap included) ran this session. */
    private var autoAdapted = false

    /** New session (or resume): forget prior link state so we start fresh at the user's target. */
    fun reset() {
        linkFactor = AdaptivePolicy.LINK_MAX
        lastDropTotal = 0L
        appliedBitrate = -1
        appliedFps = -1
        adapting = false
        autoAdapted = false
    }

    /**
     * v2.3 · Fixed power modes: link back-off only (bitrate, never fps). A dash that keeps falling
     * behind — encoded frames dropped tick after tick, the grey-screen reports — gets fewer bits per
     * frame until it keeps up, then recovers. On a clean link nothing changes.
     */
    private fun linkOnly(pipe: VideoPipeline) {
        val base = pipe.targetBitrate()
        if (base <= 0) return
        val total = pipe.droppedFramesTotal()
        if (appliedBitrate == -1) {
            // First tick (new session, or just back from AUTO): take the drop counter as the baseline.
            lastDropTotal = total
            appliedBitrate = base
            return
        }
        val drops = (total - lastDropTotal).coerceAtLeast(0L).toInt()
        lastDropTotal = total
        linkFactor = AdaptivePolicy.nextLinkFactor(linkFactor, drops)
        val bitrate = (base * linkFactor).toInt().coerceIn(AdaptivePolicy.MIN_BITRATE.coerceAtMost(base), base)
        if (bitrate != appliedBitrate) {
            pipe.setEncoderBitrate(bitrate)
            appliedBitrate = bitrate
            log("[adaptive-link] drops/tick=$drops link=${pct(linkFactor)} → ${bitrate / 1000}kbps (power ${VideoPrefs.power(context).label})")
        }
    }

    fun onTick(pipeline: VideoPipeline?) {
        val pipe = pipeline ?: return

        if (VideoPrefs.power(context) != PowerMode.AUTO) {
            // Rider switched to a fixed mode mid-session: hand the rate back once.
            if (adapting && autoAdapted) restoreFixed(pipe)
            linkOnly(pipe)
            return
        }
        autoAdapted = true

        val base = pipe.targetBitrate()
        if (base <= 0) return   // encoder not created yet (pre-canvas)

        val status = try { powerManager.currentThermalStatus } catch (_: Exception) {
            PowerManager.THERMAL_STATUS_NONE
        }

        val total = pipe.droppedFramesTotal()
        val dropsThisTick = (total - lastDropTotal).coerceAtLeast(0L).toInt()
        lastDropTotal = total

        val d = AdaptivePolicy.decide(
            baseBitrate = base,
            baseFps = PowerMode.AUTO.fps,
            thermalStatus = status,
            prevLinkFactor = linkFactor,
            dropsThisTick = dropsThisTick,
        )
        linkFactor = d.linkFactor

        if (d.bitrate != appliedBitrate) {
            pipe.setEncoderBitrate(d.bitrate)
            appliedBitrate = d.bitrate
            adapting = true
            log("[adaptive] thermal=${thermalName(status)} drops/tick=$dropsThisTick link=${pct(linkFactor)} → ${d.bitrate / 1000}kbps")
        }
        if (d.fps != appliedFps) {
            pipe.setFrameCap(d.fps)
            appliedFps = d.fps
            adapting = true
            log("[adaptive] thermal=${thermalName(status)} → ${d.fps}fps")
        }
    }

    /** Restore the pipeline to the (now-fixed) power mode's rate and stop adapting. */
    private fun restoreFixed(pipe: VideoPipeline) {
        val base = pipe.targetBitrate()
        if (base > 0) pipe.setEncoderBitrate(base)
        pipe.setFrameCap(VideoPrefs.power(context).fps)
        log("[adaptive] AUTO off — restored to ${VideoPrefs.power(context).label}")
        reset()
    }

    private fun pct(f: Float) = "${(f * 100).toInt()}%"

    private fun thermalName(status: Int) = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> "?$status"
    }
}
