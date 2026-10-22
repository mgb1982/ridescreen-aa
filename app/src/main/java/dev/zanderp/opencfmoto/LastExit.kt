// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * v2.3 · Why did the previous app process end? (Android 11+ keeps the reason.) A native crash inside
 * a codec, an ANR or the system killing us never reaches [CrashGuard]; this tells them apart in the
 * log (and in the anonymous error report, when telemetry is on) at the next start.
 * 22 Oct 2026: the app died silently while restarting a stalled video decoder.
 */
object LastExit {
    private const val PREFS = "last_exit"

    fun check(ctx: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        try {
            val am = ctx.getSystemService(ActivityManager::class.java) ?: return
            val info = am.getHistoricalProcessExitReasons(ctx.packageName, 0, 1).firstOrNull() ?: return
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getLong("ts", 0L) == info.timestamp) return   // already reported
            p.edit().putLong("ts", info.timestamp).apply()
            val reason = reasonName(info.reason)
            val ago = (System.currentTimeMillis() - info.timestamp) / 1000
            val line = "[EXIT] previous process ended: $reason (${ago}s ago, status=${info.status}, " +
                "importance=${info.importance}, pss=${info.pss / 1024} MB) ${info.description.orEmpty().take(200)}"
            LogBus.log(line)
            val bad = info.reason in setOf(
                ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_SIGNALED,
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            )
            if (bad) AnonymousTelemetry.reportError(ctx, "Previous process ended: $reason · ${info.description.orEmpty().take(300)}")
        } catch (e: Exception) {
            LogBus.log("[EXIT] could not read the last exit reason: ${e.message}")
        }
    }

    @androidx.annotation.RequiresApi(30)
    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited by itself"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW MEMORY (system killed it)"
        ApplicationExitInfo.REASON_CRASH -> "Java crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE CRASH"
        ApplicationExitInfo.REASON_ANR -> "ANR (not responding)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission changed"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "closed by the user (force stop / recents)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
        ApplicationExitInfo.REASON_OTHER -> "other (system)"
        14 -> "frozen"
        15 -> "package state change"
        16 -> "app updated"
        else -> "unknown ($r)"
    }
}
