// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import java.util.Locale

/**
 * Pure helpers (no Android types) to place events on the dash's own timeline. CLIENT_INFO
 * `currentHUTime` is the dash's uptime in ms (not a time of day); it advances like the phone clock.
 */
internal object DashBoot {
    /** Estimated phone-clock time (epoch ms) when the dash booted. */
    internal fun bootEpochMs(receivedAtEpochMs: Long, uptimeMs: Long): Long = receivedAtEpochMs - uptimeMs

    /** A new dash boot when the estimates differ by more than [toleranceMs]; no previous one counts as new. */
    internal fun isNewBoot(prevBootEpochMs: Long?, newBootEpochMs: Long, toleranceMs: Long = 5_000L): Boolean =
        prevBootEpochMs == null || kotlin.math.abs(newBootEpochMs - prevBootEpochMs) > toleranceMs

    /** Estimated dash uptime now, from the uptime seen at a CLIENT_INFO received at [infoAtEpochMs]. */
    internal fun uptimeNowMs(uptimeAtInfoMs: Long, infoAtEpochMs: Long, nowEpochMs: Long): Long =
        uptimeAtInfoMs + (nowEpochMs - infoAtEpochMs)

    /** e.g. `85.2 s`. */
    internal fun fmtSeconds(ms: Long): String = String.format(Locale.ROOT, "%.1f s", ms / 1000.0)
}
