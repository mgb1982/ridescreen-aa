package dev.zanderp.opencfmoto.aa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaBtPickTest {
    private val carkit = AaBtPick.Dev("Carkit", "AA:AA:AA:AA:A1:7C", false)
    private val dash = AaBtPick.Dev("ZT851066", "BB:BB:BB:BB:10:66", false)
    private val obd = AaBtPick.Dev("Android-Vlink", "CC:CC:CC:CC:4E:31", true)

    @Test fun zontesDashNameIsABike() {
        assertTrue(AaBtPick.isBikeName("ZT851066"))
        assertTrue(AaBtPick.isBikeName("Zontes 125X"))
        assertFalse(AaBtPick.isBikeName("Carkit"))
        assertFalse(AaBtPick.isBikeName("Android-Vlink"))
    }

    @Test fun savedBikeWinsEvenBeforeItConnects() =
        assertEquals(dash, AaBtPick.pick(listOf(carkit, dash, obd), "bb:bb:bb:bb:10:66", "Android-Vlink", false))

    @Test fun obdAdapterIsNeverPicked() =
        assertEquals(dash, AaBtPick.pick(listOf(obd, carkit, dash), null, "Android-Vlink", false))

    @Test fun connectedBikeBeatsOtherConnected() {
        val watch = AaBtPick.Dev("Galaxy Watch", "DD:DD:DD:DD:00:01", true)
        val dashOn = dash.copy(connected = true)
        assertEquals(dashOn, AaBtPick.pick(listOf(watch, dashOn), null, null, false))
    }

    @Test fun onlyTheObdAdapterMeansNothing() =
        assertEquals(null, AaBtPick.pick(listOf(obd), null, "Android-Vlink", false))
}
