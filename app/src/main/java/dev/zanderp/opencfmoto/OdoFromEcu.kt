// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure decision for taking the bike's mileage from the ECU (OBD PID 31, two bytes, so 0..65535 km).
 * Nothing is applied before the rider said yes, and a value that goes down or jumps is never applied silently.
 */
internal object OdoFromEcu {
    const val MAX_KM = 65_535
    const val MAX_JUMP_KM = 2_000
    const val ASSUMED_KMH = 150

    enum class State { UNASKED, ACCEPTED, DECLINED }

    sealed class Decision {
        /** Not a valid 2-byte reading. */
        object Invalid : Decision()
        /** First reading and the rider was not asked yet: ask. */
        object Ask : Decision()
        /** The rider said no: only remember the reading. */
        object Skip : Decision()
        /** Accepted and plausible: set the odometer to the new value. */
        object Apply : Decision()
        /** Accepted and identical to the last applied value: nothing to do (keeps the km counted by the app). */
        object Same : Decision()
        /** Went down or jumped: change nothing, warn the rider next time they open Maintenance. */
        class Reject(val from: Int, val to: Int) : Decision()
    }

    /** The largest rise accepted after [elapsedMs]: what 150 km/h covers in that time, but never less than [MAX_JUMP_KM]. */
    fun maxRiseKm(elapsedMs: Long?): Long {
        if (elapsedMs == null || elapsedMs <= 0) return MAX_JUMP_KM.toLong()
        return maxOf(MAX_JUMP_KM.toLong(), elapsedMs / 3_600_000L * ASSUMED_KMH)
    }

    /**
     * [currentKm] is the odometer the app shows now (a manual correction counts too): an accepted ECU value below it is
     * never applied silently, whatever was applied before.
     */
    fun decide(state: State, appliedKm: Int?, appliedAtMs: Long?, newKm: Int, nowMs: Long, currentKm: Int? = null): Decision {
        if (newKm !in 0..MAX_KM) return Decision.Invalid
        return when (state) {
            State.UNASKED -> Decision.Ask
            State.DECLINED -> Decision.Skip
            State.ACCEPTED -> when {
                currentKm != null && newKm < currentKm -> Decision.Reject(currentKm, newKm)
                appliedKm == null -> Decision.Apply
                newKm == appliedKm -> Decision.Same
                newKm < appliedKm -> Decision.Reject(appliedKm, newKm)
                newKm - appliedKm <= maxRiseKm(appliedAtMs?.let { nowMs - it }) -> Decision.Apply
                else -> Decision.Reject(appliedKm, newKm)
            }
        }
    }
}
