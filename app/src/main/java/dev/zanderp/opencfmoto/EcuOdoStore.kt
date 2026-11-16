// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context

/** Per-bike memory for [OdoFromEcu] (own preferences file) and the actions the Maintenance screen offers. */
internal object EcuOdoStore {
    private const val PREFS = "ecu_odo"

    class Pending(val km: Int, val fromKm: Int?)

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun k(ctx: Context, name: String) = "${name}_${Maintenance.bikeKey(ctx)}"

    private fun state(ctx: Context): OdoFromEcu.State =
        OdoFromEcu.State.values().getOrElse(p(ctx).getInt(k(ctx, "state"), 0)) { OdoFromEcu.State.UNASKED }

    /** Called once per connection with the PID 31 value. Returns the log line for a rejected value, else null. */
    fun onReading(ctx: Context, km: Int, nowMs: Long = System.currentTimeMillis()): String? {
        val pr = p(ctx)
        val applied = pr.getInt(k(ctx, "applied"), -1).takeIf { it >= 0 }
        val appliedAt = pr.getLong(k(ctx, "appliedAt"), 0L).takeIf { it > 0 }
        val d = OdoFromEcu.decide(state(ctx), applied, appliedAt, km, nowMs, Maintenance.load(ctx).odometer?.toInt())
        if (d is OdoFromEcu.Decision.Invalid) return null
        val e = pr.edit().putInt(k(ctx, "seen"), km).putLong(k(ctx, "seenAt"), nowMs)
        var line: String? = null
        when (d) {
            is OdoFromEcu.Decision.Ask -> e.putInt(k(ctx, "pendingKm"), km).remove(k(ctx, "pendingFrom"))
            is OdoFromEcu.Decision.Apply -> {
                Maintenance.setOdometer(ctx, km.toDouble())
                e.putInt(k(ctx, "applied"), km).putLong(k(ctx, "appliedAt"), nowMs).remove(k(ctx, "pendingKm")).remove(k(ctx, "pendingFrom"))
            }
            is OdoFromEcu.Decision.Reject -> {
                e.putInt(k(ctx, "pendingKm"), d.to).putInt(k(ctx, "pendingFrom"), d.from)
                line = "[ODO] ECU km went from ${d.from} to ${d.to} — not applied"
            }
            else -> {}
        }
        e.apply()
        return line
    }

    /** What the Maintenance screen should offer: a first-time question or a warning about a rejected value. */
    fun pending(ctx: Context): Pending? {
        val pr = p(ctx)
        val km = pr.getInt(k(ctx, "pendingKm"), -1).takeIf { it >= 0 } ?: return null
        if (state(ctx) == OdoFromEcu.State.DECLINED) return null
        return Pending(km, pr.getInt(k(ctx, "pendingFrom"), -1).takeIf { it >= 0 })
    }

    /** Last ECU reading (km, epoch ms), if any. */
    fun seen(ctx: Context): Pair<Int, Long>? {
        val pr = p(ctx)
        val km = pr.getInt(k(ctx, "seen"), -1).takeIf { it >= 0 } ?: return null
        return km to pr.getLong(k(ctx, "seenAt"), 0L)
    }

    /** The rider accepts [km] (pending value or last reading): remember it, turn the source on and set the odometer. */
    fun accept(ctx: Context, km: Int, nowMs: Long = System.currentTimeMillis()) {
        p(ctx).edit().putInt(k(ctx, "state"), OdoFromEcu.State.ACCEPTED.ordinal)
            .putInt(k(ctx, "applied"), km).putLong(k(ctx, "appliedAt"), nowMs)
            .remove(k(ctx, "pendingKm")).remove(k(ctx, "pendingFrom")).apply()
        Maintenance.setOdometer(ctx, km.toDouble())
    }

    /** First-time question answered "no": do not ask again until the rider uses the Maintenance button. */
    fun decline(ctx: Context) {
        p(ctx).edit().putInt(k(ctx, "state"), OdoFromEcu.State.DECLINED.ordinal)
            .remove(k(ctx, "pendingKm")).remove(k(ctx, "pendingFrom")).apply()
    }

    /** Warning dismissed: keep the source on and the old odometer; the next connection compares again. */
    fun dismissWarning(ctx: Context) {
        p(ctx).edit().remove(k(ctx, "pendingKm")).remove(k(ctx, "pendingFrom")).apply()
    }
}
