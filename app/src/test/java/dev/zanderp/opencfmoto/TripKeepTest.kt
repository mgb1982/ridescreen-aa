package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripKeepTest {
    @Test fun liveAlwaysRecords() = assertTrue(TripKeep.keepRecording(true, Phase.STOPPED, 0, 0))

    @Test fun reconnectKeepsSameTripWithinGrace() {
        assertTrue(TripKeep.keepRecording(false, Phase.RECONNECTING, 1_000, 1_000 + 60_000))
        assertTrue(TripKeep.keepRecording(false, Phase.WAITING_FOR_BIKE, 1_000, 1_000 + 9 * 60_000))
    }

    @Test fun graceRunsOut() =
        assertFalse(TripKeep.keepRecording(false, Phase.RECONNECTING, 1_000, 1_000 + TripKeep.LINK_GAP_GRACE_MS))

    @Test fun stopEndsAtOnce() {
        assertFalse(TripKeep.keepRecording(false, Phase.STOPPED, 1_000, 2_000))
        assertFalse(TripKeep.keepRecording(false, Phase.IDLE, 1_000, 2_000))
        assertFalse(TripKeep.keepRecording(false, Phase.ERROR, 1_000, 2_000))
    }

    @Test fun keepsGoodLeg() = assertNull(TripKeep.discardReason(1800.0, 300_000, 200, 300.0, 30_000))

    @Test fun explainsDiscard() {
        val r = TripKeep.discardReason(120.0, 10_000, 1, 300.0, 30_000)
        assertNotNull(r)
        assertTrue(r!!.contains("120 m"))
        assertTrue(r.contains("10 s moving"))
        assertTrue(r.contains("1 GPS point"))
        assertEquals("only 120 m (min 300)", TripKeep.discardReason(120.0, 60_000, 50, 300.0, 30_000))
    }
}
