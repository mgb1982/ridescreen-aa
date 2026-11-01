// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * v2.3 · Drive Android Auto from other apps with plain broadcast intents — Key Mapper (its
 * Shizuku-based Expert mode remaps a handlebar remote even with the screen off), Tasker, MacroDroid…
 *
 * This is how a "remote only for Android Auto" works without RideScreen declaring an accessibility
 * service: Play Protect hard-blocks sideloaded APKs that do (19 Oct). The other app owns the
 * buttons; we only obey `app.ridescreen.action.*` while Android Auto is on the dash.
 * Off by default (Controls → Remote with Key Mapper or Tasker).
 */
object ExternalControl {
    const val PREFIX = "app.ridescreen.action."

    /** Short names other apps send, e.g. `app.ridescreen.action.UP`. */
    val ACTIONS: Map<String, ButtonAction> = linkedMapOf(
        "UP" to ButtonAction.DPAD_UP,
        "DOWN" to ButtonAction.DPAD_DOWN,
        "LEFT" to ButtonAction.DPAD_LEFT,
        "RIGHT" to ButtonAction.DPAD_RIGHT,
        "OK" to ButtonAction.SELECT,
        "BACK" to ButtonAction.BACK,
        "HOME" to ButtonAction.HOME,
        "VOICE" to ButtonAction.ASSISTANT,
        "NEXT" to ButtonAction.KNOB_FORWARD,
        "PREV" to ButtonAction.KNOB_BACK,
        "NAV1" to ButtonAction.NAV_1,
        "NAV2" to ButtonAction.NAV_2,
        "NAV3" to ButtonAction.NAV_3,
    )

    /** v2.4: custom dashboard switches (`…action.DASH` toggles, `DASH_ON` / `DASH_OFF` set it). */
    internal val DASH_ACTIONS = listOf("DASH", "DASH_ON", "DASH_OFF")

    @Volatile private var receiver: BroadcastReceiver? = null

    /** Called when the projection service starts; no-op when the rider hasn't turned it on. */
    @Synchronized
    fun start(ctx: Context) {
        if (receiver != null || !AppSettings.externalControl(ctx)) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (handleDashboard(c, intent.action)) return
                handle(intent.action)
            }
        }
        val filter = IntentFilter().apply {
            ACTIONS.keys.forEach { addAction(PREFIX + it) }
            DASH_ACTIONS.forEach { addAction(PREFIX + it) }
        }
        // Dynamic + exported: implicit broadcasts from Key Mapper/Tasker reach it (a manifest
        // receiver wouldn't get implicit broadcasts on Android 8+).
        ContextCompat.registerReceiver(ctx.applicationContext, r, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = r
        LogBus.log("[EXT] listening for app.ridescreen.action.* (Key Mapper / Tasker)")
    }

    @Synchronized
    fun stop(ctx: Context) {
        val r = receiver ?: return
        try { ctx.applicationContext.unregisterReceiver(r) } catch (_: Exception) {}
        receiver = null
    }

    internal fun actionFor(intentAction: String?): ButtonAction? =
        intentAction?.takeIf { it.startsWith(PREFIX) }?.let { ACTIONS[it.removePrefix(PREFIX)] }

    /** True if [intentAction] was one of the dashboard switches (handled here, whether or not it could be done). */
    private fun handleDashboard(ctx: Context, intentAction: String?): Boolean {
        val name = intentAction?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return false
        if (name !in DASH_ACTIONS) return false
        val ok = when (name) {
            "DASH_ON" -> DashboardMode.set(ctx, true)
            "DASH_OFF" -> DashboardMode.set(ctx, false)
            else -> DashboardMode.toggle(ctx) != null
        }
        LogBus.log("[EXT] $name ${if (ok) "done" else "ignored — Android Auto isn't on the dash"}")
        return true
    }

    private fun handle(intentAction: String?) {
        val action = actionFor(intentAction) ?: return
        if (ConnectionState.phase != Phase.STREAMING) {
            LogBus.log("[EXT] ${intentAction?.removePrefix(PREFIX)} ignored — Android Auto isn't on the dash")
            return
        }
        LogBus.log("[EXT] ${intentAction?.removePrefix(PREFIX)} → ${action.id}")
        MediaButtonBridge.injectAction(action)
    }
}
