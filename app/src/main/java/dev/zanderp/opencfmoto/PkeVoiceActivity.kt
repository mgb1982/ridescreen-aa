// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible trampoline behind the four keyless-box shortcuts of the app icon (lock, unlock, seat, fuel
 * cap), which Gemini / the Google Assistant can open by name. The spoken order is the confirmation, so
 * there is no confirm dialog. Lock runs even on a locked phone; the other three first ask for the
 * phone's fingerprint / PIN when it is locked. The work is done by [PkeActionService], so the moving
 * check ([LockEndsTrip.refusesLock] inside [PkeUnlock.send]) applies exactly as for the widget.
 */
class PkeVoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val action = PkeVoicePolicy.parse(intent?.getStringExtra(PkeActionService.EXTRA_ACTION))
        if (action == null) { finish(); return }
        if (PkeBattery.code(this).length < 8 || !PkeUnlock.isValidMcuId(PkeUnlock.mcuId(this))) {
            val failure = if (PkeBattery.code(this).length < 8) PkeUnlock.Failure.NO_CODE else PkeUnlock.Failure.NO_MCU_ID
            Toast.makeText(this, PkeUnlockUi.message(this, PkeUnlock.Result(false, failure, action)), Toast.LENGTH_LONG).show()
            finish(); return
        }
        val km = getSystemService(KeyguardManager::class.java)
        // Fail closed: without a KeyguardManager we can't know, so treat the phone as locked.
        val locked = km == null || km.isKeyguardLocked || km.isDeviceLocked
        if (!PkeVoicePolicy.needsAuth(action, locked)) { run(action, locked); return }
        if (km == null) { LogBus.log(PkeVoicePolicy.cancelledLine(action)); finish(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { run(action, true) }
            override fun onDismissCancelled() { LogBus.log(PkeVoicePolicy.cancelledLine(action)); finish() }
            override fun onDismissError() { LogBus.log(PkeVoicePolicy.cancelledLine(action)); finish() }
        })
    }

    private fun run(action: PkeUnlock.Action, locked: Boolean) {
        LogBus.log(PkeVoicePolicy.requestedLine(action, locked))
        try {
            startForegroundService(
                Intent(this, PkeActionService::class.java).putExtra(PkeActionService.EXTRA_ACTION, action.name),
            )
        } catch (e: Exception) {
            LogBus.log("[PKE-voice] can't start the service: $e")
        }
        finish()
    }
}
