// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroToSpeedTest {
    private val z = ZeroToSpeed

    /** Armed, Go pressed at t=0, count run out: WAITING_LAUNCH at t=3000. */
    private fun waiting(imperial: Boolean = false): ChronoState {
        var s = z.arm(ChronoState(), 0.0, imperial)
        s = z.go(s, 0.0, 0L)
        var t = 0L
        while (t < 3_000L) { t += 100L; s = z.step(s, 0.0, t) }
        assertEquals(ChronoPhase.WAITING_LAUNCH, s.phase)
        return s
    }

    /** Linear ramp 0 -> [kmhAt100s] ... a run reaching [topKmh] in [secs] s, sampled every [dtMs]. */
    private fun run(s0: ChronoState, startMs: Long, secs: Double, topKmh: Double, dtMs: Long): ChronoState {
        var s = s0
        var t = 0L
        while (true) {
            t += dtMs
            val v = topKmh * t / (secs * 1000.0)
            s = z.step(s, v, startMs + t)
            if (s.phase != ChronoPhase.RUNNING && s.phase != ChronoPhase.WAITING_LAUNCH) return s
            if (t > 60_000L) return s
        }
    }

    @Test fun cleanRunZeroToHundredIn6_3() {
        val s = run(waiting(), 3_000L, 6.3, 100.0, 100L)
        assertEquals(ChronoPhase.DONE, s.phase)
        // speed(t) = 100 t / 6.3: launch at 2 km/h = 0.126 s, 100 km/h at 6.3 s -> 6.174 s from launch
        assertEquals(6.174, s.t2!!, 0.01)
        assertEquals(60.0 / 100.0 * 6.3 - 0.126, s.t1!!, 0.01)
    }

    @Test fun interpolatesWithOneHertzSamples() {
        var s = waiting()
        s = z.step(s, 0.0, 4_000L)
        s = z.step(s, 10.0, 5_000L)         // crossed 2 km/h at 4_200 ms
        assertEquals(ChronoPhase.RUNNING, s.phase)
        assertEquals(4_200.0, s.launchMs!!, 0.001)
        s = z.step(s, 50.0, 6_000L)
        s = z.step(s, 70.0, 7_000L)         // 60 km/h at 6_500 ms -> 2.3 s
        assertEquals(2.3, s.t1!!, 0.001)
        s = z.step(s, 110.0, 8_000L)        // 100 km/h at 7_750 ms -> 3.55 s
        assertEquals(ChronoPhase.DONE, s.phase)
        assertEquals(3.55, s.t2!!, 0.001)
    }

    @Test fun armedNeverMeasuresWithoutGoAndCountdown() {
        var s = z.arm(ChronoState(), 0.0, false)
        for (i in 1..50) s = z.step(s, i * 3.0, i * 100L)
        // Moving off while only armed disarms it, and nothing was ever measured.
        assertEquals(ChronoPhase.CANCELLED, s.phase)
        assertEquals(ChronoReason.MOVED, s.reason)
        assertNull(s.t1)
        assertNull(s.launchMs)
    }

    @Test fun armedStaysArmedWhileStandingStill() {
        var s = z.arm(ChronoState(), 0.0, false, nowMs = 1_000L)
        for (i in 1..100) s = z.step(s, 0.0, 1_000L + i * 1_000L)
        assertEquals(ChronoPhase.ARMED, s.phase)
    }

    @Test fun armedAndForgottenDisarmsAfterThreeMinutes() {
        var s = z.arm(ChronoState(), 0.0, false, nowMs = 10_000L)
        s = z.step(s, 0.0, 10_000L + ZeroToSpeed.ARMED_TIMEOUT_MS)
        assertEquals(ChronoPhase.ARMED, s.phase)
        s = z.step(s, 0.0, 10_000L + ZeroToSpeed.ARMED_TIMEOUT_MS + 1L)
        assertEquals(ChronoPhase.CANCELLED, s.phase)
        assertEquals(ChronoReason.TIMEOUT, s.reason)
        assertTrue(s.dashVisible)
        // The notice runs out after five seconds like every other end state.
        s = z.step(s, 0.0, s.endedAtMs + ZeroToSpeed.SHOW_RESULT_MS)
        assertFalse(s.dashVisible)
    }

    @Test fun armedWithoutATimestampNeverTimesOut() {
        var s = z.arm(ChronoState(), 0.0, false)
        s = z.step(s, 0.0, 10_000_000L)
        assertEquals(ChronoPhase.ARMED, s.phase)
    }

    @Test fun waitingForTheLaunchGivesUpAfterThirtySeconds() {
        var s = waiting()
        val reachedGo = s.digitSinceMs
        s = z.step(s, 0.0, reachedGo + ZeroToSpeed.WAIT_TIMEOUT_MS)
        assertEquals(ChronoPhase.WAITING_LAUNCH, s.phase)
        s = z.step(s, 0.0, reachedGo + ZeroToSpeed.WAIT_TIMEOUT_MS + 1L)
        assertEquals(ChronoPhase.CANCELLED, s.phase)
        assertEquals(ChronoReason.TIMEOUT, s.reason)
    }

    @Test fun countdownShowsThreeTwoOneThenWaits() {
        var s = z.go(z.arm(ChronoState(), 0.0, false), 0.0, 1_000L)
        assertEquals(3, s.count)
        s = z.step(s, 0.0, 2_000L); assertEquals(2, s.count)
        s = z.step(s, 0.0, 3_000L); assertEquals(1, s.count)
        s = z.step(s, 0.0, 3_900L); assertEquals(ChronoPhase.COUNTDOWN, s.phase)
        s = z.step(s, 0.0, 4_000L); assertEquals(ChronoPhase.WAITING_LAUNCH, s.phase)
    }

    @Test fun movingDuringCountdownCancels() {
        var s = z.go(z.arm(ChronoState(), 0.0, false), 0.0, 0L)
        s = z.step(s, 3.0, 500L)
        assertEquals(ChronoPhase.COUNTDOWN, s.phase)
        s = z.step(s, 6.0, 900L)
        assertEquals(ChronoPhase.CANCELLED, s.phase)
        assertEquals(ChronoReason.MOVED, s.reason)
    }

    @Test fun rollingIntoWaitingIsNotAFairRun() {
        var s = waiting()
        s = z.step(s, 3.0, 3_100L)           // rolled to 3 km/h: it would have started here, from standstill
        assertEquals(ChronoPhase.RUNNING, s.phase)
        val r = z.go(z.arm(ChronoState(), 0.0, false), 0.0, 0L)
        var w = z.step(r, 3.0, 400L)
        w = z.step(w, 3.0, 3_000L)
        assertEquals(ChronoPhase.WAITING_LAUNCH, w.phase)
        w = z.step(w, 4.0, 3_100L)
        assertEquals(ChronoPhase.CANCELLED, w.phase)
        assertEquals(ChronoReason.ROLLING, w.reason)
    }

    @Test fun cannotArmOrGoWhileMoving() {
        assertTrue(z.canArm(ChronoState(), 0.5))
        assertTrue(!z.canArm(ChronoState(), 1.0))
        assertTrue(!z.canArm(ChronoState(), null))
        assertEquals(ChronoPhase.IDLE, z.arm(ChronoState(), 20.0, false).phase)
        val armed = z.arm(ChronoState(), 0.0, false)
        assertEquals(ChronoPhase.ARMED, z.go(armed, 8.0, 0L).phase)
    }

    @Test fun sixtyAndHundredInTheSameRun() {
        val s = run(waiting(), 3_000L, 5.0, 120.0, 100L)
        assertEquals(ChronoPhase.DONE, s.phase)
        assertNotNull(s.t1)
        assertTrue(s.t1!! < s.t2!!)
    }

    @Test fun imperialUsesFortyAndSixtyMph() {
        assertEquals(64.37, z.target1Kmh(true), 0.01)
        assertEquals(96.56, z.target2Kmh(true), 0.01)
        var s = waiting(true)
        s = z.step(s, 0.0, 4_000L)
        s = z.step(s, 64.0, 5_000L)          // just under 40 mph
        assertNull(s.t1)
        s = z.step(s, 70.0, 6_000L)
        assertNotNull(s.t1)
        assertNull(s.t2)
        s = z.step(s, 100.0, 7_000L)
        assertEquals(ChronoPhase.DONE, s.phase)
    }

    @Test fun resultOnTheDashExpiresAfterFiveSeconds() {
        var s = run(waiting(), 3_000L, 6.0, 110.0, 100L)
        assertTrue(s.dashVisible)
        val end = s.endedAtMs
        s = z.step(s, 0.0, end + 4_900L); assertTrue(s.dashVisible)
        s = z.step(s, 0.0, end + 5_000L); assertTrue(!s.dashVisible)
    }

    @Test fun historyKeepsTwentyAndRecordsPerUnit() {
        var list = emptyList<ChronoRun>()
        for (i in 1..25) list = ChronoHistory.add(list, ChronoRun(i.toLong(), false, false, 4.0 + i, 8.0 + i))
        assertEquals(20, list.size)
        assertEquals(25L, list.first().atMs)
        assertEquals(14.0, ChronoHistory.best(list, false, true)!!, 0.0001)
        assertNull(ChronoHistory.best(list, true, false))
        val better = ChronoRun(99L, false, true, 9.5, 20.0)
        assertEquals(true to false, ChronoHistory.newRecords(list, better))
        assertEquals(true to true, ChronoHistory.newRecords(list, better.copy(imperial = true)))
    }
}
