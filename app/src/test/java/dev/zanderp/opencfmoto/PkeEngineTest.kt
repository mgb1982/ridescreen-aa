// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class PkeEngineTest {
    private fun run(vararg volts: Float): Boolean? =
        volts.fold(PkeBattery.Engine(null, 0, 0)) { e, v -> PkeBattery.nextEngine(e, v) }.running

    @Test
    fun oneChargingReadingTurnsTheEngineOn() {
        assertEquals(true, run(12.7f, 14.7f))
    }

    @Test
    fun goingOffNeedsTwoLowReadingsOnceKnown() {
        assertEquals(true, run(14.7f, 12.6f))
        assertEquals(false, run(14.7f, 12.6f, 12.6f))
    }

    @Test
    fun oneLowReadingIsEnoughWhileUnknown() {
        assertEquals(false, run(12.7f))
    }

    @Test
    fun aReadingBetweenThresholdsChangesNothing() {
        assertEquals(null, run(13.0f))
        assertEquals(true, run(14.0f, 13.0f, 12.6f))
    }
}
