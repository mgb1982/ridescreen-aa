// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Day/night as decided by the dash's own light sensor (log only for now).
 *
 * The official Zontes app handles `ECP_C2P_CARINFO_NIGHTMODE`, command 0x10200 (66048), dash → phone,
 * JSON `{"night": true|false}`. Only the decision, no lux value. Seen in a GPX DZ2 log (14 Nov 2026):
 * `false` at 09:01 and `true` at 17:57, one message right after CLIENT_INFO. Unknown whether a dash repeats
 * it when the light changes mid-ride, so [current] only trusts it for [VALID_MS] and the map theme falls back
 * to the phone / clock after that ([NightPrefs.isNightNow], Auto theme only).
 */
object DashNight {
    @Volatile var lastNight: Boolean? = null
    @Volatile var lastAtMs: Long = 0L

    /** How long the dash's last answer is trusted without a newer one. */
    const val VALID_MS = 20 * 60_000L

    /** Set by the Android Auto session: push the theme again when the dash changes its mind. */
    @Volatile var onChange: (() -> Unit)? = null

    /** A new dash connection starts: what the previous one said no longer counts. */
    fun reset() { lastNight = null; lastAtMs = 0L }

    /** Pure: the dash's answer if it is recent enough, else null (use the phone / clock). */
    internal fun effective(last: Boolean?, atMs: Long, nowMs: Long, validMs: Long = VALID_MS): Boolean? =
        if (last != null && nowMs - atMs in 0..validMs) last else null

    fun current(nowMs: Long = System.currentTimeMillis()): Boolean? = effective(lastNight, lastAtMs, nowMs)

    /** Pure: the `night` field of the payload, null when it is missing or not JSON-like. */
    internal fun parse(body: String): Boolean? {
        val m = Regex("\"night\"\\s*:\\s*(true|false|1|0)").find(body) ?: return null
        return when (m.groupValues[1]) { "true", "1" -> true; else -> false }
    }

    fun note(body: String, nowMs: Long, log: (String) -> Unit) {
        val n = parse(body)
        // Only changes are logged (and kept in the trace): a dash that repeats it must not flood either.
        if (n != null && n == lastNight) { lastAtMs = nowMs; return }
        log("[NIGHT] dash says ${when (n) { true -> "NIGHT"; false -> "DAY"; null -> "? (unreadable: ${body.take(60)})" }}")
        if (n != null) {
            lastNight = n; lastAtMs = nowMs
            try { onChange?.invoke() } catch (_: Exception) {}
        }
    }
}
