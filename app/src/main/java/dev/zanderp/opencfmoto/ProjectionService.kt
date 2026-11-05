package dev.zanderp.opencfmoto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Minimal foreground service of type `mediaProjection`. Android 14+ requires an FGS of this
 * type to be running before MediaProjectionManager.getMediaProjection() is called.
 *
 * While mirroring, [setKeepScreenOn] holds a dim wake lock so Entire-screen capture does not go
 * black the moment the phone times out (single-app still needs that app visible).
 */
class ProjectionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_KEEP_SCREEN) {
            // Only a live mirror may take the wake lock. If the mirror already stopped (the keep-screen
            // intent raced the teardown), going foreground as mediaProjection without a projection
            // throws SecurityException on Android 14+ (2.3: 5 crashes in a week). Just leave.
            if (!isForeground) { stopSelf(startId); return START_NOT_STICKY }
            applyKeepScreen(intent.getBooleanExtra(EXTRA_KEEP, false))
            return START_NOT_STICKY
        }
        try {
            ensureForeground()
        } catch (e: Exception) {   // SecurityException / ForegroundServiceStartNotAllowedException
            LogBus.log("[MIRROR] could not go foreground (no screen-capture permission?): ${e.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground() {
        if (isForeground) return
        val channelId = "opencfmoto_projection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channelId, getString(R.string.notif_mirror_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_mirror_title))
            .setContentText(getString(R.string.notif_mirror_text))
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
        isForeground = true
    }

    override fun onDestroy() {
        applyKeepScreen(false)
        isForeground = false
        super.onDestroy()
    }

    private fun applyKeepScreen(on: Boolean) {
        try {
            if (on) {
                if (wakeLock?.isHeld == true) return
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "opencfmoto:mirror",
                )
                wl.setReferenceCounted(false)
                wl.acquire(4 * 60 * 60 * 1000L) // 4h ride ceiling
                wakeLock = wl
                LogBus.log("[MIRROR] keep-screen-on wake lock held")
            } else {
                wakeLock?.let { if (it.isHeld) it.release() }
                wakeLock = null
            }
        } catch (e: Exception) {
            LogBus.log("[MIRROR] keep-screen-on failed: $e")
        }
    }

    companion object {
        private const val ACTION_KEEP_SCREEN = "dev.zanderp.opencfmoto.KEEP_SCREEN"
        private const val EXTRA_KEEP = "keep"

        /** True once startForeground() has completed — poll this before getMediaProjection(). */
        @Volatile var isForeground = false
            private set

        fun start(ctx: Context) {
            isForeground = false
            val i = Intent(ctx, ProjectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            // Do NOT startService(KEEP_SCREEN) here — on API 34+ that re-enters this FGS with type
            // mediaProjection after teardown and crashes (no active MediaProjection). onDestroy
            // already releases the wake lock.
            isForeground = false
            try {
                ctx.stopService(Intent(ctx, ProjectionService::class.java))
            } catch (_: Exception) {
            }
        }

        fun setKeepScreenOn(ctx: Context, on: Boolean) {
            // Never start a new ProjectionService just for the wake lock — only poke a live FGS.
            if (!isForeground) return
            val i = Intent(ctx, ProjectionService::class.java).apply {
                action = ACTION_KEEP_SCREEN
                putExtra(EXTRA_KEEP, on)
            }
            try {
                ctx.startService(i)
            } catch (_: Exception) {
                // Service may already be stopping.
            }
        }
    }
}
