// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * A1 · In map mode the real phone screen must stay ON (a SCREEN_BRIGHT wake lock keeps the dash
 * VirtualDisplay painting), so in a pocket it burns at full brightness. When the app has been in the
 * background for a while we cover it with a black overlay at minimum brightness ([PocketCover]).
 * These are the pure decisions; the watchdog tick calls them.
 */
internal object PocketDim {
    const val BACKGROUND_DELAY_MS = 8_000L
    const val TOUCH_PAUSE_MS = 20_000L

    /**
     * @param backgroundForMs ms since the app went to the background; negative = app in the foreground.
     * @param sinceDismissMs ms since the rider tapped the cover away (huge if never).
     */
    fun shouldCover(
        enabled: Boolean,
        gpxActive: Boolean,
        canDrawOverlays: Boolean,
        backgroundForMs: Long,
        sinceDismissMs: Long,
        covering: Boolean,
    ): Boolean = enabled && gpxActive && canDrawOverlays && !covering &&
        backgroundForMs >= BACKGROUND_DELAY_MS && sinceDismissMs >= TOUCH_PAUSE_MS

    fun shouldUncover(
        enabled: Boolean,
        gpxActive: Boolean,
        canDrawOverlays: Boolean,
        appInForeground: Boolean,
        covering: Boolean,
    ): Boolean = covering && (!enabled || !gpxActive || !canDrawOverlays || appInForeground)
}
