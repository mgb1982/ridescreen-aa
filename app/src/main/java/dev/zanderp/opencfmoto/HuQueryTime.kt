// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Body for `0x10451` — reply to bike `ECP_C2P_QUERY_TIME` (`0x10450`).
 *
 * 2.0.11 / 2.0.12 sent Carbit's `time` + `dateTime` to every HU. Griffin / X-Cape / Voge / QJ
 * jumped hours, so 2.0.13 empty-acked again. Official Zontes Smart also sends `currentTime`
 * (epoch UTC ms + local TZ offset) and `currentTimeZone`. Clock lab can force any of those
 * bodies; [ackIfZontes] still gates the 2.0.16-pre default on channel `21340`.
 */
internal object HuQueryTime {
    internal const val ZONTES_125X_CHANNEL = "21340"

    private val dateTimeFmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    // Carbit Ride (decompiled handler, via MOTO-HUB's querytime.go): these two head-unit models want the
    // date separated by colons. 21313 is the Zontes 350D/350E dash.
    internal val COLON_DATE_CHANNELS = setOf("21312", "21313")
    private val colonDateTimeFmt = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.US)

    data class Ack(
        val payload: ByteArray,
        val dateTime: String,
        val timeMillis: Long,
        val currentTime: Long,
        val timeZone: String,
    )

    fun dateTimeString(
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
        channel: String? = null,
    ): String {
        val ch = channel?.trim()
        val fmt = if (ch != null && ch in COLON_DATE_CHANNELS) colonDateTimeFmt else dateTimeFmt
        return synchronized(fmt) {
            fmt.timeZone = zone
            String.format(Locale.US, "%s:%03d", fmt.format(Date(nowMillis)), (nowMillis % 1000L).toInt())
        }
    }

    /**
     * Default answer for every dash that is not a Zontes clock-fix channel (v2.5).
     *
     * We used to answer these with an EMPTY 0x10451. A rider's Voge log (MOTO-HUB, Aug 2026: one 0x10450
     * after the handshake, no 0x10600 in five days) shows what that does: the only clock question the dash
     * ever asks gets no clock, so it never learns the time. 2.0.11/2.0.12 sent `time` + `dateTime` to all
     * and Griffin / X-Cape / Voge / QJ jumped hours; this body follows what MOTO-HUB ships instead:
     * `time` (epoch ms), `currentTime` (epoch + local offset) and `currentTimeZone`, and `dateTime` ONLY
     * when the dash itself advertised `supportSyncCorrectTime` (Carbit does the same).
     */
    fun standard(
        channel: String?,
        dashWantsDateTime: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Ack {
        val currentTime = nowMillis + zone.getOffset(nowMillis)
        val dateTime = if (dashWantsDateTime) dateTimeString(nowMillis, zone, channel) else ""
        val json = "{\"time\":$nowMillis,\"currentTime\":$currentTime,\"currentTimeZone\":\"${zone.id}\"" +
            (if (dashWantsDateTime) ",\"dateTime\":\"$dateTime\"" else "") + "}"
        return Ack(json.toByteArray(Charsets.UTF_8), dateTime, nowMillis, currentTime, zone.id)
    }

    /** Carbit / 2.0.12 body — `time` + `dateTime` only. */
    fun carbit(
        nowMillis: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Ack {
        val dateTime = dateTimeString(nowMillis, zone)
        val json = "{\"time\":$nowMillis,\"dateTime\":\"$dateTime\"}"
        return Ack(json.toByteArray(Charsets.UTF_8), dateTime, nowMillis, nowMillis, zone.id)
    }

    /** Zontes Smart extras — not gated on channel. Clock lab uses this when the knob is on. */
    fun zontesOem(
        nowMillis: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Ack {
        val dateTime = dateTimeString(nowMillis, zone)
        val currentTime = nowMillis + zone.getOffset(nowMillis)
        val json = "{\"time\":$nowMillis,\"currentTime\":$currentTime," +
            "\"currentTimeZone\":\"${zone.id}\",\"dateTime\":\"$dateTime\"}"
        return Ack(json.toByteArray(Charsets.UTF_8), dateTime, nowMillis, currentTime, zone.id)
    }

    fun ackIfZontes(
        channel: String?,
        nowMillis: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Ack? {
        if (channel?.trim() != ZONTES_125X_CHANNEL) return null
        return zontesOem(nowMillis, zone)
    }
}
