// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * v2.6 · pure maths of the four extra dash designs (Clean, Pit, Classic, Focus): dial angles, arc and
 * bar fill, tick scales and the Focus hero. No Android types, unit tested on the JVM.
 */
internal object DashDesignMath {
    /** Gauges start at 135 degrees (bottom left) and sweep 270 clockwise, as the other styles do. */
    const val ARC_START = 135f
    const val ARC_SWEEP = 270f

    /** [value] / [max] kept in 0..1; null or a bad scale counts as empty. */
    fun fraction(value: Double?, max: Double): Float =
        if (value == null || max <= 0.0 || value.isNaN()) 0f else (value / max).toFloat().coerceIn(0f, 1f)

    /** Angle in degrees of mark [i] of [n] equal divisions of the 270 degree scale. */
    fun tickAngle(i: Int, n: Int): Float = if (n <= 0) ARC_START else ARC_START + ARC_SWEEP * i / n

    /** Where the red zone starts, as a fraction of the scale. */
    fun redFraction(redFrom: Double, max: Double): Float = fraction(redFrom, max)

    /**
     * A gauge filled to [f] with the red zone from [redFrom] (both 0..1): the accent part ends at
     * [accentTo]; the red part runs from [redFrom] to [redTo] (empty, redTo == redFrom, below the zone).
     */
    data class Fill(val accentTo: Float, val redFrom: Float, val redTo: Float) {
        val hasRed: Boolean get() = redTo > redFrom
    }

    fun fill(f: Float, redFrom: Float): Fill {
        val v = f.coerceIn(0f, 1f)
        val r = redFrom.coerceIn(0f, 1f)
        return Fill(minOf(v, r), r, if (v > r) v else r)
    }

    /** Sweep in degrees of the fraction span [from]..[to]. */
    fun sweep(from: Float, to: Float): Float = (to - from).coerceAtLeast(0f) * ARC_SWEEP

    // ── Pit: segmented RPM bar

    /** Segments lit for [f] of the scale: a segment lights once the value reaches its end. */
    fun litSegments(f: Float, n: Int): Int =
        if (n <= 0) 0 else Math.floor(f.coerceIn(0f, 1f).toDouble() * n + 1e-9).toInt().coerceIn(0, n)

    /** Index of the first red segment: those ending above [redFrom]. */
    fun firstRedSegment(n: Int, redFrom: Float): Int =
        if (n <= 0) 0 else Math.floor(redFrom.coerceIn(0f, 1f).toDouble() * n + 1e-9).toInt().coerceIn(0, n)

    /** Scale labels under the bar: every 2,000 rpm from 0 to [rpmK] thousand, as (fraction, text). */
    fun barLabels(rpmK: Int): List<Pair<Float, String>> =
        if (rpmK <= 0) emptyList() else (0..rpmK step 2).map { it.toFloat() / rpmK to it.toString() }

    // ── Classic: dial scales

    /**
     * Marks of a round dial: [ticks] divisions, a long mark every [majorEvery], a number every
     * [labelEvery] long marks, [unitPerMajor] units per long mark.
     */
    data class DialScale(val ticks: Int, val majorEvery: Int, val labelEvery: Int, val unitPerMajor: Int) {
        fun isMajor(i: Int) = majorEvery > 0 && i % majorEvery == 0
        /** (mark index, number) for every labelled mark. */
        fun labels(): List<Pair<Int, Int>> =
            (0..ticks).filter { isMajor(it) && (it / majorEvery) % labelEvery == 0 }
                .map { it to (it / majorEvery) * unitPerMajor }
    }

    /** A mark every 10 km/h, long and numbered every 20. */
    fun speedScale(speedMax: Int) = DialScale(ticks = maxOf(1, speedMax / 10), majorEvery = 2, labelEvery = 1, unitPerMajor = 20)

    /** A mark every 500 rpm, long every 1,000; numbers every 1,000 (10k scale) or 2,000. */
    fun rpmScale(rpmK: Int) = DialScale(ticks = maxOf(1, rpmK * 2), majorEvery = 2, labelEvery = if (rpmK <= 10) 1 else 2, unitPerMajor = 1)
}

/** Which reading Focus makes its hero. */
internal object FocusHero {
    enum class Hero { SPEED, COOLANT, VOLT }

    /** Coolant leaves the hero spot this far below the level that put it there. */
    const val HYSTERESIS = 3.0

    /**
     * Coolant when hot ([coolantActive]: at or above the amber level, or still above the exit level),
     * the voltage when the engine is stopped (rpm 0 or unknown) and there is one, otherwise the speed.
     */
    fun pick(coolant: Double?, rpm: Double?, volt: Double?, coolantActive: Boolean, amber: Double): Hero = when {
        coolant != null && (coolant >= amber || (coolantActive && coolant >= amber - HYSTERESIS)) -> Hero.COOLANT
        (rpm == null || rpm <= 0.0) && volt != null -> Hero.VOLT
        else -> Hero.SPEED
    }
}

/** Keeps Focus from flickering between coolant and speed: enters at [amber], leaves below [amber] - 3. */
internal class FocusHeroState(private val amber: Double) {
    private var coolantActive = false

    fun update(coolant: Double?, rpm: Double?, volt: Double?): FocusHero.Hero {
        val hero = FocusHero.pick(coolant, rpm, volt, coolantActive, amber)
        coolantActive = hero == FocusHero.Hero.COOLANT
        return hero
    }
}
