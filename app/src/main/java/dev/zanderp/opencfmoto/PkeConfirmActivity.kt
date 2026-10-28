// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Confirmation for the widget's seat and fuel-cap buttons (a widget can't show a dialog). It floats
 * over the home screen as a transparent activity and opens the link to the box while the rider reads
 * the question, so confirming sends at once. Confirming starts [PkeActionService]; anything else
 * closes it and drops the pre-opened link.
 */
class PkeConfirmActivity : AppCompatActivity() {
    private var confirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = PkeUnlock.Action.values().firstOrNull { it.name == intent?.getStringExtra(PkeActionService.EXTRA_ACTION) }
        if (action == null || !PkeUnlockUi.needsConfirm(action)) { finish(); return }
        if (PkeBattery.code(this).length < 8 || !PkeUnlock.isValidMcuId(PkeUnlock.mcuId(this))) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish(); return
        }
        PkeUnlock.warmUp(this)
        val label = PkeUnlockUi.actionLabel(this, action)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.pke_unlock_confirm_title, label))
            .setMessage(R.string.pke_unlock_confirm_body)
            .setPositiveButton(getString(R.string.pke_unlock_confirm_yes, label)) { _, _ ->
                confirmed = true
                ContextCompat.startForegroundService(
                    this, Intent(this, PkeActionService::class.java).putExtra(PkeActionService.EXTRA_ACTION, action.name),
                )
            }
            .setNegativeButton(R.string.dash_cancel, null)
            .setOnDismissListener {
                if (!confirmed) PkeUnlock.release()
                finish()
            }
            .show()
    }
}
