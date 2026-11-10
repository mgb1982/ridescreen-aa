// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import androidx.annotation.StringRes

/**
 * What the home screen says while connecting. Reads [Phase] and the existing detail text only —
 * it does not change when the link advances.
 */
object ConnectionGuide {
    enum class Mark { TODO, CURRENT, DONE, FAILED }

    data class Model(
        val steps: List<Mark>,
        @StringRes val hintRes: Int,
        val showSamsungNote: Boolean,
    )

    fun model(phase: Phase, detail: String): Model {
        val samsung = phase == Phase.ERROR || phase == Phase.JOINING_WIFI || phase == Phase.WAITING_FOR_BIKE
        if (phase == Phase.ERROR) {
            val kind = errorKind(detail)
            return Model(stepsFor(kind), hintFor(kind), samsung)
        }
        val steps = when (phase) {
            Phase.AA_VIDEO_LIVE, Phase.JOINING_WIFI, Phase.RECONNECTING, Phase.WAITING_FOR_BIKE ->
                marks(Mark.CURRENT, Mark.TODO, Mark.TODO, Mark.TODO)
            Phase.PXC_CONNECTING ->
                marks(Mark.DONE, Mark.DONE, Mark.CURRENT, Mark.TODO)
            Phase.STREAMING, Phase.MIRRORING ->
                marks(Mark.DONE, Mark.DONE, Mark.DONE, Mark.DONE)
            else -> marks(Mark.TODO, Mark.TODO, Mark.TODO, Mark.TODO)
        }
        val hint = when (phase) {
            Phase.STARTING_AA -> R.string.hint_starting_aa
            Phase.AA_VIDEO_LIVE, Phase.JOINING_WIFI -> R.string.hint_wifi
            Phase.PXC_CONNECTING -> R.string.hint_link
            Phase.STREAMING -> R.string.hint_stream
            Phase.MIRRORING -> R.string.hint_mirror
            Phase.RECONNECTING -> R.string.hint_reconnect
            Phase.WAITING_FOR_BIKE -> R.string.hint_waiting
            Phase.STOPPED -> R.string.hint_stopped
            else -> R.string.hint_idle
        }
        return Model(steps, hint, samsung && phase != Phase.ERROR)
    }

    private enum class Kind { WIFI, DASH, LINK, AA, VPN, HOTSPOT, PERMISSION, GENERIC }

    private fun errorKind(detail: String): Kind {
        val d = detail.lowercase().replace(Regex("[\\u2010-\\u2015]"), "-")
        return when {
            d.contains("kill-switch") || d.contains("vpn") -> Kind.VPN
            d.contains("hotspot") -> Kind.HOTSPOT
            d.contains("nearby") || d.contains("bluetooth") || d.contains("permission") ||
                d.contains("wi-fi direct") || d.contains("wifi direct") -> Kind.PERMISSION
            d.contains("wi-fi is off") || d.contains("wifi is off") || d.contains("can't find") ||
                d.contains("can’t find") || d.contains("not found") || d.contains("picker") ||
                d.contains("turn it on") -> Kind.WIFI
            d.contains("easyconn") || d.contains("isn't asking") || d.contains("isn’t asking") ||
                d.contains("no peer") || d.contains("mirroring interface") || d.contains("motoplay") ||
                d.contains("phone-link") -> Kind.DASH
            d.contains("head unit") || d.contains("android auto") || d.contains("isn't sending video") ||
                d.contains("isn’t sending video") || d.contains("didn't attach") ||
                d.contains("didn’t attach") || d.contains("wedged") -> Kind.AA
            d.contains("yunmo") || d.contains("link dropped") || d.contains("lost bike") ||
                d.contains("prober") || d.contains("map switch") -> Kind.LINK
            else -> Kind.GENERIC
        }
    }

    private fun hintFor(kind: Kind): Int = when (kind) {
        Kind.WIFI -> R.string.hint_error_wifi
        Kind.DASH -> R.string.hint_error_dash
        Kind.LINK, Kind.GENERIC -> R.string.hint_error_ignition
        Kind.AA -> R.string.hint_error_aa
        Kind.VPN -> R.string.hint_error_vpn
        Kind.HOTSPOT -> R.string.hint_error_hotspot
        Kind.PERMISSION -> R.string.hint_error_permission
    }

    private fun stepsFor(kind: Kind): List<Mark> = when (kind) {
        // Android Auto failed on the phone, before the bike steps.
        Kind.AA -> marks(Mark.TODO, Mark.TODO, Mark.TODO, Mark.TODO)
        Kind.WIFI, Kind.HOTSPOT, Kind.PERMISSION ->
            marks(Mark.FAILED, Mark.TODO, Mark.TODO, Mark.TODO)
        Kind.DASH -> marks(Mark.DONE, Mark.FAILED, Mark.TODO, Mark.TODO)
        else -> marks(Mark.DONE, Mark.DONE, Mark.FAILED, Mark.TODO)
    }

    private fun marks(a: Mark, b: Mark, c: Mark, d: Mark) = listOf(a, b, c, d)
}
