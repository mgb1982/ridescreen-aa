package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardRedrawTest {
    private fun data(rpm: Double?, speed: Double?, volt: Double? = 13.8) = DashboardMode.Data(
        rpm, speed, false, 90.0, 10.0, volt, null, null, null, null, null, true,
    )
    private fun sig(d: DashboardMode.Data, clock: String = "10:00") =
        DashboardMode.signatureOf(d, false, "SPORT", "rpm,speed", clock)

    @Test fun sameRoundedValuesGiveSameSignature() {
        assertEquals(sig(data(5_010.0, 50.2)), sig(data(5_020.0, 49.9)))
        assertEquals(sig(data(5_000.0, 50.0, 13.81)), sig(data(5_000.0, 50.0, 13.84)))
    }

    @Test fun visibleChangesChangeIt() {
        assertNotEquals(sig(data(5_000.0, 50.0)), sig(data(5_100.0, 50.0)))
        assertNotEquals(sig(data(5_000.0, 50.0)), sig(data(5_000.0, 51.0)))
        assertNotEquals(sig(data(5_000.0, 50.0, 13.8)), sig(data(5_000.0, 50.0, 13.9)))
        assertNotEquals(sig(data(5_000.0, 50.0)), sig(data(null, 50.0)))
        assertNotEquals(sig(data(5_000.0, 50.0)), sig(data(5_000.0, 50.0), clock = "10:01"))
    }

    @Test fun redrawRule() {
        assertFalse(LiveRedraw.shouldDraw("a", "a", 500, false, 2_000))
        assertTrue(LiveRedraw.shouldDraw("b", "a", 500, false, 2_000))
        assertTrue(LiveRedraw.shouldDraw("a", "a", 2_000, false, 2_000))
        assertTrue(LiveRedraw.shouldDraw("a", "a", 10, true, 2_000))
        assertTrue(LiveRedraw.shouldDraw(null, null, 10, false, 2_000))
    }
}
