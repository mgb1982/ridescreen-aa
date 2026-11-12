// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock

/** Counts started activities so services can tell whether the app is in the foreground (see [PocketDim]). */
object AppVisibility {
    @Volatile private var started = 0
    @Volatile private var backgroundSince = SystemClock.elapsedRealtime()
    @Volatile private var registered = false

    fun init(app: Application) {
        if (registered) return
        registered = true
        backgroundSince = SystemClock.elapsedRealtime()
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                started++
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) backgroundSince = SystemClock.elapsedRealtime()
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    val inForeground: Boolean get() = started > 0

    /** -1 while in the foreground, else ms since the last activity stopped (or since [init]). */
    fun backgroundForMs(now: Long = SystemClock.elapsedRealtime()): Long =
        if (started > 0) -1L else (now - backgroundSince).coerceAtLeast(0L)
}
