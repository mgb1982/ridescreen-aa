// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavFeedTest {
    @Test fun advertiseWhenEitherIsOn() {
        assertFalse(NavFeedLogic.shouldAdvertise(false, false))
        assertTrue(NavFeedLogic.shouldAdvertise(true, false))
        assertTrue(NavFeedLogic.shouldAdvertise(false, true))
        assertTrue(NavFeedLogic.shouldAdvertise(true, true))
    }

    @Test fun freshnessWindow() {
        assertTrue(NavFeedLogic.isFresh(0))
        assertTrue(NavFeedLogic.isFresh(15_000))
        assertFalse(NavFeedLogic.isFresh(-1))
        assertFalse(NavFeedLogic.isFresh(15_001))
    }
}
