// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * "Allow all the time" location. With it, Android lets the ride service claim the location type
 * even when it starts from the background (Bluetooth auto-start, phone in the pocket), so trips
 * keep full GPS. Asked once, after fine location is granted, with an explanation first.
 */
object BackgroundLocationAsk {
    const val REQ = 4711
    private const val PREFS = "bg_location"
    private const val KEY_ASKED = "asked_v1"

    fun granted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun maybeAsk(a: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(a)) return
        if (a.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val p = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY_ASKED, false)) return
        p.edit().putBoolean(KEY_ASKED, true).apply()
        android.app.AlertDialog.Builder(a)
            .setTitle(R.string.bgloc_title)
            .setMessage(R.string.bgloc_body)
            .setPositiveButton(R.string.bgloc_allow) { _, _ ->
                LogBus.log("[trip] asking for 'allow all the time' location")
                a.requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ)
            }
            .setNegativeButton(R.string.bgloc_later) { _, _ -> LogBus.log("[trip] rider declined background location") }
            .show()
    }
}
