// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Day/night as decided by the dash's own light sensor (log only for now).
 *
 * The official Zontes app handles `ECP_C2P_CARINFO_NIGHTMODE`, command 0x10200 (66048), dash → phone,
 * JSON `{"night": true|false}`. Only the decision, no lux value. Not seen yet in any RideScreen log, so
 * it is unknown which dashes send it and when. Kept here so a later step can feed the map theme.
 */
object DashNight {
    @Volatile var lastNight: Boolean? = null
    @Volatile var lastAtMs: Long = 0L

    /** Pure: the `night` field of the payload, null when it is missing or not JSON-like. */
    internal fun parse(body: String): Boolean? {
        val m = Regex("\"night\"\\s*:\\s*(true|false|1|0)").find(body) ?: return null
        return when (m.groupValues[1]) { "true", "1" -> true; else -> false }
    }

    fun note(body: String, nowMs: Long, log: (String) -> Unit) {
        val n = parse(body)
        log("[NIGHT] dash says ${when (n) { true -> "NIGHT"; false -> "DAY"; null -> "? (unreadable: ${body.take(60)})" }}")
        if (n != null) { lastNight = n; lastAtMs = nowMs }
    }
}
