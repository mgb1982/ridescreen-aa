package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoServiceTest {
    @Test
    fun bikeTransportIsLiveForApOrP2p() {
        assertFalse(AndroidAutoService.bikeTransportConnected(false, false))
        assertTrue(AndroidAutoService.bikeTransportConnected(true, false))
        assertTrue(AndroidAutoService.bikeTransportConnected(false, true))
        assertTrue(AndroidAutoService.bikeTransportConnected(true, true))
        // 125M: the dash is on the phone's hotspot (no AP, no P2P) but frames are flowing.
        assertTrue(AndroidAutoService.bikeTransportConnected(false, false, liveLink = true))
    }

    @Test
    fun aBikeThatNeverLinkedParksLaterThanADroppedOne() {
        assertEquals(180_000L, AndroidAutoService.parkGraceMs(sawStream = true))
        assertEquals(300_000L, AndroidAutoService.parkGraceMs(sawStream = false))
        assertTrue(AndroidAutoService.parkGraceMs(false) > AndroidAutoService.parkGraceMs(true))
    }
}
