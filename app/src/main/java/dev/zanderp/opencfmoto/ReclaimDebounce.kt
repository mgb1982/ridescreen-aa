// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** Pure debounce rule for AndroidAutoService.regainForegroundTypes. */
object ReclaimDebounce {
    const val WINDOW_MS = 2_000L

    /** True when the previous run ([lastAtMs], null = never) is at least [windowMs] before [nowMs]. */
    fun shouldRun(lastAtMs: Long?, nowMs: Long, windowMs: Long = WINDOW_MS): Boolean =
        lastAtMs == null || nowMs - lastAtMs >= windowMs || nowMs < lastAtMs
}
