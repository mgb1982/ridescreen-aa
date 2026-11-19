// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure decisions of [MediaButtonBridge] about the volume rocker and held keys, so they can be unit tested
 * (the bridge itself needs Android).
 */
internal object ButtonVolumeLogic {
    /** What a change of the pinned music volume is. */
    enum class Change {
        /** Same as the pin: our own re-pin, nothing happened. */
        NONE,
        /** The volume dropped to 0 while the pin is above 0: the dash muted the phone, nobody pressed ▼. */
        MUTED,
        UP,
        DOWN,
    }

    /** [pinned] is the volume we hold (>= 1 while pinned); [now] the one we just read. */
    fun classify(pinned: Int, now: Int): Change = when {
        now == pinned -> Change.NONE
        now == 0 && pinned > 0 -> Change.MUTED
        now > pinned -> Change.UP
        else -> Change.DOWN
    }

    /**
     * A volume change while the rocker is marked ABSENT (so nothing is pinned): is it a real press that
     * proves the rocker exists after all? Only while the setting still sends ▲/▼ to Android Auto, the bike
     * is streaming, the change is not our own, and not more than once per bridge session — the phone's own
     * volume keys look the same, so without the limit they could keep switching the pin on and off.
     */
    fun shouldRepinAfterAbsent(
        rocker: ButtonPresence, volumeToAa: Boolean, streaming: Boolean,
        previous: Int, now: Int, alreadyRepinned: Boolean,
    ): Boolean =
        rocker == ButtonPresence.ABSENT && volumeToAa && streaming && !alreadyRepinned &&
            previous >= 0 && now != previous

    /** A key down with no key up and no repeat for this long is resolved by the bridge itself. */
    const val NO_RELEASE_MS = 5_000L

    /** Long press once held [heldMs] with hold detection on and at least [longPressMs]; otherwise a tap. */
    fun isLongPress(heldMs: Long, holdsEnabled: Boolean, longPressMs: Long): Boolean =
        holdsEnabled && heldMs >= longPressMs
}
