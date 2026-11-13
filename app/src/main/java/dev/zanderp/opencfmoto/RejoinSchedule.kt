// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pause between bike Wi-Fi re-requests after the link was lost (pure, unit-tested).
 *
 * First [FAST_WINDOW_MS] after the loss: same pace as before (near-instant first retry, then
 * 2.5 s steps up to 15 s; with the 12 s request that is one request every ~27 s), which covers a
 * traffic light or a fuel stop. After that the bike is probably off: one request start every
 * [SLOW_PERIOD_MS] (12 s request + the pause returned here) to save battery.
 */
object RejoinSchedule {
    const val FAST_MS = 300L
    const val BASE_MS = 2_500L
    const val MAX_FAST_PAUSE_MS = 15_000L
    const val FAST_WINDOW_MS = 3 * 60_000L
    const val SLOW_PERIOD_MS = 75_000L
    const val REQUEST_MS = 12_000L

    /** @param attempt 1-based number of the upcoming re-request (already incremented). */
    fun pauseAfterAttempt(elapsedSinceLossMs: Long, attempt: Int): Long {
        val fast = when {
            attempt <= 1 -> FAST_MS
            else -> minOf(BASE_MS * (attempt - 1), MAX_FAST_PAUSE_MS)
        }
        return if (elapsedSinceLossMs < FAST_WINDOW_MS) fast else maxOf(fast, SLOW_PERIOD_MS - REQUEST_MS)
    }
}
