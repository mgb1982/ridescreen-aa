// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** Pure decisions of the "Cuadro" screen ([HudViewActivity]); no Android types so they are unit-tested. */
object CuadroLogic {
    /** Movement allowed between finger down and up for a gesture to still count as a tap. */
    const val TAP_SLOP_DP = 12f

    enum class Mode { ANDROID_AUTO, DASHBOARD }

    /** Which selector button is lit: the dashboard when the rider wants it, else Android Auto. */
    fun selectedMode(dashboardWanted: Boolean): Mode = if (dashboardWanted) Mode.DASHBOARD else Mode.ANDROID_AUTO

    /** Android Auto / Dashboard need a live pipeline; "Map" is always available. */
    fun modeButtonsEnabled(connected: Boolean): Boolean = connected

    /** Where a touch gesture goes, decided at finger down and kept until it ends. */
    enum class Target { ANDROID_AUTO, DASHBOARD, LEGACY, NONE }

    /**
     * [hitKnown]: the compositor says the preview mirrors the dash. [onAndroidAuto]: the touch is on the
     * Android Auto window. Unknown mirror: the plain Android Auto mapping (LEGACY).
     */
    fun target(hitKnown: Boolean, onAndroidAuto: Boolean, insideDash: Boolean = true): Target = when {
        !hitKnown -> Target.LEGACY
        onAndroidAuto -> Target.ANDROID_AUTO
        !insideDash -> Target.NONE      // black bars around the picture, or a welcome card: nothing happens
        else -> Target.DASHBOARD
    }

    /** True when the finger stayed within [slopPx] of where it went down. */
    fun isTap(downX: Float, downY: Float, upX: Float, upY: Float, slopPx: Float): Boolean {
        val dx = upX - downX
        val dy = upY - downY
        return dx * dx + dy * dy <= slopPx * slopPx
    }
}
