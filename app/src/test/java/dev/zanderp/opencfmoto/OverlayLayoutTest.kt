package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutTest {
    @Test
    fun roundTrips() {
        val l = OverlayLayout().with(OverlayPart.MAP, -0.25f, 0.5f).with(OverlayPart.SPEED, 0.1f, -0.05f)
        assertEquals(l, OverlayLayout.decode(l.encode()))
    }

    @Test
    fun defaultAndJunk() {
        assertTrue(OverlayLayout.decode(null).isDefault)
        assertTrue(OverlayLayout.decode("").isDefault)
        assertTrue(OverlayLayout().with(OverlayPart.MAP, 0f, 0f).isDefault)
        assertEquals("", OverlayLayout().with(OverlayPart.MAP, 0f, 0f).encode())
        assertEquals(OverlayLayout().with(OverlayPart.TITLE, 0.2f, 0.3f), OverlayLayout.decode("TITLE:0.2,0.3;NOPE:1,1;MAP:x,1;SPEED"))
    }
}
