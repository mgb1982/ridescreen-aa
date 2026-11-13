// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RejoinScheduleTest {
    @Test fun firstRetryIsNearInstant() = assertEquals(300L, RejoinSchedule.pauseAfterAttempt(0, 1))
    @Test fun withinThreeMinutesKeepsTodaysBackoff() {
        assertEquals(2_500L, RejoinSchedule.pauseAfterAttempt(5_000, 2))
        assertEquals(12_500L, RejoinSchedule.pauseAfterAttempt(60_000, 6))
        assertEquals(15_000L, RejoinSchedule.pauseAfterAttempt(100_000, 7))
        assertEquals(15_000L, RejoinSchedule.pauseAfterAttempt(179_999, 40))
    }
    @Test fun afterThreeMinutesStartsAreSeventyFiveSecondsApart() {
        val pause = RejoinSchedule.pauseAfterAttempt(180_000, 8)
        assertEquals(75_000L, pause + RejoinSchedule.REQUEST_MS)
        assertEquals(pause, RejoinSchedule.pauseAfterAttempt(19 * 60_000L, 30))
    }
    @Test fun slowPeriodIsInsideSixtyToNinetySeconds() {
        val period = RejoinSchedule.pauseAfterAttempt(200_000, 9) + RejoinSchedule.REQUEST_MS
        assertTrue(period in 60_000L..90_000L)
    }
    @Test fun requestCountUntilGiveUpIsFarBelowTheOldFortyFive() {
        var t = 0L
        var n = 0
        while (t <= 20 * 60_000L) { // 20 min = BikeWifi.GIVE_UP_AFTER_MS
            n++
            t += RejoinSchedule.pauseAfterAttempt(t, n) + RejoinSchedule.REQUEST_MS
        }
        assertTrue("requests=$n", n in 15..30)
    }
}
