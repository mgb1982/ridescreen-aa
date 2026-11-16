package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.OdoFromEcu.Decision
import dev.zanderp.opencfmoto.OdoFromEcu.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdoFromEcuTest {
    private val day = 24L * 3_600_000L
    private val now = 1_000_000_000_000L

    @Test fun firstValueAsksTheRider() = assertTrue(OdoFromEcu.decide(State.UNASKED, null, null, 1200, now) is Decision.Ask)
    @Test fun declinedOnlyRemembers() = assertTrue(OdoFromEcu.decide(State.DECLINED, null, null, 1200, now) is Decision.Skip)
    @Test fun acceptedFirstReadingApplies() = assertTrue(OdoFromEcu.decide(State.ACCEPTED, null, null, 1200, now) is Decision.Apply)
    @Test fun normalRiseApplies() = assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, now - day, 1260, now) is Decision.Apply)
    @Test fun sameValueChangesNothing() = assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, now - day, 1200, now) is Decision.Same)
    @Test fun lowerValueIsRejected() {
        val d = OdoFromEcu.decide(State.ACCEPTED, 1200, now - day, 40, now) as Decision.Reject
        assertEquals(1200, d.from); assertEquals(40, d.to)
    }
    @Test fun hugeJumpIsRejected() = assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, now - day, 9000, now) is Decision.Reject)
    @Test fun bigRiseAfterALongTimeIsPlausible() {
        // 30 days at 150 km/h would allow 108,000 km and 5 days 18,000 km: a 3,000 km rise is plausible after either
        assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, now - 30 * day, 4200, now) is Decision.Apply)
        assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, now - 5 * day, 4200, now) is Decision.Apply)
    }
    @Test fun jumpWithinTheFloorWithNoDateApplies() {
        assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, null, 3200, now) is Decision.Apply)
        assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, null, 3201, now) is Decision.Reject)
    }
    @Test fun maxRise() {
        assertEquals(2000L, OdoFromEcu.maxRiseKm(null))
        assertEquals(2000L, OdoFromEcu.maxRiseKm(3_600_000L))
        assertEquals(3600L, OdoFromEcu.maxRiseKm(24L * 3_600_000L))
    }
    @Test fun outOfRangeIsInvalid() {
        assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, null, 65_536, now) is Decision.Invalid)
        assertTrue(OdoFromEcu.decide(State.UNASKED, null, null, -1, now) is Decision.Invalid)
    }
    @Test fun twoByteMaximumIsValid() = assertTrue(OdoFromEcu.decide(State.UNASKED, null, null, 65_535, now) is Decision.Ask)

    @Test fun valueBelowTheCurrentOdometerIsNeverApplied() {
        val d = OdoFromEcu.decide(State.ACCEPTED, 1200, now - day, 1260, now, currentKm = 1500) as Decision.Reject
        assertEquals(1500, d.from); assertEquals(1260, d.to)
    }
    @Test fun manualOdometerCorrectionDownIsRespected() =
        assertTrue(OdoFromEcu.decide(State.ACCEPTED, 5000, now - day, 5010, now, currentKm = 1000) is Decision.Apply)
    @Test fun valueAtOrAboveCurrentStillApplies() {
        assertTrue(OdoFromEcu.decide(State.ACCEPTED, 1200, now - day, 1500, now, currentKm = 1500) is Decision.Apply)
        assertTrue(OdoFromEcu.decide(State.UNASKED, null, null, 10, now, currentKm = 1500) is Decision.Ask)
    }

    @Test fun gpsEstimateSlightlyAheadOfTheEcuIsCorrectedSilently() {
        val d = OdoFromEcu.decide(OdoFromEcu.State.ACCEPTED, 5_000, 0L, 5_100, 86_400_000L, currentKm = 5_140)
        org.junit.Assert.assertTrue(d is OdoFromEcu.Decision.Apply)
        val far = OdoFromEcu.decide(OdoFromEcu.State.ACCEPTED, 5_000, 0L, 5_100, 86_400_000L, currentKm = 5_400)
        org.junit.Assert.assertTrue(far is OdoFromEcu.Decision.Reject)
    }
}
