// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.Executors

/**
 * Runs one keyless-box command for the home-screen widget. A widget tap has no activity and a
 * broadcast receiver only gets seconds, while a cold connection to the box takes 2-6 s, so the tap
 * starts this short foreground service (allowed from a widget tap), which does [PkeUnlock.send],
 * shows the result as a toast and stops itself. Only the four actions of [PkeUnlock.Action] can be
 * requested; anything else is ignored.
 */
class PkeActionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = PkeUnlock.Action.values().firstOrNull { it.name == intent?.getStringExtra(EXTRA_ACTION) }
        if (action == null) { stopSelf(startId); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification(action),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            LogBus.log("[PKE-widget] can't run in the foreground: $e")
            stopSelf(startId); return START_NOT_STICKY
        }
        val app = applicationContext
        worker.execute {
            LogBus.log("[PKE-widget] ${action.label} requested from the widget")
            val res = try { PkeUnlock.send(app, action) } catch (e: Exception) {
                LogBus.log("[PKE-widget] send failed: $e"); PkeUnlock.Result(false, PkeUnlock.Failure.NO_ANSWER)
            }
            ui.post {
                Toast.makeText(
                    app,
                    if (res.ok) PkeUnlockUi.doneText(app, action) else PkeUnlockUi.message(app, res),
                    if (res.ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
            }
            ui.post { stopSelf(startId) }
        }
        return START_NOT_STICKY
    }

    private fun notification(action: PkeUnlock.Action): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.pke_notif_channel), NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(PkeUnlockUi.actionLabel(this, action))
            .setContentText(getString(R.string.pke_notif_sending))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val EXTRA_ACTION = "pke_action"
        private const val CHANNEL = "pke_actions"
        private const val NOTIF_ID = 7351
        private val worker = Executors.newSingleThreadExecutor()
        private val ui = Handler(Looper.getMainLooper())
    }
}
