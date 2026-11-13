// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure reading of the keyless box's answer to an action (unit tested, no Android).
 *
 * The box answers `*AT,<pke>,10,001,<action>,<r>#`: `1` = accepted, `0` = rejected. Case 20261113 (Viti,
 * 2.4.1, no Bluetooth password set): two 'lock' commands answered `0`, the bike stayed unlocked, but the
 * app took the write as a lock and saved the parking spot, ended the ride and held auto-connect.
 */
internal object PkeReply {
    /** True only when the reply is for [actionCode] and its result field is exactly `0`. */
    fun rejected(reply: String?, actionCode: String): Boolean {
        val p = reply?.trim()?.removeSuffix("#")?.split(",") ?: return false
        if (p.firstOrNull() != "*AT") return false
        val i = p.indexOf("001")
        if (i < 1 || p.size < i + 3) return false
        return p[i + 1] == actionCode && p[i + 2] == "0"
    }

    /**
     * The bike did not end up as asked. A read-back wins when there is one (a rejected lock on a bike
     * that was already locked is not a problem); without a read-back, a rejected command counts.
     */
    fun mismatch(want: Boolean?, lockedAfter: Boolean?, rejected: Boolean): Boolean {
        if (want == null) return false
        return if (lockedAfter != null) lockedAfter != want else rejected
    }

    /** The state to show the rider when it did not end up as asked: the read-back, else the opposite of the order. */
    fun leftLocked(want: Boolean?, lockedAfter: Boolean?): Boolean = lockedAfter ?: (want == false)
}
