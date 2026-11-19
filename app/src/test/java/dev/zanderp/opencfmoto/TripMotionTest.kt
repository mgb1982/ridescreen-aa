// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripMotionTest {
    private fun trip(vararg s: MotionTripSample) =
        Trip("1", 0, 1, 0.0, 0, 0f, emptyList(), motionSamples = s.toList())

    @Test fun encodeDecodeRoundTripKeepsNulls() {
        val src = listOf(
            MotionTripSample(1_000, -32.5, -0.55, 0.1, 140),
            MotionTripSample(1_250, null, null, null, 141),
            MotionTripSample(1_500, 12.0, 0.2, -0.3, null),
        )
        val back = MotionCodec.decode(JSONArray(MotionCodec.encode(src).toString()))
        assertEquals(src, back)
    }

    @Test fun decodeToleratesMissingAndBrokenData() {
        assertEquals(emptyList<MotionTripSample>(), MotionCodec.decode(null))
        val arr = JSONArray("[[5],[7,1.5],\"x\"]")
        val out = MotionCodec.decode(arr)
        assertEquals(2, out.size)
        assertEquals(MotionTripSample(5), out[0])
        assertEquals(1.5, out[1].leanDeg!!, 0.0)
    }

    @Test fun oldTripsHaveNoMotion() {
        val t = Trip("1", 0, 1, 0.0, 0, 0f, emptyList())
        assertEquals(emptyList<MotionTripSample>(), t.motionSamples)
        assertNull(t.motionSummary())
        assertNull(t.motionAt(5))
    }

    @Test fun motionAtPicksTheNearestWithinTwoSeconds() {
        val t = trip(MotionTripSample(1_000, 1.0), MotionTripSample(2_000, 2.0), MotionTripSample(6_000, 6.0))
        assertEquals(2.0, t.motionAt(2_300)!!.leanDeg!!, 0.0)
        assertEquals(6.0, t.motionAt(5_000)!!.leanDeg!!, 0.0)
        assertNull(t.motionAt(4_000, 1_500))      // 2 s from both
        assertNull(t.motionAt(20_000))
    }

    @Test fun summaryHasMaxLeanEachSideGAndHeart() {
        val t = trip(
            MotionTripSample(1, -38.0, -0.6, 0.0, 120),
            MotionTripSample(2, 41.0, 0.3, 0.4, 130),
            MotionTripSample(3, 5.0, 0.0, -0.8, null),
            MotionTripSample(4, null, null, null, 140),
        )
        val s = t.motionSummary()!!
        assertEquals(38.0, s.maxLeanLeftDeg!!, 0.0)
        assertEquals(41.0, s.maxLeanRightDeg!!, 0.0)
        assertEquals(0.8, s.maxG!!, 1e-9)
        assertEquals(130, s.avgBpm)
        assertEquals(140, s.maxBpm)
    }

    @Test fun summaryOnlyHeartRate() {
        val s = trip(MotionTripSample(1, bpm = 100), MotionTripSample(2, bpm = 111)).motionSummary()!!
        assertNull(s.maxLeanLeftDeg); assertNull(s.maxLeanRightDeg); assertNull(s.maxG)
        assertEquals(106, s.avgBpm)
    }
}
