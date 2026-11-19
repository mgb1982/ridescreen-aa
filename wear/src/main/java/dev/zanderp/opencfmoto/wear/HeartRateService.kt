// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the heart-rate sensor running while the phone records a trip, so the pulse reaches the trip (and the
 * trip video) even with the wrist down and the screen off. Before this the pulse was only read while the watch
 * app was open and awake.
 *
 * The phone says "on" when a trip starts, again every minute, and "off" when it ends ([PhoneLink.PATH_HR]).
 * With no "on" for [IDLE_STOP_MS] the service stops by itself (phone gone, trip ended without the "off").
 * It only runs with the heart-rate permission already granted and shows an ongoing notification.
 *
 * Android only lets a health foreground service use the sensor if it was started while the app was visible, so
 * a start from the background message may be refused: then it starts the next time the app opens
 * ([WearMainActivity.onResume], which the "ride started" notification brings up).
 */
class HeartRateService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null
    private var lastSentAt = 0L

    private val watchdog = object : Runnable {
        override fun run() {
            if (!wantedNow()) { stopSelf(); return }
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!wantedNow() || !hasPermission(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            val n = notification()
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            else startForeground(NOTIF_ID, n)
        } catch (_: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!running) {
            running = true
            register()
            handler.postDelayed(watchdog, WATCHDOG_MS)
        }
        return START_NOT_STICKY
    }

    private fun register() {
        val sm = getSystemService(SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        // The wake-up variant delivers with the screen off without keeping the CPU awake ourselves.
        val sensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE, true) ?: sm.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return
        try { sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HEART_RATE || event.values.isEmpty()) return
        val bpm = event.values[0].toInt()
        val now = SystemClock.elapsedRealtime()
        if (bpm in 30..240 && now - lastSentAt >= SEND_EVERY_MS) {
            lastSentAt = now
            PhoneLink.send(this, PhoneLink.PATH_HEART, "$bpm;${System.currentTimeMillis()}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(watchdog)
        try { sensorManager?.unregisterListener(this) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_heart), NotificationManager.IMPORTANCE_LOW))
        val open = Intent(this, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_moto)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.heart_active))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL = "heart"
        private const val NOTIF_ID = 2
        const val IDLE_STOP_MS = 3 * 60_000L
        private const val WATCHDOG_MS = 30_000L
        private const val SEND_EVERY_MS = 5_000L

        /** elapsedRealtime of the phone's last "on"; 0 = not wanted. */
        @Volatile private var wantedAt = 0L
        @Volatile var running = false
            private set

        fun wantedNow(now: Long = SystemClock.elapsedRealtime()): Boolean = wantedAt > 0L && now - wantedAt <= IDLE_STOP_MS

        fun hasPermission(ctx: Context): Boolean {
            val p = if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS
            return ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        }

        /** The phone's "on" / "off". */
        fun onPhoneMessage(ctx: Context, text: String) {
            when (text.trim()) {
                "on" -> { wantedAt = SystemClock.elapsedRealtime(); startIfWanted(ctx) }
                "off" -> { wantedAt = 0L; ctx.stopService(Intent(ctx, HeartRateService::class.java)) }
            }
        }

        /** Start it when the phone wants the pulse and it is not already running; false if Android refused. */
        fun startIfWanted(ctx: Context): Boolean {
            if (!wantedNow() || running || !hasPermission(ctx)) return running
            return try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, HeartRateService::class.java)); true
            } catch (_: Exception) { false }   // background start not allowed: WearMainActivity retries when it opens
        }
    }
}
