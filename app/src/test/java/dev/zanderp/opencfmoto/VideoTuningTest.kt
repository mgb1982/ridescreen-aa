package dev.zanderp.opencfmoto

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTuningTest {

    // ---- option 1 · heat → ceiling ----

    @Test fun heatTableMatchesTheSpec() {
        val none = HeatPolicy.NONE
        assertEquals(none, HeatPolicy.capFor(PowerManager.THERMAL_STATUS_NONE))
        assertEquals(none, HeatPolicy.capFor(PowerManager.THERMAL_STATUS_LIGHT))
        assertEquals(HeatPolicy.Cap(0.8f, 20), HeatPolicy.capFor(PowerManager.THERMAL_STATUS_MODERATE))
        assertEquals(HeatPolicy.Cap(0.6f, 15), HeatPolicy.capFor(PowerManager.THERMAL_STATUS_SEVERE))
        assertEquals(HeatPolicy.Cap(0.5f, 12), HeatPolicy.capFor(PowerManager.THERMAL_STATUS_CRITICAL))
        assertEquals(HeatPolicy.Cap(0.5f, 12), HeatPolicy.capFor(PowerManager.THERMAL_STATUS_EMERGENCY))
        assertEquals(HeatPolicy.Cap(0.5f, 12), HeatPolicy.capFor(PowerManager.THERMAL_STATUS_SHUTDOWN))
        assertFalse(none.active)
    }

    @Test fun heatCeilingNeverRaisesWhatTheAdaptiveControlChose() {
        val base = 2_500_000
        val moderate = HeatPolicy.capFor(PowerManager.THERMAL_STATUS_MODERATE)
        assertEquals(2_000_000, HeatPolicy.clampBitrate(2_500_000, base, moderate))   // capped to 80 %
        assertEquals(1_600_000, HeatPolicy.clampBitrate(1_600_000, base, moderate))   // link already lower: kept
        assertEquals(1_250_000, HeatPolicy.clampBitrate(2_000_000, base, HeatPolicy.capFor(PowerManager.THERMAL_STATUS_CRITICAL)))
        assertEquals(2_500_000, HeatPolicy.clampBitrate(2_500_000, base, HeatPolicy.NONE))
    }

    @Test fun heatCeilingKeepsTheProjectFloor() {
        // 700 kbps base at 50 % would be 350 kbps; the floor (600 kbps) wins, never above the base itself.
        val critical = HeatPolicy.capFor(PowerManager.THERMAL_STATUS_CRITICAL)
        assertEquals(AdaptivePolicy.MIN_BITRATE, HeatPolicy.clampBitrate(700_000, 700_000, critical))
        assertEquals(400_000, HeatPolicy.clampBitrate(400_000, 400_000, critical))   // base below the floor: untouched
    }

    @Test fun heatFpsCap() {
        val moderate = HeatPolicy.capFor(PowerManager.THERMAL_STATUS_MODERATE)
        assertEquals(20, HeatPolicy.clampFps(30, moderate))
        assertEquals(15, HeatPolicy.clampFps(15, moderate))
        assertEquals(15, HeatPolicy.clampFps(24, HeatPolicy.capFor(PowerManager.THERMAL_STATUS_SEVERE)))
        assertEquals(12, HeatPolicy.clampFps(24, HeatPolicy.capFor(PowerManager.THERMAL_STATUS_CRITICAL)))
        assertEquals(30, HeatPolicy.clampFps(30, HeatPolicy.NONE))
    }

    @Test fun heatLineFormat() {
        assertEquals(
            "[PWR] heat thermal=moderate headroom10s=0.85 battery=38.5°C plugged=no",
            HeatPolicy.line(PowerManager.THERMAL_STATUS_MODERATE, 0.85f, 385, 0),
        )
        assertEquals(
            "[PWR] heat thermal=none headroom10s=n/a battery=n/a plugged=usb",
            HeatPolicy.line(PowerManager.THERMAL_STATUS_NONE, Float.NaN, null, 2),
        )
    }

    // ---- option 2 · repeat interval ----

    @Test fun repeatInterval() {
        assertEquals(100_000L, EncoderTuning.repeatAfterUs(fastOn = true, saver = false, keyframeIntervalSec = 1))
        assertEquals(500_000L, EncoderTuning.repeatAfterUs(fastOn = false, saver = false, keyframeIntervalSec = 1))   // option off
        assertEquals(900_000L, EncoderTuning.repeatAfterUs(fastOn = true, saver = true, keyframeIntervalSec = 1))   // Battery saver keeps ~1 fps
        assertEquals(900_000L, EncoderTuning.repeatAfterUs(fastOn = true, saver = false, keyframeIntervalSec = 0))  // all-intra
        assertEquals(900_000L, EncoderTuning.repeatAfterUs(fastOn = false, saver = true, keyframeIntervalSec = 1))  // saver, option off too
    }

    // ---- option 4 · intra-refresh ----

    @Test fun intraRefreshNeedsOptionAndSupport() {
        assertTrue(EncoderTuning.useIntraRefresh(optionOn = true, codecSupports = true))
        assertFalse(EncoderTuning.useIntraRefresh(optionOn = true, codecSupports = false))
        assertFalse(EncoderTuning.useIntraRefresh(optionOn = false, codecSupports = true))
    }

    // ---- option 3 · loss classification ----

    @Test fun pausedLinkIsAlwaysLinkDown() {
        assertEquals(LossKind.LINK_DOWN, LossPolicy.classify(true, false, 0L, false))
        assertEquals(LossKind.LINK_DOWN, LossPolicy.classify(true, false, 100L, true))
    }

    @Test fun knownDownWifiCountsAsLinkDownOnlyWithTheOption() {
        assertEquals(LossKind.LINK_DOWN, LossPolicy.classify(false, true, 500L, true))
        assertEquals(LossKind.SATURATED, LossPolicy.classify(false, true, 500L, false))   // old rule
    }

    @Test fun idleForTwoSecondsIsLinkDownEvenWithoutTheOption() {
        assertEquals(LossKind.LINK_DOWN, LossPolicy.classify(false, false, 2_000L, false))
        assertEquals(LossKind.SATURATED, LossPolicy.classify(false, false, 1_999L, false))
        assertEquals(LossKind.SATURATED, LossPolicy.classify(false, false, 0L, false))   // never pulled yet
    }

    @Test fun onlySaturationCountsAsCongestion() {
        assertTrue(LossPolicy.countsTowardCongestion(LossKind.SATURATED))
        assertFalse(LossPolicy.countsTowardCongestion(LossKind.LINK_DOWN))
    }

    @Test fun linkDownLossesNeverLowerTheBitrate() {
        val c = LossCounters()
        repeat(500) { c.onLinkDown() }                       // a long outage: queue overflow after overflow
        val afterOutage = AdaptivePolicy.nextLinkFactor(1.0f, c.saturated.toInt())
        assertEquals(1.0f, afterOutage, 0f)                  // controller reads "saturated" only → no back-off
        c.onSaturated(AdaptivePolicy.DROP_CONGESTION_THRESHOLD.toLong())
        assertTrue(AdaptivePolicy.nextLinkFactor(1.0f, c.saturated.toInt()) < 1.0f)
    }

    // ---- option 3 · dash re-attach ("rejected") and recovery time ----

    @Test fun firstAttachIsNotARejection() {
        val c = LossCounters()
        c.onReattach(1_000L, alive = true)                   // nothing served yet: the normal first attach
        assertEquals(0L, c.rejected)
    }

    @Test fun reattachWhileTheDashWasPullingCountsAndTimesTheRecovery() {
        val c = LossCounters()
        assertNull(c.onServed(10L))
        c.onReattach(5_000L, alive = true)
        assertEquals(1L, c.rejected)
        assertEquals(320L, c.onServed(5_320L))
        assertNull(c.onServed(5_400L))                       // only the first frame after the re-attach reports
        assertTrue(c.summary().contains("rejected=1"))
        assertTrue(c.summary().contains("max 320 ms"))
    }

    @Test fun reattachAfterALinkDropIsNotARejection() {
        val c = LossCounters()
        c.onServed(10L)
        c.onReattach(9_000L, alive = false)                  // pipeline was paused / dash had stopped pulling
        assertEquals(0L, c.rejected)
        assertNull(c.onServed(9_100L))
    }

    @Test fun summaryOnlyOnceAndOnlyWhenThereIsSomethingToSay() {
        val c = LossCounters()
        assertNull(c.summaryOnce())
        c.onSaturated(3); c.onLinkDown(5)
        assertEquals("[VIDEO] session loss summary: link-down=5 saturated=3 rejected=0", c.summaryOnce())
        assertNull(c.summaryOnce())
        assertNotNull(c.summary())
    }

    // ---- frame size window (V1 diagnostics)

    @Test fun frameSizeWindowReportsAvgMaxAndKeyframe() {
        val st = FrameSizeStats(windowMs = 30_000L)
        assertNull(st.take(1_000L, false))                       // first call only starts the window
        st.add(1_000, false); st.add(3_000, false); st.add(20_000, true)
        assertNull(st.take(20_000L, false))                       // not 30 s yet
        assertEquals(
            "[VIDEO] frame sizes 30s: n=3 avg=8000B max=20000B keyframe-max=20000B intra-refresh=off",
            st.take(31_000L, false),
        )
        assertNull(st.take(40_000L, true))                        // a new window
    }

    @Test fun frameSizeWindowWithoutFramesSaysNothing() {
        val st = FrameSizeStats(windowMs = 10_000L)
        st.take(0L, true)
        assertNull(st.take(11_000L, true))
    }
}
