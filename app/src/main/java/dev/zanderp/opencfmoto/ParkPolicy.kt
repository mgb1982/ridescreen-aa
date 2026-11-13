// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * How long the bike's Wi-Fi may be missing before Android Auto is parked (pure, unit tested; the
 * service itself needs Android classes, so its rules live here).
 */
internal object ParkPolicy {
    /** Dropped after a first stream: SoftAP blinks, 60 s was parking mid-ride. */
    const val AFTER_STREAM_MS = 180_000L

    /**
     * Connect tapped but the bike never linked (bike off). Before, the supervisor only parked after a
     * first stream, so Android Auto, the Wi-Fi locks and the 90 s join retries ran until Stop (12 Nov
     * log: 13 min). Longer than the slowest first links seen (Wi-Fi Direct dashes, ~2.5 min).
     */
    const val NEVER_LINKED_MS = 300_000L

    fun graceMs(sawStream: Boolean): Long = if (sawStream) AFTER_STREAM_MS else NEVER_LINKED_MS
}
