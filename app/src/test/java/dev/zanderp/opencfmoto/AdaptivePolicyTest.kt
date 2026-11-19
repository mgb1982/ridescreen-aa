package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AdaptivePolicy] — the pure adaptation math behind [PowerMode.AUTO]. The thermal
 * status ints are the real `PowerManager.THERMAL_STATUS_*` values (0=NONE … 6=SHUTDOWN), which are
 * compile-time constants so this runs on the host JVM with no Android runtime.
 */
class AdaptivePolicyTest {

    private val base = 2_500_000   // a typical target bitrate
    private val baseFps = 30

    // ── thermal → bitrate factor ─────────────────────────────────────────────────────────────────

    @Test fun thermal_none_and_light_do_not_throttle() {
        assertEquals(1.0f, AdaptivePolicy.thermalBitrateFactor(0), 0f) // NONE
        assertEquals(1.0f, AdaptivePolicy.thermalBitrateFactor(1), 0f) // LIGHT
    }

    @Test fun thermal_steps_down_as_it_heats() {
        assertEquals(0.8f, AdaptivePolicy.thermalBitrateFactor(2), 0f) // MODERATE
        assertEquals(0.6f, AdaptivePolicy.thermalBitrateFactor(3), 0f) // SEVERE
        assertEquals(0.5f, AdaptivePolicy.thermalBitrateFactor(4), 0f) // CRITICAL
        assertEquals(0.5f, AdaptivePolicy.thermalBitrateFactor(6), 0f) // SHUTDOWN
    }

    // ── thermal → fps cap ────────────────────────────────────────────────────────────────────────

    @Test fun fps_cap_full_when_cool_then_drops() {
        assertEquals(30, AdaptivePolicy.thermalFpsCap(0, baseFps))
        assertEquals(30, AdaptivePolicy.thermalFpsCap(1, baseFps))
        assertEquals(20, AdaptivePolicy.thermalFpsCap(2, baseFps))
        assertEquals(15, AdaptivePolicy.thermalFpsCap(3, baseFps))
        assertEquals(12, AdaptivePolicy.thermalFpsCap(5, baseFps))
    }

    @Test fun fps_cap_never_exceeds_base() {
        // A saver-style base of 20 must not be raised by the "cool" branch.
        assertEquals(20, AdaptivePolicy.thermalFpsCap(0, 20))
        assertEquals(15, AdaptivePolicy.thermalFpsCap(3, 20))
    }

    // ── AIMD link factor ─────────────────────────────────────────────────────────────────────────

    @Test fun link_backs_off_on_congestion() {
        val next = AdaptivePolicy.nextLinkFactor(1.0f, AdaptivePolicy.DROP_CONGESTION_THRESHOLD)
        assertEquals(0.8f, next, 1e-4f)
    }

    @Test fun link_recovers_gently_when_clean() {
        val next = AdaptivePolicy.nextLinkFactor(0.8f, 0)
        assertEquals(0.85f, next, 1e-4f)
    }

    @Test fun link_never_below_floor_or_above_ceiling() {
        var f = 1.0f
        repeat(50) { f = AdaptivePolicy.nextLinkFactor(f, 100) }   // hammer congestion
        assertEquals(AdaptivePolicy.LINK_MIN, f, 1e-4f)
        repeat(200) { f = AdaptivePolicy.nextLinkFactor(f, 0) }    // long clean spell
        assertEquals(AdaptivePolicy.LINK_MAX, f, 1e-4f)
    }

    @Test fun a_few_drops_below_threshold_are_not_congestion() {
        val next = AdaptivePolicy.nextLinkFactor(1.0f, AdaptivePolicy.DROP_CONGESTION_THRESHOLD - 1)
        assertEquals(1.0f, next, 1e-4f) // clean tick → stays at ceiling
    }

    // ── decide() combines the two ────────────────────────────────────────────────────────────────

    @Test fun cool_and_clean_yields_full_quality() {
        val d = AdaptivePolicy.decide(base, baseFps, thermalStatus = 0, prevLinkFactor = 1.0f, dropsThisTick = 0)
        assertEquals(base, d.bitrate)
        assertEquals(30, d.fps)
        assertEquals(1.0f, d.linkFactor, 0f)
    }

    @Test fun hot_reduces_both_bitrate_and_fps() {
        val d = AdaptivePolicy.decide(base, baseFps, thermalStatus = 3, prevLinkFactor = 1.0f, dropsThisTick = 0)
        assertEquals((base * 0.6f).toInt(), d.bitrate)   // severe → 0.6
        assertEquals(15, d.fps)
    }

    @Test fun the_more_aggressive_pressure_wins_on_bitrate() {
        // Moderate thermal (0.8) but a badly congested link driven down to the floor (0.4) → link wins.
        val d = AdaptivePolicy.decide(base, baseFps, thermalStatus = 2, prevLinkFactor = 0.5f, dropsThisTick = 100)
        // link steps 0.5 → 0.4; min(0.8, 0.4) = 0.4
        assertEquals(0.4f, d.linkFactor, 1e-4f)
        assertEquals((base * 0.4f).toInt(), d.bitrate)
        assertEquals(20, d.fps)   // fps follows thermal (moderate), not link
    }

    @Test fun bitrate_never_drops_below_the_floor() {
        val lowBase = 1_000_000
        // Drive link and thermal both hard; result would be 0.4 * 0.5 semantics, but factor is a min
        // not a product, so worst factor is 0.4 → 400k, floored to MIN_BITRATE.
        val d = AdaptivePolicy.decide(lowBase, baseFps, thermalStatus = 6, prevLinkFactor = AdaptivePolicy.LINK_MIN, dropsThisTick = 100)
        assertTrue("expected >= MIN_BITRATE", d.bitrate >= AdaptivePolicy.MIN_BITRATE)
    }

    @Test fun floor_is_capped_by_base_when_base_is_tiny() {
        // If the user's target is somehow below MIN_BITRATE, we must not raise it above the target.
        val tinyBase = 400_000
        val d = AdaptivePolicy.decide(tinyBase, baseFps, thermalStatus = 6, prevLinkFactor = AdaptivePolicy.LINK_MIN, dropsThisTick = 100)
        assertTrue("must never exceed target", d.bitrate <= tinyBase)
    }

    // ---- v2.7 · faster recovery after a run of clean ticks

    @Test fun increaseStepByCleanRun() {
        assertEquals(0.05f, AdaptivePolicy.increaseStep(1), 1e-6f)
        assertEquals(0.05f, AdaptivePolicy.increaseStep(2), 1e-6f)
        assertEquals(0.10f, AdaptivePolicy.increaseStep(3), 1e-6f)
        assertEquals(0.10f, AdaptivePolicy.increaseStep(4), 1e-6f)
        assertEquals(0.20f, AdaptivePolicy.increaseStep(5), 1e-6f)
        assertEquals(0.20f, AdaptivePolicy.increaseStep(50), 1e-6f)
        assertEquals(0.05f, AdaptivePolicy.increaseStep(0), 1e-6f)   // never below the base step
    }

    @Test fun cleanRunCountsAndResets() {
        assertEquals(1, AdaptivePolicy.nextCleanTicks(0, 0))
        assertEquals(6, AdaptivePolicy.nextCleanTicks(5, AdaptivePolicy.DROP_CONGESTION_THRESHOLD - 1))
        assertEquals(0, AdaptivePolicy.nextCleanTicks(9, AdaptivePolicy.DROP_CONGESTION_THRESHOLD))
    }

    @Test fun recoveryAcceleratesWithTheRun() {
        // From the floor: ticks 1-2 +0.05, 3-4 +0.10, then +0.20 each, capped at LINK_MAX.
        var f = AdaptivePolicy.LINK_MIN
        var clean = 0
        val seen = ArrayList<Float>()
        repeat(8) {
            f = AdaptivePolicy.nextLinkFactor(f, 0, clean); clean = AdaptivePolicy.nextCleanTicks(clean, 0); seen.add(f)
        }
        assertEquals(listOf(0.45f, 0.50f, 0.60f, 0.70f, 0.90f, 1.0f, 1.0f, 1.0f), seen.map { Math.round(it * 100) / 100f })
    }

    @Test fun congestionStillBacksOffByTwentyPercentAndResetsTheRun() {
        val next = AdaptivePolicy.nextLinkFactor(1.0f, AdaptivePolicy.DROP_CONGESTION_THRESHOLD, cleanTicksBefore = 9)
        assertEquals(0.8f, next, 1e-6f)
        assertEquals(1.0f, AdaptivePolicy.nextLinkFactor(0.95f, 0, 20), 0f)   // still never above LINK_MAX
    }

    @Test fun decideCarriesTheRun() {
        val d = AdaptivePolicy.decide(2_000_000, 24, 0, 0.5f, 0, cleanTicks = 4)
        assertEquals(0.7f, d.linkFactor, 1e-6f)    // 5th clean tick: +0.20
        assertEquals(5, d.cleanTicks)
        assertEquals(0, AdaptivePolicy.decide(2_000_000, 24, 0, 0.5f, 99, cleanTicks = 4).cleanTicks)
    }
}
