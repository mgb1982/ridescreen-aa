// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent

/**
 * Per-device handling of media keys (AVRCP / consumer-control) while handlebar capture is on.
 *
 * Everything here is opt-in: with nothing taught, media keys behave exactly as before. Two things can be taught
 * from Controls ▸ Customise buttons ▸ "Assign a remote button…":
 *  • a media key from a device → any handlebar gesture (device-scoped, so the bike's own bars are untouched);
 *  • a "music-only" device (e.g. the intercom): its media keys are handed back to the music app instead of
 *    driving Android Auto.
 * Devices are told apart by the name of the input device that delivered the key. Every media key is logged with
 * that name so we can tell whether the remote and the intercom are distinguishable on a given phone.
 */
object MediaKeyRouting {
    private const val PREFS = "opencfmoto_media_key_routing"
    private const val KEY_MAP = "map"
    private const val KEY_MUSIC = "music_devices"

    /** Name of the input device that delivered [ke] (blank when Android doesn't say). */
    fun deviceName(ke: KeyEvent): String {
        val dev = try { InputDevice.getDevice(ke.deviceId) } catch (_: Exception) { null }
        return dev?.name?.trim().orEmpty()
    }

    /** One log-friendly line describing where a key came from. */
    fun describe(ke: KeyEvent): String {
        val dev = try { InputDevice.getDevice(ke.deviceId) } catch (_: Exception) { null }
        return if (dev == null) {
            "device=? id=${ke.deviceId} src=0x${Integer.toHexString(ke.source)}"
        } else {
            "device=\"${dev.name}\" id=${ke.deviceId} vid:pid=${dev.vendorId}:${dev.productId} " +
                "src=0x${Integer.toHexString(ke.source)} virtual=${dev.isVirtual}"
        }
    }

    fun gestureFor(ctx: Context, device: String, keyCode: Int): ButtonGesture? {
        if (device.isEmpty()) return null
        val id = loadMap(ctx)["$device|$keyCode"] ?: return null
        return ButtonGesture.entries.firstOrNull { it.id == id }
    }

    fun teachKey(ctx: Context, device: String, keyCode: Int, gesture: ButtonGesture) {
        val map = loadMap(ctx).toMutableMap()
        map["$device|$keyCode"] = gesture.id
        saveMap(ctx, map)
    }

    fun isMusicDevice(ctx: Context, device: String): Boolean =
        device.isNotEmpty() && prefs(ctx).getStringSet(KEY_MUSIC, emptySet())!!.contains(device)

    fun addMusicDevice(ctx: Context, device: String) {
        if (device.isEmpty()) return
        val set = HashSet(prefs(ctx).getStringSet(KEY_MUSIC, emptySet())!!)
        set.add(device)
        prefs(ctx).edit().putStringSet(KEY_MUSIC, set).apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_MAP).remove(KEY_MUSIC).apply()
    }

    fun summary(ctx: Context): String {
        val maps = loadMap(ctx).size
        val music = prefs(ctx).getStringSet(KEY_MUSIC, emptySet())!!.size
        return "$maps remapped media key(s), $music music-only device(s)"
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadMap(ctx: Context): Map<String, String> {
        val raw = prefs(ctx).getString(KEY_MAP, null) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.split('\n').forEach { line ->
            val i = line.lastIndexOf('=')
            if (i > 0) out[line.substring(0, i)] = line.substring(i + 1)
        }
        return out
    }

    private fun saveMap(ctx: Context, map: Map<String, String>) {
        prefs(ctx).edit()
            .putString(KEY_MAP, map.entries.joinToString("\n") { "${it.key}=${it.value}" })
            .apply()
    }
}
