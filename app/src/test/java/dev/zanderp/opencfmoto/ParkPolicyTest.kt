// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkPolicyTest {
    @Test fun aDroppedLinkParksAfterThreeMinutes() = assertEquals(180_000L, ParkPolicy.graceMs(sawStream = true))
    @Test fun aBikeThatNeverLinkedParksAfterFiveMinutes() = assertEquals(300_000L, ParkPolicy.graceMs(sawStream = false))
    @Test fun neverLinkedWaitsLonger() = assertTrue(ParkPolicy.graceMs(false) > ParkPolicy.graceMs(true))
}
