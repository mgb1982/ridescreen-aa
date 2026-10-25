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
import java.util.concurrent.Executors

/**
 * v2.3 · "🔒 Lock / unlock (keyless box)" in Help. Sends a real command to [PkeUnlock]. Needs the
 * PKE code ([PkeBattery] holds it, shared with the battery reading) and a McuID the rider must
 * supply themselves — there is no source for it here, see [PkeUnlock]'s doc. Every send asks for
 * explicit confirmation naming the exact action first; nothing here is automatic.
 */
object PkeUnlockUi {
    private const val REQ_BT_PERM = 7347
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val code = PkeBattery.code(activity)
        if (code.length < 8) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.pke_unlock_title)
                .setMessage(R.string.pke_unlock_need_code)
                .setPositiveButton(R.string.whats_new_ok, null)
                .show()
            return
        }
        if (PkeUnlock.mcuId(activity).length < 4) { askMcuId(activity) { pickAction(activity) }; return }
        pickAction(activity)
    }

    private fun askMcuId(activity: Activity, then: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(PkeUnlock.mcuId(activity))
            hint = "A1B2C3D4"
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pke_unlock_mcu_title)
            .setMessage(R.string.pke_unlock_mcu_body)
            .setView(input)
            .setPositiveButton(R.string.dest_save) { _, _ ->
                val id = input.text?.toString()?.trim().orEmpty()
                if (id.length < 4) { Toast.makeText(activity, R.string.pke_unlock_mcu_invalid, Toast.LENGTH_LONG).show(); return@setPositiveButton }
                PkeUnlock.setMcuId(activity, id)
                then()
            }
            .setNegativeButton(R.string.dash_cancel, null)
            .show()
    }

    private fun pickAction(activity: Activity) {
        val actions = listOf(
            PkeUnlock.Action.UNLOCK to activity.getString(R.string.pke_unlock_action_unlock),
            PkeUnlock.Action.LOCK to activity.getString(R.string.pke_unlock_action_lock),
            PkeUnlock.Action.OILBOX to activity.getString(R.string.pke_unlock_action_oilbox),
            PkeUnlock.Action.CUSHION to activity.getString(R.string.pke_unlock_action_cushion),
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pke_unlock_title)
            .setItems(actions.map { it.second }.toTypedArray()) { _, i -> confirm(activity, actions[i].first, actions[i].second) }
            .setNeutralButton(R.string.pke_unlock_change_mcu) { _, _ -> askMcuId(activity) { pickAction(activity) } }
            .show()
    }

    private fun confirm(activity: Activity, action: PkeUnlock.Action, label: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.pke_unlock_confirm_title, label))
            .setMessage(R.string.pke_unlock_confirm_body)
            .setPositiveButton(activity.getString(R.string.pke_unlock_confirm_yes, label)) { _, _ -> send(activity, action) }
            .setNegativeButton(R.string.pke_unlock_change_mcu) { _, _ -> askMcuId(activity) { pickAction(activity) } }
            .setNeutralButton(R.string.dash_cancel, null)
            .show()
    }

    private fun send(activity: Activity, action: PkeUnlock.Action) {
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
            .setTitle(R.string.pke_unlock_title)
            .setMessage(R.string.pke_unlock_sending)
            .setCancelable(false)
            .show()
        io.execute {
            val res = PkeUnlock.send(activity, action)
            main.post {
                runCatching { dialog.dismiss() }
                if (activity.isFinishing || activity.isDestroyed) return@post
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.pke_unlock_title)
                    .setMessage(message(activity, res))
                    .setPositiveButton(R.string.whats_new_ok, null)
                    .apply {
                        if (res.failure == PkeUnlock.Failure.NO_MCU_ID || res.failure == PkeUnlock.Failure.BAD_MCU_ID) {
                            setNeutralButton(R.string.pke_unlock_change_mcu) { _, _ -> askMcuId(activity) { pickAction(activity) } }
                        }
                    }
                    .show()
            }
        }
    }

    private fun message(activity: Activity, res: PkeUnlock.Result): String {
        if (res.ok) return activity.getString(R.string.pke_unlock_sent)
        return activity.getString(
            when (res.failure) {
                PkeUnlock.Failure.NO_CODE -> R.string.pke_fail_no_code
                PkeUnlock.Failure.NO_MCU_ID -> R.string.pke_unlock_need_mcu
                PkeUnlock.Failure.NO_PERMISSION -> R.string.pke_need_nearby
                PkeUnlock.Failure.BLUETOOTH_OFF -> R.string.pke_bt_off
                PkeUnlock.Failure.NOT_FOUND -> R.string.pke_fail_not_found
                PkeUnlock.Failure.NO_LINK -> R.string.pke_fail_not_found
                PkeUnlock.Failure.NO_CHALLENGE -> R.string.pke_unlock_fail_no_challenge
                PkeUnlock.Failure.BAD_CHALLENGE -> R.string.pke_unlock_fail_bad_challenge
                PkeUnlock.Failure.BAD_MCU_ID -> R.string.pke_unlock_fail_bad_mcu
                PkeUnlock.Failure.DERIVE_FAILED -> R.string.pke_unlock_fail_derive
                PkeUnlock.Failure.NO_ANSWER -> R.string.pke_fail_no_answer
                null -> R.string.pke_fail_no_answer
            },
        )
    }
}
