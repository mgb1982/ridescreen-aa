package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.LockHold.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockHoldTest {
    private val t0 = 1_000_000L

    @Test fun noHoldAllowsEverything() {
        assertTrue(LockHold.allows(0L, t0, false, Trigger.WIFI_SEEN))
        assertTrue(LockHold.allows(0L, t0, false, Trigger.BT_CONNECTED))
    }

    @Test fun wifiStillUpRightAfterLockIsHeld() {
        assertFalse(LockHold.allows(t0, t0 + 5_000, false, Trigger.WIFI_SEEN))
        assertFalse(LockHold.allows(t0, t0 + 30 * 60_000, false, Trigger.WIFI_SEEN))
    }

    @Test fun wifiAfterTheDashWentOffIsANewRide() {
        assertTrue(LockHold.allows(t0, t0 + 5 * 60_000, true, Trigger.WIFI_SEEN))
    }

    @Test fun bluetoothNeedsTheGrace() {
        assertFalse(LockHold.allows(t0, t0 + 10_000, false, Trigger.BT_CONNECTED))
        assertTrue(LockHold.allows(t0, t0 + LockHold.BT_GRACE_MS, false, Trigger.BT_CONNECTED))
    }

    @Test fun safetyNetAndClockGoingBack() {
        assertTrue(LockHold.allows(t0, t0 + LockHold.MAX_HOLD_MS, false, Trigger.WIFI_SEEN))
        assertTrue(LockHold.allows(t0, t0 - 1, false, Trigger.WIFI_SEEN))
    }
}
