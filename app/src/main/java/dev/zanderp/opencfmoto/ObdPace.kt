// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** A5 · pure pacing rule (unit tested): full rate with the dashboard, else at most one round per [SLOW_ROUND_MS]. */
internal object ObdPace {
    const val SLOW_ROUND_MS = 700L
    fun gapMs(dashboardVisible: Boolean, roundTookMs: Long): Long =
        if (dashboardVisible) 0L else (SLOW_ROUND_MS - roundTookMs).coerceAtLeast(0L)

    /**
     * Which PIDs go into round [round] of the one-PID modes. Each PID costs ~35 ms on the adapter, so what moves
     * slowly is asked less often and RPM / speed get most of the time: RPM and speed every round (2 PIDs ≈ 70 ms,
     * about twice the old rate), load and relative throttle every 3rd round, coolant, module voltage, intake air,
     * barometric pressure and absolute load every 10th. A skipped PID keeps its last value in the snapshot.
     */
    fun shouldRead(pid: Int, round: Int): Boolean = when (pid) {
        0x0C, 0x0D -> true
        0x04, 0x45 -> round % 3 == 0
        else -> round % 10 == 0
    }
}
