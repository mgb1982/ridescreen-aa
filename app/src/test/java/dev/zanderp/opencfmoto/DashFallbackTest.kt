package dev.zanderp.opencfmoto

import dev.zanderp.opencfmoto.DashboardMode.Design as D
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashFallbackTest {
    private val obdAll = setOf("rpm", "speed", "coolant", "volt", "load", "intake", "baro", "absolute", "throttle")
    private val phoneAll = setOf("clock", "trip", "moving", "avg", "max", "battery", "turn", "remaining", "alt", "heading", "outside")
    private val noNav = phoneAll - setOf("turn", "remaining")

    private fun data(
        rpm: Double? = null, nextTurnM: Int? = null, headingDeg: Double? = null, tripKm: Double? = null,
    ) = DashboardMode.Data(
        rpm, 24.0, true, null, null, null, null, null, null, null, null, false,
        clockMs = 0L, tripKm = tripKm, nextTurnM = nextTurnM, headingDeg = headingDeg, noObd = true,
    )

    // ── when

    @Test fun noAdapterIsNoObdAtOnce() {
        assertTrue(DashFallback.noObd(false, 0))
        assertTrue(DashFallback.noObd(false, 5_000))
    }

    @Test fun adapterNeedsThirtySecondsOfSilence() {
        assertFalse(DashFallback.noObd(true, 0))
        assertFalse(DashFallback.noObd(true, 29_999))
        assertTrue(DashFallback.noObd(true, 30_000))
        assertTrue(DashFallback.noObd(true, 120_000))
    }

    @Test fun warningOnlyForRidersWithAnAdapter() {
        assertTrue(DashFallback.showWarning(true, false))
        assertFalse(DashFallback.showWarning(true, true))
        assertFalse(DashFallback.showWarning(false, false))
    }

    // ── what

    @Test fun availableNeedsDataAndNavigation() {
        assertEquals(setOf("clock"), DashFallback.available(data()))
        val nav = DashFallback.available(data(nextTurnM = 300, headingDeg = 10.0, tripKm = 1.0))
        assertEquals(setOf("clock", "turn", "heading", "trip"), nav)
    }

    @Test fun sportClockTakesRpmDialAndCardsFollowPreferenceWithoutClock() {
        val p = DashFallback.plan(D.SPORT, obdAll, noNav)
        assertTrue(p.clockDial)
        // coolant, volt, throttle, load, intake, baro get trip, avg, battery, max, moving, outside
        assertEquals(
            mapOf("coolant" to "trip", "volt" to "avg", "throttle" to "battery", "load" to "max", "intake" to "moving", "baro" to "outside"),
            p.subs,
        )
    }

    @Test fun turnAndRemainingOnlyWhenNavigating() {
        val noNavPlan = DashFallback.plan(D.CLEAN, setOf("speed", "coolant", "volt", "throttle"), noNav)
        assertFalse("turn" in noNavPlan.subs.values)
        val nav = DashFallback.plan(D.PIT, setOf("speed", "coolant", "volt", "load", "intake"), phoneAll)
        // clock is free in Pit (no dial): clock, trip, avg, battery, then turn would be next
        assertEquals(mapOf("coolant" to "clock", "volt" to "trip", "load" to "avg", "intake" to "battery"), nav.subs)
    }

    @Test fun whatTheRiderAlreadyShowsIsNotRepeated() {
        val p = DashFallback.plan(D.CLASSIC, setOf("rpm", "speed", "coolant", "volt", "trip"), noNav)
        assertEquals(mapOf("coolant" to "avg", "volt" to "battery"), p.subs)   // clock is the dial, trip is already visible
        assertEquals(p.subs.values.size, p.subs.values.toSet().size)
    }

    @Test fun slotsWithoutASubstituteAreHidden() {
        val p = DashFallback.plan(D.SPORT, obdAll, setOf("clock"))
        assertTrue(p.clockDial)
        assertTrue(p.subs.isEmpty())   // the clock is the dial: nothing else to show
        val q = DashFallback.plan(D.PIT, setOf("coolant", "volt", "load"), setOf("clock"))
        assertEquals(mapOf("coolant" to "clock"), q.subs)
    }

    @Test fun racingHidesTheBarAndSubstitutesTheThrottleDial() {
        val p = DashFallback.plan(D.RACING, obdAll, noNav)
        assertFalse(p.clockDial)
        assertTrue(p.speedBig)
        assertEquals("avg", p.subs["throttle"])
        assertEquals("clock", p.subs["coolant"])
    }

    @Test fun touringThrottleDialIsCompassWithHeadingElseClock() {
        val withHeading = DashFallback.plan(D.TOURING, obdAll, noNav + "heading")
        assertTrue(withHeading.compass)
        assertFalse(withHeading.touringClock)
        assertFalse("throttle" in withHeading.subs)
        assertFalse("heading" in withHeading.subs.values)
        assertTrue("clock" in withHeading.subs.values)
        val without = DashFallback.plan(D.TOURING, obdAll, noNav - "heading")
        assertTrue(without.touringClock)
        assertFalse("clock" in without.subs.values)
    }

    @Test fun focusKeepsTimeInTheRpmCorner() {
        val p = DashFallback.plan(D.FOCUS, obdAll, noNav)
        assertTrue(p.clockDial)
        assertEquals(mapOf("coolant" to "trip", "volt" to "avg"), p.subs)
    }

    @Test fun designsThatDrawNoRpmDialDoNotGetAClockDial() {
        for (d in listOf(D.CLEAN, D.PIT, D.WINDOW, D.RACING, D.TOURING)) assertFalse("$d", DashFallback.plan(d, obdAll, noNav).clockDial)
    }

    @Test fun rpmHiddenByTheRiderMeansNoClockDial() {
        assertFalse(DashFallback.plan(D.SPORT, obdAll - "rpm", noNav).clockDial)
    }

    @Test fun everyDesignHasASlotSet() {
        for (d in D.entries) assertTrue("$d", DashFallback.drawnSlots(d).isNotEmpty())
    }

    @Test fun cardIdsMapObdCardsAndAppendPickedPhoneOnes() {
        val order = listOf("coolant", "volt", "load")
        assertEquals(listOf("coolant", "volt", "load"), DashFallback.cardIds(order, setOf("coolant", "volt", "load"), null))
        assertEquals(listOf("coolant", "volt", "load", "trip"), DashFallback.cardIds(order, setOf("coolant", "volt", "load", "trip"), null))
        val plan = DashFallback.Plan(false, false, false, mapOf("coolant" to "clock", "load" to "avg"), false)
        assertEquals(listOf("clock", "avg", "trip"), DashFallback.cardIds(order, setOf("coolant", "volt", "load", "trip"), plan))
        // A picked reading that is also a substitute is listed once.
        assertEquals(listOf("clock"), DashFallback.cardIds(listOf("coolant"), setOf("coolant", "clock"), DashFallback.Plan(false, false, false, mapOf("coolant" to "clock"), false)))
    }

    @Test fun planKeyChangesWithTheSubstitutes() {
        val a = DashFallback.plan(D.SPORT, obdAll, noNav)
        val b = DashFallback.plan(D.SPORT, obdAll, noNav - "trip")
        assertNotEquals(a.key, b.key)
    }

    @Test fun phoneFieldsAreAfterTheObdOnesAndOptIn() {
        assertEquals(11, DashFallback.PHONE_IDS.size)
        assertEquals(DashFallback.PHONE_IDS.toSet(), phoneAll)
    }

    // ── signature

    @Test fun signatureFollowsPhoneReadings() {
        fun sig(d: DashboardMode.Data) = DashboardMode.signatureOf(d, false, "SPORT", "rpm", "10:00")
        assertNotEquals(sig(data(tripKm = 1.0)), sig(data(tripKm = 1.2)))
        assertEquals(sig(data(tripKm = 1.01)), sig(data(tripKm = 1.02)))
        assertNotEquals(sig(data()), sig(data(nextTurnM = 200)))
    }

    // ── clock and text

    @Test fun handAnglesAtThreeOClock() {
        val ms = (3 * 3600L) * 1000
        val h = DashPhoneMath.hands(ms, 0)
        assertEquals(90f, h.hour, 0.01f)
        assertEquals(0f, h.minute, 0.01f)
        assertEquals(0f, h.second, 0.01f)
    }

    @Test fun handAnglesAtTenPastTenThirtyFive() {
        val ms = (10 * 3600L + 10 * 60 + 35) * 1000
        val h = DashPhoneMath.hands(ms, 0)
        assertEquals(305f, h.hour, 0.01f)   // 10h10: 300 + 10/60 of 30
        assertEquals((10 + 35 / 60f) * 6f, h.minute, 0.01f)
        assertEquals(210f, h.second, 0.01f)
    }

    @Test fun handsUseTheOffsetAndWrapPastMidnight() {
        // 23:30 UTC with +2 h is 01:30 local.
        val ms = (23 * 3600L + 30 * 60) * 1000
        val h = DashPhoneMath.hands(ms, 2 * 3_600_000)
        assertEquals(45f, h.hour, 0.01f)
        assertEquals(180f, h.minute, 0.01f)
        assertEquals("01:30", DashPhoneMath.clockText(ms, 2 * 3_600_000))
        // Before 1970 with a negative offset still wraps into a valid day.
        assertEquals("22:00", DashPhoneMath.clockText(0L, -2 * 3_600_000))
    }

    @Test fun movingTime() {
        assertEquals("0:00" to "min", DashPhoneMath.movingText(0))
        assertEquals("12:34" to "min", DashPhoneMath.movingText((12 * 60 + 34) * 1000L))
        assertEquals("1:05" to "h", DashPhoneMath.movingText((65 * 60) * 1000L))
        assertEquals("0:00" to "min", DashPhoneMath.movingText(-5))
    }

    @Test fun turnDistance() {
        assertEquals("350" to "m", DashPhoneMath.turnText(350, Locale.US))
        assertEquals("350" to "m", DashPhoneMath.turnText(347, Locale.US))
        assertEquals("0" to "m", DashPhoneMath.turnText(4, Locale.US))
        assertEquals("1.2" to "km", DashPhoneMath.turnText(1234, Locale.US))
        assertEquals("1,2" to "km", DashPhoneMath.turnText(1234, Locale.GERMANY))
    }

    @Test fun averageSpeed() {
        assertNull(DashPhoneMath.avgKmh(100.0, 5_000))
        assertEquals(24.0, DashPhoneMath.avgKmh(12_400.0, (12.4 / 24.0 * 3_600_000.0).toLong())!!, 0.01)
    }

    @Test fun compass() {
        assertEquals("N", DashPhoneMath.compassPoint(0.0))
        assertEquals("N", DashPhoneMath.compassPoint(359.0))
        assertEquals("NE", DashPhoneMath.compassPoint(48.0))
        assertEquals("E", DashPhoneMath.compassPoint(90.0))
        assertEquals("SW", DashPhoneMath.compassPoint(225.0))
        assertEquals("NW", DashPhoneMath.compassPoint(315.0))
        assertEquals("045", DashPhoneMath.degText(45.4))
        assertEquals("000", DashPhoneMath.degText(359.8))
    }

    @Test fun headingIsKeptForAMinute() {
        assertEquals(90.0, DashPhoneMath.keepHeading(90.0, 10.0, 1_000)!!, 0.0)
        assertEquals(10.0, DashPhoneMath.keepHeading(null, 10.0, 59_000)!!, 0.0)
        assertNull(DashPhoneMath.keepHeading(null, 10.0, 61_000))
        assertNull(DashPhoneMath.keepHeading(null, null, 0))
    }

    @Test fun sampleForPreviewsIsCoherent() {
        val s = DashboardMode.sampleNoObd(0L)
        assertTrue(s.noObd)
        assertFalse(s.obdConfigured)
        assertEquals(12.4, s.tripKm!!, 0.0)
        assertEquals(24.0, s.avgKmh!!, 0.0)
        assertEquals(76, s.phoneBatteryPct)
        assertEquals(350, s.nextTurnM)
        assertEquals(31, (s.tripMovingMs!! / 60_000L).toInt())
        assertEquals(phoneAll, DashFallback.available(s))
    }
}
