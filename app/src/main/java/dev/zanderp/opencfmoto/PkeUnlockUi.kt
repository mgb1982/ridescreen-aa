// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.Manifest
import android.app.Activity
import android.content.Context
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
        if (!PkeUnlock.isValidMcuId(PkeUnlock.mcuId(activity))) { askMcuId(activity) { pickAction(activity) }; return }
        pickAction(activity)
    }

    private fun askMcuId(activity: Activity, then: () -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(PkeUnlock.mcuId(activity))
            hint = "32 hexadecimal digits"
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pke_unlock_mcu_title)
            .setMessage(R.string.pke_unlock_mcu_body)
            .setView(input)
            .setPositiveButton(R.string.dest_save) { _, _ ->
                val id = input.text?.toString()?.trim().orEmpty()
                if (!PkeUnlock.isValidMcuId(id)) { Toast.makeText(activity, R.string.pke_unlock_mcu_invalid, Toast.LENGTH_LONG).show(); return@setPositiveButton }
                PkeUnlock.setMcuId(activity, id)
                then()
            }
            .setNeutralButton(R.string.pke_unlock_scan) { _, _ ->
                activity.startActivity(android.content.Intent(activity, PkeMcuScanActivity::class.java))
            }
            .setNegativeButton(R.string.dash_cancel, null)
            .show()
    }

    private fun pickAction(activity: Activity) {
        PkeUnlock.warmUp(activity)
        val actions = listOf(
            PkeUnlock.Action.UNLOCK to activity.getString(R.string.pke_unlock_action_unlock),
            PkeUnlock.Action.LOCK to activity.getString(R.string.pke_unlock_action_lock),
            PkeUnlock.Action.OILBOX to activity.getString(R.string.pke_unlock_action_oilbox),
            PkeUnlock.Action.CUSHION to activity.getString(R.string.pke_unlock_action_cushion),
        )
        val onConnect = PkeUnlock.unlockOnConnect(activity)
        val toggle = activity.getString(if (onConnect) R.string.pke_unlock_on_connect_on else R.string.pke_unlock_on_connect_off)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.pke_unlock_title)
            .setItems((actions.map { it.second } + toggle).toTypedArray()) { _, i ->
                if (i < actions.size) confirm(activity, actions[i].first, actions[i].second)
                else {
                    PkeUnlock.setUnlockOnConnect(activity, !onConnect)
                    Toast.makeText(
                        activity,
                        if (onConnect) R.string.pke_unlock_on_connect_toast_off else R.string.pke_unlock_on_connect_toast_on,
                        Toast.LENGTH_LONG,
                    ).show()
                    pickAction(activity)
                }
            }
            .setNeutralButton(R.string.pke_unlock_change_mcu) { _, _ -> askMcuId(activity) { pickAction(activity) } }
            .show()
    }

    private fun confirm(activity: Activity, action: PkeUnlock.Action, label: String) {
        PkeUnlock.warmUp(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.pke_unlock_confirm_title, label))
            .setMessage(R.string.pke_unlock_confirm_body)
            .setPositiveButton(activity.getString(R.string.pke_unlock_confirm_yes, label)) { _, _ -> send(activity, action) }
            .setNegativeButton(R.string.pke_unlock_change_mcu) { _, _ -> askMcuId(activity) { pickAction(activity) } }
            .setNeutralButton(R.string.dash_cancel, null)
            .show()
    }

    /** Lock and unlock go out on one tap; the seat and fuel-cap releases ask first. */
    internal fun needsConfirm(a: PkeUnlock.Action) = a == PkeUnlock.Action.OILBOX || a == PkeUnlock.Action.CUSHION

    internal fun actionLabel(ctx: Context, a: PkeUnlock.Action): String = ctx.getString(
        when (a) {
            PkeUnlock.Action.UNLOCK -> R.string.pke_unlock_action_unlock
            PkeUnlock.Action.LOCK -> R.string.pke_unlock_action_lock
            PkeUnlock.Action.OILBOX -> R.string.pke_unlock_action_oilbox
            PkeUnlock.Action.CUSHION -> R.string.pke_unlock_action_cushion
        },
    )

    internal fun doneText(ctx: Context, a: PkeUnlock.Action): String = ctx.getString(R.string.pke_done, actionLabel(ctx, a))

    /** Buttons on the home card: not configured yet -> the setup flow; seat/fuel cap -> confirmation first. */
    fun quick(activity: Activity, action: PkeUnlock.Action, then: () -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (PkeBattery.code(activity).length < 8 || !PkeUnlock.isValidMcuId(PkeUnlock.mcuId(activity))) { show(activity); return }
        if (!needsConfirm(action)) { runQuick(activity, action, then); return }
        PkeUnlock.warmUp(activity)
        val label = actionLabel(activity, action)
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.pke_unlock_confirm_title, label))
            .setMessage(R.string.pke_unlock_confirm_body)
            .setPositiveButton(activity.getString(R.string.pke_unlock_confirm_yes, label)) { _, _ -> runQuick(activity, action, then) }
            .setNegativeButton(R.string.dash_cancel) { _, _ -> PkeUnlock.release() }
            .setOnCancelListener { PkeUnlock.release() }
            .show()
    }

    /**
     * One-step Connect: when the rider turned on "Unlock when connecting", a Connect tap also unlocks the
     * bike, in parallel with the connection (it never delays or blocks it). Called only from an explicit
     * tap (the Connect button or the widget), never from auto-connect or the Bluetooth trigger: coming
     * into range must not open the bike.
     */
    fun unlockWithConnect(app: Context) {
        if (!PkeUnlock.unlockOnConnect(app)) return
        if (PkeBattery.code(app).length < 8 || !PkeUnlock.isValidMcuId(PkeUnlock.mcuId(app))) return
        LogBus.log("[PKE-unlock] Connect tapped — unlocking the bike in parallel")
        io.execute {
            val res = PkeUnlock.send(app, PkeUnlock.Action.UNLOCK)
            main.post {
                Toast.makeText(app, resultText(app, PkeUnlock.Action.UNLOCK, res), toastLength(res)).show()
            }
        }
    }

    private fun runQuick(activity: Activity, action: PkeUnlock.Action, then: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 31) {
            val need = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                .filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
            if (need.isNotEmpty()) {
                Toast.makeText(activity, R.string.pke_need_nearby, Toast.LENGTH_LONG).show()
                ActivityCompat.requestPermissions(activity, need.toTypedArray(), REQ_BT_PERM)
                return
            }
        }
        val app = activity.applicationContext
        Toast.makeText(app, app.getString(R.string.pke_unlock_sending_short, actionLabel(app, action)), Toast.LENGTH_SHORT).show()
        io.execute {
            val res = PkeUnlock.send(app, action)
            main.post {
                Toast.makeText(app, resultText(app, action, res), toastLength(res)).show()
                if (!activity.isFinishing && !activity.isDestroyed) then()
            }
        }
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

    /** Toast text: "<action> ✓", the read-back warning, or why it failed. */
    internal fun resultText(ctx: Context, a: PkeUnlock.Action, res: PkeUnlock.Result): String =
        if (res.ok && !res.mismatch) doneText(ctx, a) else message(ctx, res)

    internal fun toastLength(res: PkeUnlock.Result) = if (res.ok && !res.mismatch) Toast.LENGTH_SHORT else Toast.LENGTH_LONG

    internal fun message(ctx: Context, res: PkeUnlock.Result): String {
        if (res.mismatch) return ctx.getString(if (res.lockedAfter == true) R.string.pke_state_left_locked else R.string.pke_state_left_unlocked)
        if (res.ok) return ctx.getString(R.string.pke_unlock_sent)
        return ctx.getString(
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
                PkeUnlock.Failure.MOVING -> R.string.pke_fail_moving
                null -> R.string.pke_fail_no_answer
            },
        )
    }
}
