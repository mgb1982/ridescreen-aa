// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.ParkSpot.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkSpotTest {
    private val now = 1_000_000_000_000L
    private val bike = Fix(41.3700, 2.1300, 5, now - 50_000L)        // where the bike stopped
    private val walked = Fix(41.3704, 2.1305, 4, now)                // 50 s later, rider walked off

    @Test fun theSpotWhereTheLinkWasUpBeatsThePositionAfterWalkingOff() =
        assertEquals(bike, ParkSpot.pick(bike, walked, now))

    @Test fun withoutALinkedPositionTheCurrentOneIsUsed() =
        assertEquals(walked, ParkSpot.pick(null, walked, now))

    @Test fun aStaleLinkedPositionIsIgnored() {
        val old = bike.copy(timeMs = now - ParkSpot.MAX_LINKED_AGE_MS - 1)
        assertEquals(walked, ParkSpot.pick(old, walked, now))
    }

    @Test fun exactlyAtTheLimitStillCounts() {
        val edge = bike.copy(timeMs = now - ParkSpot.MAX_LINKED_AGE_MS)
        assertEquals(edge, ParkSpot.pick(edge, walked, now))
    }

    @Test fun aLinkedPositionFromTheFutureIsIgnored() =
        assertEquals(walked, ParkSpot.pick(bike.copy(timeMs = now + 1), walked, now))

    @Test fun nothingAtAllGivesNothing() = assertNull(ParkSpot.pick(null, null, now))

    @Test fun linkedPositionIsUsedEvenWithoutAFixNow() =
        assertEquals(bike, ParkSpot.pick(bike, null, now))
}
