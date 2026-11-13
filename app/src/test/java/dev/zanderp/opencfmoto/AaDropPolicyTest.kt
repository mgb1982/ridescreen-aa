// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaDropPolicyTest {
    @Test fun aRealDropIsBroughtBack() = assertTrue(AaDropPolicy.mayRelaunch(parked = false, stopped = false))
    @Test fun parkedIsNeverRelaunched() = assertFalse(AaDropPolicy.mayRelaunch(parked = true, stopped = false))
    @Test fun stoppedIsNeverRelaunched() = assertFalse(AaDropPolicy.mayRelaunch(parked = false, stopped = true))
    @Test fun parkedAndStoppedIsNeverRelaunched() = assertFalse(AaDropPolicy.mayRelaunch(parked = true, stopped = true))
}
