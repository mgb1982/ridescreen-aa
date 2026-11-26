// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import dev.zanderp.opencfmoto.aa.AaInput
import org.json.JSONObject

/**
 * v2.5 · Handlebar buttons the dash sends over the projection Wi‑Fi (PXC `0x10350`,
 * EasyConn `ECP_C2P_SEND_BTN_EVENT_TO_PHONE`), as `{"code":<key>,"type":<2 tap|3 double|4 hold>}`.
 *
 * Read from the official Zontes Smart EU app (`findings/zontes-handlebar-buttons`): it accepts types 2 and 4
 * only and spaces next/previous 500 ms apart. Which Zontes dashes send it is **unknown** (none of our logs has
 * it; the 368G is the suspect), so this is **off by default** ([isEnabled]) and every frame is logged either way.
 *
 * What it does when on:
 *   • music (play/pause, next, previous) and volume ± in both button modes;
 *   • arrows, OK, back and assistant only while "handlebar buttons control Android Auto" ([ButtonMode]) is on.
 */
object DashButtons {
    const val CMD_SEND_BTN_EVENT = 0x10350

    // EasyConn MultifunctionKey codes
    const val CODE_PLAY = 4160
    const val CODE_NEXT = 4161
    const val CODE_PREVIOUS = 4162
    const val CODE_PAUSE = 4163
    const val CODE_STOP = 4164
    const val CODE_PLAY_PAUSE = 4167
    const val CODE_VOLUME_UP = 4176
    const val CODE_VOLUME_DOWN = 4177
    const val CODE_ASSISTANT = 4144
    const val CODE_BACK = 4224
    const val CODE_FOCUS_BACKWARD = 4353
    const val CODE_FOCUS_FORWARD = 4354
    const val CODE_LEFT = 4355
    const val CODE_RIGHT = 4356
    const val CODE_UP = 4357
    const val CODE_DOWN = 4358
    const val CODE_ENTER = 4359

    const val TYPE_TAP = 2
    const val TYPE_DOUBLE = 3
    const val TYPE_HOLD = 4

    /** Same gap the official app keeps between two next/previous presses. */
    const val TRACK_KEY_GAP_MS = 500L

    data class Event(val code: Int, val type: Int)

    /** What one dash button press turns into. */
    sealed class Command {
        enum class Media { PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS }
        data class Music(val key: Media) : Command()
        data class Volume(val up: Boolean) : Command()
        data class AndroidAuto(val action: ButtonAction) : Command()
    }

    /** Pure: the JSON body of a `0x10350` frame, or null when it isn't one we can read. */
    fun parse(body: String): Event? = try {
        val j = JSONObject(body)
        val code = j.optInt("code", 0)
        if (code == 0) null else Event(code, j.optInt("type", 0))
    } catch (_: Exception) {
        null
    }

    /**
     * Pure: what [event] does. Null = ignored (unknown key, a double tap — which the official app drops too —
     * or an Android Auto key while the buttons are left to music).
     */
    fun map(event: Event, controlAa: Boolean): Command? {
        if (event.type != TYPE_TAP && event.type != TYPE_HOLD) return null
        val music = when (event.code) {
            CODE_PLAY -> Command.Media.PLAY
            CODE_PAUSE, CODE_STOP -> Command.Media.PAUSE
            CODE_PLAY_PAUSE -> Command.Media.PLAY_PAUSE
            CODE_NEXT -> Command.Media.NEXT
            CODE_PREVIOUS -> Command.Media.PREVIOUS
            else -> null
        }
        if (music != null) return Command.Music(music)
        when (event.code) {
            CODE_VOLUME_UP -> return Command.Volume(up = true)
            CODE_VOLUME_DOWN -> return Command.Volume(up = false)
        }
        if (!controlAa) return null
        val action = when (event.code) {
            CODE_LEFT -> ButtonAction.DPAD_LEFT
            CODE_RIGHT -> ButtonAction.DPAD_RIGHT
            CODE_UP -> ButtonAction.DPAD_UP
            CODE_DOWN -> ButtonAction.DPAD_DOWN
            CODE_ENTER -> ButtonAction.SELECT
            CODE_BACK -> ButtonAction.BACK
            CODE_ASSISTANT -> ButtonAction.ASSISTANT
            CODE_FOCUS_BACKWARD -> ButtonAction.KNOB_BACK
            CODE_FOCUS_FORWARD -> ButtonAction.KNOB_FORWARD
            else -> null
        } ?: return null
        return Command.AndroidAuto(action)
    }

    /** Next/previous closer than [TRACK_KEY_GAP_MS] to the last one are dropped, like the official app. */
    class TrackKeyGate(private val gapMs: Long = TRACK_KEY_GAP_MS) {
        private var lastAt = Long.MIN_VALUE / 2

        fun allow(command: Command, nowMs: Long): Boolean {
            val trackKey = command is Command.Music &&
                (command.key == Command.Media.NEXT || command.key == Command.Media.PREVIOUS)
            if (!trackKey) return true
            if (nowMs - lastAt < gapMs) return false
            lastAt = nowMs
            return true
        }
    }

    private val gate = TrackKeyGate()

    // ── setting (off by default) ─────────────────────────────────────────────────────────────────

    private const val PREF = "dash_buttons"
    private const val KEY_ENABLED = "wifiButtons"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    // ── runtime ──────────────────────────────────────────────────────────────────────────────────

    /** One `0x10350` body from the dash. Always logged; acted on only when [isEnabled]. */
    fun onFrame(context: Context, body: String, log: (String) -> Unit) {
        val event = parse(body)
        if (event == null) {
            log("[BTN] dash button frame not understood: $body")
            return
        }
        val controlAa = ButtonMode.isControlAa(context)
        val command = map(event, controlAa)
        val what = "dash button code=${event.code} type=${event.type}"
        if (!isEnabled(context)) {
            log("[BTN] $what (Wi‑Fi buttons off in Controls — ignored)")
            return
        }
        if (command == null) {
            log("[BTN] $what → nothing (${if (controlAa) "unmapped key" else "buttons left to music"})")
            return
        }
        if (!gate.allow(command, SystemClock.elapsedRealtime())) {
            log("[BTN] $what → skipped (less than ${TRACK_KEY_GAP_MS} ms after the last track key)")
            return
        }
        log("[BTN] $what → $command")
        when (command) {
            is Command.Music -> music(context, command.key, controlAa, log)
            is Command.Volume -> volume(context, command.up, log)
            is Command.AndroidAuto -> {
                val bridge = MediaButtonBridge.instance
                if (bridge == null) log("[BTN] no Android Auto session — dash key dropped")
                else MediaButtonBridge.injectAction(command.action)
            }
        }
    }

    /**
     * Music mode: a plain media key to whatever player owns the media session. Android Auto mode: our own
     * session is the active one (it turns handlebar keys into navigation), so the key goes to Android Auto
     * over its input channel instead, where it reaches the player Android Auto is showing.
     */
    private fun music(context: Context, key: Command.Media, controlAa: Boolean, log: (String) -> Unit) {
        if (controlAa) {
            val aaKey = when (key) {
                Command.Media.NEXT -> AaInput.KEY_NEXT
                Command.Media.PREVIOUS -> AaInput.KEY_PREVIOUS
                else -> AaInput.KEY_PLAY_PAUSE
            }
            val sink = AaVideoBridge.keySink
            if (sink == null) log("[BTN] no Android Auto session — music key dropped") else sink(aaKey)
            return
        }
        val keycode = when (key) {
            Command.Media.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            Command.Media.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            Command.Media.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            Command.Media.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Command.Media.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
        } catch (e: Exception) {
            log("[BTN] media key failed: $e")
        }
    }

    /** Skipped while the handlebar ▲/▼ drive Android Auto: moving the pinned volume would read as a knob step. */
    private fun volume(context: Context, up: Boolean, log: (String) -> Unit) {
        if (MediaButtonBridge.instance?.isVolumePinned == true) {
            log("[BTN] volume held for Android Auto (▲/▼ drive it) — dash volume key ignored")
            return
        }
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0,
            )
        } catch (e: Exception) {
            log("[BTN] volume key failed: $e")
        }
    }
}
