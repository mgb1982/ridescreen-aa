// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryAlertTest {
    private val now = 10L * 24 * 3_600_000L
    private val h = 3_600_000L

    @Test fun lowAndStoppedWarns() = assertTrue(BatteryAlert.shouldWarn(12.1, false, 0L, now))
    @Test fun atThresholdDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(12.2, false, 0L, now))
    @Test fun healthyDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(12.8, false, 0L, now))
    @Test fun engineRunningDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(11.9, true, 0L, now))
    @Test fun noReadingDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(null, false, 0L, now))
    @Test fun within24hDoesNotRepeat() = assertFalse(BatteryAlert.shouldWarn(12.0, false, now - 23 * h, now))
    @Test fun after24hRepeats() = assertTrue(BatteryAlert.shouldWarn(12.0, false, now - 24 * h, now))
    @Test fun rearmedAfterRecoveryWarnsAgain() = assertTrue(BatteryAlert.shouldWarn(12.0, false, now - 2 * h, now, rearmed = true))
    @Test fun clockWentBackwardsWarns() = assertTrue(BatteryAlert.shouldWarn(12.0, false, now + h, now))
    @Test fun rearmFlag() {
        assertFalse(BatteryAlert.nextRearmed(12.6, false))
        assertTrue(BatteryAlert.nextRearmed(12.7, false))
        assertTrue(BatteryAlert.nextRearmed(12.0, true))
        assertFalse(BatteryAlert.nextRearmed(null, false))
        assertEquals(12.2, BatteryAlert.WARN_BELOW_VOLTS, 0.0)
    }
}
