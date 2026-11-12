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
    private val min = 60_000L

    // At parking (under load): only below 12.0 V.
    @Test fun clearlyLowAtParkingWarns() = assertTrue(BatteryAlert.shouldWarn(11.9, false, 0L, now))
    @Test fun loadDipAtParkingDoesNotWarn() {
        // 12 Nov: a healthy 125X read 12.3 V with dash + OBD on, 12.8 V at rest.
        assertFalse(BatteryAlert.shouldWarn(12.3, false, 0L, now))
        assertFalse(BatteryAlert.shouldWarn(12.1, false, 0L, now))
        assertFalse(BatteryAlert.shouldWarn(12.0, false, 0L, now))
    }

    // At rest (10 min or more after parking): below 12.2 V.
    @Test fun lowAtRestWarns() = assertTrue(BatteryAlert.shouldWarn(12.1, false, 0L, now, atRest = true))
    @Test fun atThresholdAtRestDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(12.2, false, 0L, now, atRest = true))
    @Test fun healthyDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(12.8, false, 0L, now, atRest = true))

    @Test fun engineRunningDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(11.5, true, 0L, now, atRest = true))
    @Test fun noReadingDoesNotWarn() = assertFalse(BatteryAlert.shouldWarn(null, false, 0L, now, atRest = true))
    @Test fun within24hDoesNotRepeat() = assertFalse(BatteryAlert.shouldWarn(11.8, false, now - 23 * h, now))
    @Test fun after24hRepeats() = assertTrue(BatteryAlert.shouldWarn(11.8, false, now - 24 * h, now))
    @Test fun rearmedAfterRecoveryWarnsAgain() = assertTrue(BatteryAlert.shouldWarn(11.8, false, now - 2 * h, now, rearmed = true))
    @Test fun clockWentBackwardsWarns() = assertTrue(BatteryAlert.shouldWarn(11.8, false, now + h, now))

    @Test fun restStartsTenMinutesAfterParking() {
        assertFalse(BatteryAlert.isAtRest(0L, now))                 // never parked
        assertFalse(BatteryAlert.isAtRest(now, now + 9 * min))
        assertTrue(BatteryAlert.isAtRest(now, now + 10 * min))
        assertFalse(BatteryAlert.isAtRest(now, now - min))          // reading older than the parking
    }

    @Test fun rearmFlag() {
        assertFalse(BatteryAlert.nextRearmed(12.6, false))
        assertTrue(BatteryAlert.nextRearmed(12.7, false))
        assertTrue(BatteryAlert.nextRearmed(12.0, true))
        assertFalse(BatteryAlert.nextRearmed(null, false))
        assertEquals(12.2, BatteryAlert.WARN_BELOW_VOLTS, 0.0)
        assertEquals(12.0, BatteryAlert.WARN_AT_PARKING_VOLTS, 0.0)
    }
}
