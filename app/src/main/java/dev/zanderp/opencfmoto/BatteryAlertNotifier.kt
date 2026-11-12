// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Parked with a weak bike battery: one notification, decided by the pure [BatteryAlert] rule:
 * once right at parking (strict limit) and once on the first box reading 10 min or more later
 * (resting limit). Uses the last voltage the box gave us (no new Bluetooth connection, so it never competes with a
 * lock/unlock command) and only if that reading is fresh and the engine is known to be stopped.
 */
object BatteryAlertNotifier {
    private const val PREFS = "battery_alert"
    private const val CHANNEL = "bike_battery"
    private const val NOTIF_ID = 4501
    private const val MAX_READING_AGE_MS = 3 * 60_000L

    /** Call when the bike is parked (parking saved / locked). Never throws. */
    fun onParked(ctx: Context) {
        try {
            val app = ctx.applicationContext
            prefs(app).edit().putLong("parked_at", System.currentTimeMillis()).putBoolean("rest_checked", false).apply()
            val r = PkeBattery.last(app) ?: return
            if (System.currentTimeMillis() - r.voltsAtMs > MAX_READING_AGE_MS) return
            evaluate(app, r.volts?.toDouble(), atRest = false)
        } catch (e: Exception) {
            LogBus.log("[BATT] check failed: ${e.message}")
        }
    }

    /**
     * Call after a one-shot box read (app opened). If the bike has been parked [BatteryAlert.REST_MS]
     * or more, that reading is a resting voltage: checked once per parking. Never throws.
     */
    fun onBoxReading(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val p = prefs(app)
            if (p.getBoolean("rest_checked", false)) return
            val r = PkeBattery.last(app) ?: return
            if (!BatteryAlert.isAtRest(p.getLong("parked_at", 0L), r.voltsAtMs)) return
            p.edit().putBoolean("rest_checked", true).apply()
            evaluate(app, r.volts?.toDouble(), atRest = true)
        } catch (e: Exception) {
            LogBus.log("[BATT] check failed: ${e.message}")
        }
    }

    /** Riding again: the next parking starts a new rest window. */
    fun onRideStart(ctx: Context) {
        try { prefs(ctx.applicationContext).edit().remove("parked_at").putBoolean("rest_checked", false).apply() } catch (_: Exception) {}
    }

    private fun prefs(app: Context) = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun evaluate(app: Context, volts: Double?, atRest: Boolean) {
        val now = System.currentTimeMillis()
        val engine = PkeBattery.engineState(app) ?: return    // unknown engine state: no warning
        val p = prefs(app)
        val rearmed = BatteryAlert.nextRearmed(volts, p.getBoolean("rearmed", false))
        val last = p.getLong("last_warn_at", 0L)
        if (!BatteryAlert.shouldWarn(volts, engine, last, now, rearmed, atRest)) {
            p.edit().putBoolean("rearmed", rearmed).apply()
            return
        }
        p.edit().putLong("last_warn_at", now).putBoolean("rearmed", false).apply()
        val text = app.getString(R.string.cd_battery_low, String.format(Locale.getDefault(), "%.1f", volts))
        LogBus.log("[BATT] low battery warning (${if (atRest) "at rest" else "at parking"})")
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.cd_battery_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            app, 5, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(app.getString(R.string.cd_battery_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}
