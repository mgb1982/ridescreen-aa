// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.LockEndsTrip.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockEndsTripTest {
    private val lock = PkeUnlock.Action.LOCK

    @Test
    fun confirmedLockStandingStillEndsTheRide() {
        assertEquals(Decision.END, LockEndsTrip.decide(true, lock, ok = true, mismatch = false, speedKmh = 0.0, engineRunning = false))
        assertEquals(Decision.END, LockEndsTrip.decide(true, lock, ok = true, mismatch = false, speedKmh = null, engineRunning = null))
    }

    @Test
    fun failedOrUnconfirmedLockDoesNothing() {
        assertEquals(Decision.SKIP_NOT_LOCKED, LockEndsTrip.decide(true, lock, ok = false, mismatch = false, speedKmh = 0.0, engineRunning = false))
        assertEquals(Decision.SKIP_NOT_LOCKED, LockEndsTrip.decide(true, lock, ok = true, mismatch = true, speedKmh = 0.0, engineRunning = false))
        assertEquals(Decision.SKIP_NOT_LOCKED, LockEndsTrip.decide(true, PkeUnlock.Action.UNLOCK, ok = true, mismatch = false, speedKmh = 0.0, engineRunning = false))
    }

    @Test
    fun movingOrEngineOnKeepsTheRide() {
        assertEquals(Decision.SKIP_MOVING, LockEndsTrip.decide(true, lock, ok = true, mismatch = false, speedKmh = 12.0, engineRunning = false))
        assertEquals(Decision.END, LockEndsTrip.decide(true, lock, ok = true, mismatch = false, speedKmh = 5.0, engineRunning = false))
        assertEquals(Decision.SKIP_ENGINE, LockEndsTrip.decide(true, lock, ok = true, mismatch = false, speedKmh = 0.0, engineRunning = true))
    }

    @Test
    fun optionOff() {
        assertEquals(Decision.SKIP_OFF, LockEndsTrip.decide(false, lock, ok = true, mismatch = false, speedKmh = 0.0, engineRunning = false))
    }

    @Test
    fun lastExitOnlyReportsKillsWhileInUse() {
        assertTrue(LastExit.worthReporting(3, 125))    // low memory while projecting
        assertTrue(LastExit.worthReporting(3, 100))
        assertFalse(LastExit.worthReporting(3, 400))   // cached in the background: normal
        assertFalse(LastExit.worthReporting(2, 300))
        assertTrue(LastExit.worthReporting(5, 400))    // native crash: always
        assertTrue(LastExit.worthReporting(6, 400))    // ANR: always
        assertFalse(LastExit.worthReporting(10, 100))  // user requested: never
    }

    @Test
    fun obdLiveEngineThreshold() {
        assertFalse(ObdLive.engineFromRpm(0.0))
        assertFalse(ObdLive.engineFromRpm(250.0))
        assertTrue(ObdLive.engineFromRpm(1700.0))
    }

    @Test
    fun lockRefusedWhileMoving() {
        assertTrue(LockEndsTrip.tooFastToLock(obdKmh = 2.0, gpsKmh = null))
        assertTrue(LockEndsTrip.tooFastToLock(obdKmh = 40.0, gpsKmh = 0.0))   // OBD wins over GPS
        assertFalse(LockEndsTrip.tooFastToLock(obdKmh = 0.0, gpsKmh = 30.0))  // OBD says stopped
        assertFalse(LockEndsTrip.tooFastToLock(obdKmh = 1.0, gpsKmh = null))  // 1 km/h is still allowed
        assertTrue(LockEndsTrip.tooFastToLock(obdKmh = null, gpsKmh = 12.0))
        assertFalse(LockEndsTrip.tooFastToLock(obdKmh = null, gpsKmh = 2.0))  // GPS noise at a standstill
    }

    @Test
    fun lockNeverBlockedWithoutSpeedData() {
        assertFalse(LockEndsTrip.tooFastToLock(obdKmh = null, gpsKmh = null))
    }

    @Test
    fun engineRunningDoesNotBlockTheLock() {
        // Only speed refuses a lock; the engine state only decides whether the ride ends afterwards.
        assertFalse(LockEndsTrip.tooFastToLock(obdKmh = 0.0, gpsKmh = null))
        assertEquals(Decision.SKIP_ENGINE, LockEndsTrip.decide(true, lock, ok = true, mismatch = false, speedKmh = 0.0, engineRunning = true))
    }
}
