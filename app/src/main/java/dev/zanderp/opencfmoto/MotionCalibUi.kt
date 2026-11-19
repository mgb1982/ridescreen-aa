// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The "Calibrate lean angle" dialog: instructions, runs [RideMotion.calibrate] off the main thread, shows the result. */
object MotionCalibUi {
    fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    fun show(activity: Activity, onChanged: () -> Unit) {
        val b = AlertDialog.Builder(activity)
            .setTitle(R.string.motion_calib_title)
            .setMessage(R.string.motion_calib_msg)
            .setPositiveButton(R.string.motion_calib_go) { _, _ -> run(activity, onChanged) }
            .setNegativeButton(android.R.string.cancel, null)
        if (RideMotion.isCalibrated(activity)) {
            b.setNeutralButton(R.string.motion_calib_clear) { _, _ ->
                RideMotion.clearCalibration(activity, "cleared by the rider")
                onChanged()
            }
        }
        b.show()
    }

    private fun run(activity: Activity, onChanged: () -> Unit) {
        val wait = AlertDialog.Builder(activity).setMessage(R.string.motion_calib_running).setCancelable(false).show()
        Thread({
            val result = try { RideMotion.calibrate(activity) } catch (_: Throwable) { CalibResult.NO_SENSOR }
            activity.runOnUiThread {
                try { wait.dismiss() } catch (_: Exception) {}
                Toast.makeText(activity, messageFor(result), Toast.LENGTH_LONG).show()
                onChanged()
            }
        }, "motion-calibrate").start()
    }

    fun messageFor(r: CalibResult): Int = when (r) {
        CalibResult.OK -> R.string.motion_calib_ok
        CalibResult.MOVING -> R.string.motion_calib_moving
        CalibResult.NOT_STILL -> R.string.motion_calib_notstill
        CalibResult.NO_SENSOR -> R.string.motion_calib_nosensor
    }
}
