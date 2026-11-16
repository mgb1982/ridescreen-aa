// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import java.util.Locale

/**
 * Dashboard without OBD: when the rider has no ELM327 set up (or it has been silent for a while) the cards
 * that would show "—" are replaced by readings the phone already has (time, trip, battery, next turn…) and the
 * RPM dials become a clock. Pure Kotlin, no Android types, unit tested on the JVM.
 */
internal object DashFallback {
    /** No OBD reading for this long (with an adapter configured) switches to the phone layout; shorter gaps keep the "—". */
    const val OBD_LOST_MS = 30_000L

    /** Ids of the phone readings, in the order they are offered as dashboard fields (after the OBD ones). */
    val PHONE_IDS = listOf("clock", "trip", "moving", "avg", "max", "battery", "turn", "remaining", "alt", "heading", "outside")

    /** Which phone reading takes the place of an OBD card first. */
    private val PREFERENCE = listOf("clock", "trip", "avg", "battery", "turn", "remaining", "max", "moving", "outside", "alt", "heading")

    /** OBD slots that get a substitute, in the order they are replaced. RPM is handled by the design itself. */
    private val SLOT_ORDER = listOf("coolant", "volt", "throttle", "load", "intake", "baro")

    /** "No OBD mode": no adapter configured, or no reading for [OBD_LOST_MS]. */
    fun noObd(adapterConfigured: Boolean, msSinceReading: Long): Boolean =
        !adapterConfigured || msSinceReading >= OBD_LOST_MS

    /** The "No OBD data" notice only makes sense for a rider who has an adapter configured. */
    fun showWarning(adapterConfigured: Boolean, obdLive: Boolean): Boolean = adapterConfigured && !obdLive

    /** Phone readings that have a value now. "turn" and "remaining" exist only while navigating. */
    fun available(d: DashboardMode.Data): Set<String> {
        val out = LinkedHashSet<String>()
        if (d.clockMs != null) out += "clock"
        if (d.tripKm != null) out += "trip"
        if (d.tripMovingMs != null) out += "moving"
        if (d.avgKmh != null) out += "avg"
        if (d.maxKmh != null) out += "max"
        if (d.phoneBatteryPct != null) out += "battery"
        if (d.nextTurnM != null) out += "turn"
        if (d.navRemainingKm != null) out += "remaining"
        if (d.altitudeM != null) out += "alt"
        if (d.headingDeg != null) out += "heading"
        if (d.outsideC != null) out += "outside"
        return out
    }

    /** The OBD cards and dials each design draws, by id (RPM aside). */
    fun drawnSlots(design: DashboardMode.Design): Set<String> = when (design) {
        DashboardMode.Design.SPORT, DashboardMode.Design.RACING ->
            setOf("coolant", "volt", "load", "intake", "baro", "throttle")
        DashboardMode.Design.TOURING, DashboardMode.Design.PIT, DashboardMode.Design.WINDOW ->
            setOf("coolant", "volt", "load", "intake", "baro")
        DashboardMode.Design.CLEAN -> setOf("coolant", "volt", "throttle")
        DashboardMode.Design.CLASSIC, DashboardMode.Design.FOCUS -> setOf("coolant", "volt")
    }

    /**
     * What replaces the OBD parts of a design.
     * [clockDial]: the RPM dial is an analog clock (Sport, Classic); Focus shows the time in the corner where its RPM was. [compass]/[touringClock]: Touring's round
     * throttle dial is a compass (when there is a heading) or an analog clock. [subs]: OBD slot id to the
     * phone reading drawn in its place; slots without a substitute are hidden. Shift lights are always off
     * and the RPM bars/arcs are hidden. [speedBig]: Racing gives the freed RPM bar to the speed plate (there is
     * no reliable route progress to draw in it).
     */
    data class Plan(
        val clockDial: Boolean, val compass: Boolean, val touringClock: Boolean,
        val subs: Map<String, String>, val speedBig: Boolean,
    ) {
        /** Part of the renderer's cache key: anything here changes what the cached static layer holds. */
        val key: String get() = "fb:$clockDial:$compass:$touringClock:$speedBig:" + subs.entries.joinToString(",") { "${it.key}>${it.value}" }
    }

    /**
     * [visible]: the rider's fields. [available]: phone readings with data ([available]). A reading the rider
     * already shows is not repeated, and one drawn as a dial (clock, compass) is not offered as a card.
     */
    fun plan(design: DashboardMode.Design, visible: Set<String>, available: Set<String>): Plan {
        val touring = design == DashboardMode.Design.TOURING
        val clockDial = "rpm" in visible &&
            design in setOf(DashboardMode.Design.SPORT, DashboardMode.Design.CLASSIC, DashboardMode.Design.FOCUS)
        val touringDial = touring && "throttle" in visible
        val compass = touringDial && "heading" in available
        val touringClock = touringDial && !compass
        val excluded = HashSet<String>()
        excluded += visible.filter { it in PHONE_IDS }
        if (clockDial || touringClock) excluded += "clock"
        if (compass) excluded += "heading"
        val slots = SLOT_ORDER.filter { it in visible && it in drawnSlots(design) && !(touring && it == "throttle") }
        val candidates = PREFERENCE.filter { it !in excluded && it in available }
        return Plan(clockDial, compass, touringClock, slots.zip(candidates).toMap(), speedBig = design == DashboardMode.Design.RACING)
    }

    /**
     * Cards in drawing order. [obdCards]: the rider's visible OBD cards in their order. Without a plan they
     * stay as they are; with one each becomes its substitute (or drops out). The phone readings the rider
     * picked follow in both cases.
     */
    fun cardIds(obdCards: List<String>, visible: Set<String>, plan: Plan?): List<String> {
        val head = if (plan == null) obdCards else obdCards.mapNotNull { plan.subs[it] }
        return (head + PHONE_IDS.filter { it in visible }).distinct()
    }
}

/** Pure maths and text for the phone readings and the analog clock (unit tested). */
internal object DashPhoneMath {
    /** Hand angles in degrees clockwise from 12 o'clock. */
    data class Hands(val hour: Float, val minute: Float, val second: Float)

    /** Local hours, minutes, seconds of an epoch time. */
    fun localHms(clockMs: Long, offsetMs: Int): Triple<Int, Int, Int> {
        val s = Math.floorMod(Math.floorDiv(clockMs + offsetMs, 1000L), 86_400L).toInt()
        return Triple(s / 3600, (s / 60) % 60, s % 60)
    }

    fun hands(clockMs: Long, offsetMs: Int): Hands {
        val (h, m, s) = localHms(clockMs, offsetMs)
        return Hands(
            hour = ((h % 12) + m / 60f) * 30f,
            minute = (m + s / 60f) * 6f,
            second = s * 6f,
        )
    }

    fun clockText(clockMs: Long, offsetMs: Int): String {
        val (h, m, _) = localHms(clockMs, offsetMs)
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    /** Average moving speed; null until there are 10 s of movement to divide by. */
    fun avgKmh(distanceM: Double, movingMs: Long): Double? =
        if (movingMs < 10_000L) null else distanceM / 1000.0 / (movingMs / 3_600_000.0)

    /** Moving time as (text, unit): "12:34" min under an hour, "1:05" h after. */
    fun movingText(ms: Long): Pair<String, String> {
        val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
        return if (total < 3600) String.format(Locale.US, "%d:%02d", total / 60, total % 60) to "min"
        else String.format(Locale.US, "%d:%02d", total / 3600, (total / 60) % 60) to "h"
    }

    /** Distance to the next turn as (text, unit): metres rounded to 10 under 1 km, then km with one decimal. */
    fun turnText(meters: Int, locale: Locale): Pair<String, String> =
        if (meters < 1000) (Math.round(meters / 10.0) * 10).toInt().toString() to "m"
        else String.format(locale, "%.1f", meters / 1000.0) to "km"

    private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** Eight-point compass name of a bearing. */
    fun compassPoint(deg: Double): String = POINTS[(Math.floorMod(Math.round(deg / 45.0).toInt(), 8))]

    /** Bearing as three digits: "045". */
    fun degText(deg: Double): String = String.format(Locale.US, "%03d", Math.floorMod(Math.round(deg).toInt(), 360))

    /** GPS bearing is noise at a standstill: keep the last good one up to [maxAgeMs] instead of flicking to "no heading". */
    fun keepHeading(fresh: Double?, last: Double?, lastAgeMs: Long, maxAgeMs: Long = 60_000L): Double? =
        fresh ?: last?.takeIf { lastAgeMs in 0..maxAgeMs }
}
