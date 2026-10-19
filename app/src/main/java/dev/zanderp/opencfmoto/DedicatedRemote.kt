// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent

/**
 * v2.3 · A handlebar remote that drives Android Auto ONLY, while the intercom keeps music and
 * volume (Manel, 19 Oct: "the remote for Android Auto, the Q58 Max for media").
 *
 * Cheap handlebar remotes pair as an *input device* (HID); headsets/intercoms send AVRCP commands.
 * The key source (see [available]) tells them apart by device, so the intercom keeps the music.
 * Keys are taken only while Android Auto is live on the dash; otherwise the remote works as usual.
 */
object DedicatedRemote {
    private const val PREFS = "dedicated_remote"
    private const val KEY_DESCRIPTOR = "descriptor"
    private const val KEY_NAME = "name"

    data class Remote(val descriptor: String, val name: String)

    fun chosen(ctx: Context): Remote? {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = p.getString(KEY_DESCRIPTOR, null) ?: return null
        return Remote(d, p.getString(KEY_NAME, null) ?: "?")
    }

    fun choose(ctx: Context, dev: InputDevice) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DESCRIPTOR, dev.descriptor).putString(KEY_NAME, dev.name).apply()
        LogBus.log("[REMOTE] dedicated to Android Auto: '${dev.name}'")
    }

    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        LogBus.log("[REMOTE] dedicated remote removed")
    }

    /** Connected physical key devices (remotes, keyboards, gamepads), excluding the phone's own. */
    fun candidates(ctx: Context): List<InputDevice> {
        val im = ctx.getSystemService(InputManager::class.java) ?: return emptyList()
        return im.inputDeviceIds.toList().mapNotNull { im.getInputDevice(it) }.filter { d ->
            !d.isVirtual && (if (android.os.Build.VERSION.SDK_INT >= 29) d.isExternal else true) &&
                (d.sources and (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_GAMEPAD)) != 0
        }.distinctBy { it.descriptor }
    }

    /**
     * Whether any key source can feed [onKey] right now. The accessibility service that used to do
     * it was removed (19 Oct): Play Protect hard-blocks sideloaded APKs that declare one. A
     * replacement that needs no accessibility (Shizuku + evdev, or media keys by device) plugs in
     * here; until then the Controls card stays hidden.
     */
    fun available(@Suppress("UNUSED_PARAMETER") ctx: Context): Boolean = false

    /** What each key of a remote does in Android Auto. Media-style remotes become a D-pad. */
    internal fun actionFor(keyCode: Int): ButtonAction? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_DPAD_UP -> ButtonAction.DPAD_UP
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> ButtonAction.DPAD_DOWN
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT -> ButtonAction.DPAD_LEFT
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT -> ButtonAction.DPAD_RIGHT
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> ButtonAction.BACK
        KeyEvent.KEYCODE_HOME -> ButtonAction.HOME
        else -> null
    }

    /** Keys that act as OK: a tap = OK, hold = Home, double tap = Back. */
    internal fun isOkKey(keyCode: Int) = keyCode in setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENDCALL,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A,
    )

    private val main = Handler(Looper.getMainLooper())
    private var okDownAt = 0L
    private var pendingTap: Runnable? = null
    @Volatile var teachListener: ((KeyEvent) -> Unit)? = null

    /** From the key source: true = the key was ours and is swallowed. */
    fun onKey(ctx: Context, e: KeyEvent): Boolean {
        val dev = e.device ?: return false
        val mine = chosen(ctx) ?: return false
        if (dev.descriptor != mine.descriptor) return false
        teachListener?.let { it(e); return true }
        // Only while Android Auto is on the dash; otherwise the remote does its normal job.
        if (ConnectionState.phase != Phase.STREAMING) return false
        val code = e.keyCode
        if (isOkKey(code)) { okKey(e); return true }
        val action = actionFor(code) ?: RemotePad.gestureFor(ctx, code)?.let { g ->
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) MediaButtonBridge.injectGesture(g)
            return true
        } ?: return true   // an unknown key from the remote: swallow it, never leak to media
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) MediaButtonBridge.injectAction(action)
        return true
    }

    private fun okKey(e: KeyEvent) {
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) okDownAt = e.eventTime
            KeyEvent.ACTION_UP -> {
                val held = e.eventTime - okDownAt
                if (held >= 600) { cancelTap(); MediaButtonBridge.injectAction(ButtonAction.HOME); return }
                val waiting = pendingTap
                if (waiting != null) { cancelTap(); MediaButtonBridge.injectAction(ButtonAction.BACK); return }
                val r = Runnable { pendingTap = null; MediaButtonBridge.injectAction(ButtonAction.SELECT) }
                pendingTap = r
                main.postDelayed(r, 300)
            }
        }
    }

    private fun cancelTap() { pendingTap?.let { main.removeCallbacks(it) }; pendingTap = null }
}
