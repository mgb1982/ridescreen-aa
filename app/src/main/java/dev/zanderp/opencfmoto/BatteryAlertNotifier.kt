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
 * Parked with a weak bike battery: one notification, decided by the pure [BatteryAlert] rule.
 * Uses the last voltage the box gave us (no new Bluetooth connection, so it never competes with a
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
            val r = PkeBattery.last(app) ?: return
            val now = System.currentTimeMillis()
            if (now - r.voltsAtMs > MAX_READING_AGE_MS) return
            val volts = r.volts?.toDouble()
            val engine = PkeBattery.engineState(app) ?: return    // unknown engine state: no warning
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val rearmed = BatteryAlert.nextRearmed(volts, p.getBoolean("rearmed", false))
            val last = p.getLong("last_warn_at", 0L)
            if (!BatteryAlert.shouldWarn(volts, engine, last, now, rearmed)) {
                p.edit().putBoolean("rearmed", rearmed).apply()
                return
            }
            p.edit().putLong("last_warn_at", now).putBoolean("rearmed", false).apply()
            val text = app.getString(R.string.cd_battery_low, String.format(Locale.getDefault(), "%.1f", volts))
            LogBus.log("[BATT] low battery warning at parking")
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
        } catch (e: Exception) {
            LogBus.log("[BATT] check failed: ${e.message}")
        }
    }
}
