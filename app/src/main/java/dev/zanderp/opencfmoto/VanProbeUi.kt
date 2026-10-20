// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * "🔬 Dash data probe" in Help (test builds). Starts a [VanProbe] and shows what the dash offers and
 * pushes: tyre pressure/temperature, mileage, etc. Read only; the raw numbers are shown as the dash
 * sends them (units are not interpreted yet) and every frame also goes to the log.
 */
object VanProbeUi {
    private const val REQ_PERM = 7347
    private val main = Handler(Looper.getMainLooper())

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val need = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) {
            Toast.makeText(activity, R.string.van_need_perm, Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(activity, need.toTypedArray(), REQ_PERM)
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.van_title)
            .setMessage(R.string.van_intro)
            .setPositiveButton(R.string.van_mode_official_km) { _, _ -> run(activity, VanProbe.Mode.OFFICIAL_PLUS_MILEAGE) }
            .setNegativeButton(R.string.van_mode_official) { _, _ -> run(activity, VanProbe.Mode.OFFICIAL) }
            .setNeutralButton(R.string.van_mode_listen) { _, _ -> run(activity, VanProbe.Mode.LISTEN_ONLY) }
            .show()
    }

    private fun run(activity: Activity, mode: VanProbe.Mode) {
        var dialog: AlertDialog? = null
        lateinit var probe: VanProbe
        val tick = object : Runnable {
            override fun run() {
                val d = dialog ?: return
                if (!d.isShowing) return
                d.setMessage(render(activity, probe))
                main.postDelayed(this, 1000)
            }
        }
        probe = VanProbe(activity, mode, { LogBus.log(it) }) { }
        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.van_title)
            .setMessage(R.string.van_scanning)
            .setCancelable(false)
            .setPositiveButton(R.string.van_stop) { _, _ -> probe.stop() }
            .create()
        dialog = dlg
        dlg.show()
        probe.start()
        main.postDelayed(tick, 500)
    }

    private fun render(activity: Activity, p: VanProbe): String {
        val sb = StringBuilder()
        sb.append(p.status)
        val age = if (p.lastFrameAt > 0) (System.currentTimeMillis() - p.lastFrameAt) / 1000 else -1
        if (age >= 0) sb.append(" · ").append(activity.getString(R.string.van_last_frame, age.toString()))
        if (p.offered.isNotEmpty()) {
            sb.append("\n\n").append(activity.getString(R.string.van_offered))
            sb.append("\n").append(p.offered.joinToString(", ") { "$it=${VanProtocol.name(it)}" })
        }
        val vals = p.values.values.sortedBy { it.id }
        if (vals.isNotEmpty()) {
            sb.append("\n\n").append(activity.getString(R.string.van_values))
            for (f in vals) {
                sb.append("\n").append(VanProtocol.name(f.id)).append(" (").append(f.id).append(") = ").append(f.raw)
                if (f.bytes == 4) sb.append("  [0x").append(java.lang.Long.toHexString(f.raw and 0xFFFFFFFFL)).append("]")
                val text = VanProtocol.describe(f)
                if (text.isNotEmpty()) sb.append("\n   → ").append(text)
            }
        }
        sb.append("\n\n").append(activity.getString(R.string.van_log_hint))
        return sb.toString()
    }
}
