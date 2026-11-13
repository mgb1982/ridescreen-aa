// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Whether an Android Auto session that just ended should be brought back (pure, unit tested).
 * Parking stops the receiver on purpose, which ends the AA session; that must not be taken for a
 * drop (13 Nov log: "parking Android Auto" was followed by "bringing it back (try 1/3)").
 */
internal object AaDropPolicy {
    fun mayRelaunch(parked: Boolean, stopped: Boolean): Boolean = !parked && !stopped
}
