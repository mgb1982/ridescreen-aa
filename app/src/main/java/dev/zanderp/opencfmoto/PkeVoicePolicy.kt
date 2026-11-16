// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure rules for the voice / app-icon shortcuts of the keyless box ([PkeVoiceActivity]).
 * Locking is always allowed from a locked phone (the moving check stays in [PkeUnlock.send]);
 * every other action needs the phone unlocked first.
 */
object PkeVoicePolicy {
    /** The action named by the shortcut extra, or null for anything that is not one of the four. */
    fun parse(name: String?): PkeUnlock.Action? = PkeUnlock.Action.values().firstOrNull { it.name == name }

    /** True when the rider must pass the phone's lock screen before the action runs. */
    fun needsAuth(action: PkeUnlock.Action, phoneLocked: Boolean): Boolean =
        phoneLocked && action != PkeUnlock.Action.LOCK

    /** Log line when the request is accepted. */
    fun requestedLine(action: PkeUnlock.Action, phoneLocked: Boolean) =
        "[PKE-voice] ${action.label} requested (phone locked=$phoneLocked)"

    /** Log line when the lock-screen prompt was cancelled or failed. */
    fun cancelledLine(action: PkeUnlock.Action) =
        "[PKE-voice] ${action.label} cancelled (phone locked, not authenticated)"
}
