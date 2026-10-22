// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * v2.3 · "🔋 Battery (keyless box)" in Help. Reads the bike's battery voltage over Bluetooth with
 * [PkeBattery] (read only). The rider enters the PKE code once (shown in Zontes Smart → vehicle
 * management). Experimental and off the main path: nothing here ever controls the bike.
 */
object PkeBatteryUi {
    private const val REQ_BT_PERM = 7346
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val code = PkeBattery.code(activity)
        if (code.length < 8) { askCode(activity) { read(activity) }; return }
        read(activity)
    }

    private fun askCode(activity: Activity, then: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(PkeBattery.code(activity))
            hint = "0501234567890123"
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pke_code_title)
            .setMessage(R.string.pke_code_body)
            .setView(input)
            .setPositiveButton(R.string.dest_save) { _, _ ->
                val c = PkeBattery.cleanCode(input.text?.toString())
                if (c.length < 8) { Toast.makeText(activity, R.string.pke_code_invalid, Toast.LENGTH_LONG).show(); return@setPositiveButton }
                PkeBattery.setCode(activity, c)
                then()
            }
            .setNegativeButton(R.string.dash_cancel, null)
            .show()
    }

    private fun read(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 31) {
            val need = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                .filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
            if (need.isNotEmpty()) {
                Toast.makeText(activity, R.string.pke_need_nearby, Toast.LENGTH_LONG).show()
                ActivityCompat.requestPermissions(activity, need.toTypedArray(), REQ_BT_PERM)
                return
            }
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pke_title)
            .setMessage(R.string.pke_reading)
            .setCancelable(false)
            .show()
        io.execute {
            val res = PkeBattery.read(activity)
            main.post {
                runCatching { dialog.dismiss() }
                if (activity.isFinishing || activity.isDestroyed) return@post
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pke_title)
                    .setMessage(message(activity, res))
                    .setPositiveButton(R.string.whats_new_ok, null)
                    .apply {
                        if (res.failure == PkeBattery.Failure.NO_CODE || res.failure == PkeBattery.Failure.NOT_FOUND) {
                            setNeutralButton(R.string.pke_change_code) { _, _ -> askCode(activity) { read(activity) } }
                        }
                    }
                    .show()
            }
        }
    }

    private fun message(activity: Activity, res: PkeBattery.Result): String {
        res.reading?.let { r ->
            val when_ = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(r.atMs))
            val v = r.volts?.let { activity.getString(R.string.pke_volts, "%.1f".format(it)) } ?: activity.getString(R.string.pke_no_volts)
            val lock = when (r.locked) {
                true -> "\n" + activity.getString(R.string.pke_locked)
                false -> "\n" + activity.getString(R.string.pke_unlocked)
                null -> ""
            }
            val low = if (r.volts != null && r.volts < 12f) "\n" + activity.getString(R.string.pke_low) else ""
            return activity.getString(R.string.pke_result, v, when_) + lock + low
        }
        return activity.getString(
            when (res.failure) {
                PkeBattery.Failure.NO_CODE -> R.string.pke_fail_no_code
                PkeBattery.Failure.NO_PERMISSION -> R.string.pke_need_nearby
                PkeBattery.Failure.BLUETOOTH_OFF -> R.string.pke_bt_off
                PkeBattery.Failure.NOT_FOUND -> R.string.pke_fail_not_found
                PkeBattery.Failure.NO_LINK -> R.string.pke_fail_not_found
                PkeBattery.Failure.NO_ANSWER -> R.string.pke_fail_no_answer
                null -> R.string.pke_fail_no_answer
            },
        )
    }
}
