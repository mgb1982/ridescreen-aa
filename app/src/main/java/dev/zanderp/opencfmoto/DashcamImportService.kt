package dev.zanderp.opencfmoto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Network
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * B2 · downloads the chosen bike camera clips in a foreground service (dataSync) with a progress
 * notification (clip N of M, MB, cancel), a high-performance Wi-Fi lock and a CPU wake lock, so it keeps
 * going with the screen off or the app in the background. Locks, the bike Wi-Fi hold ([ImportLink]) and
 * the "import running" flag are always released at the end.
 *
 * B3 · per clip up to [ATTEMPTS] tries, resuming with HTTP Range from what is already written (the dash
 * supports it); a failed clip doesn't stop the rest; the partial file is only deleted once all tries are
 * spent. B4 · while running, [DashcamImport.active] keeps keyless-box reads and automatic connects off.
 */
class DashcamImportService : Service() {
    companion object {
        private const val EXTRA_CLIPS = "clips"
        private const val ACTION_CANCEL = "dev.zanderp.opencfmoto.DASHCAM_CANCEL"
        private const val CHANNEL = "dashcam_import"
        private const val NOTIF_PROGRESS = 7301
        private const val NOTIF_DONE = 7302
        const val ATTEMPTS = 3

        fun start(ctx: Context, clips: List<DashcamImport.Remote>) {
            DashcamImport.active = true
            androidx.core.content.ContextCompat.startForegroundService(
                ctx, Intent(ctx, DashcamImportService::class.java).putExtra(EXTRA_CLIPS, DashcamImport.encode(clips)),
            )
        }
    }

    @Volatile private var cancelled = false
    private var worker: Thread? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        // Foreground first, on every path (a startForegroundService that stops early must still promote).
        try {
            ServiceCompat.startForeground(this, NOTIF_PROGRESS, progress(0, 0, 0L),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        } catch (e: Exception) {
            DashcamImport.log("can't run in the foreground: $e")
            finish("no foreground"); return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            DashcamImport.log("cancel tapped")
            worker?.interrupt()
            if (worker == null) finish("cancelled")
            return START_NOT_STICKY
        }
        if (worker != null) return START_NOT_STICKY
        val clips = DashcamImport.decode(intent?.getStringExtra(EXTRA_CLIPS))
        if (clips.isEmpty()) { finish("nothing to do"); return START_NOT_STICKY }
        DashcamImport.active = true
        acquireLocks()
        worker = Thread({ run(clips) }, "dashcam-import").also { it.start() }
        return START_NOT_STICKY
    }

    private fun run(clips: List<DashcamImport.Remote>) {
        var imported = 0; var present = 0; var failed = 0
        try {
            val net = ImportLink.network ?: awaitNetwork()
            if (net == null) {
                DashcamImport.log("no bike Wi-Fi for the import")
                failed = clips.size
                return
            }
            DashcamImport.log("service: ${clips.size} clips, ${clips.sumOf { it.size } / 1048576} MB")
            for ((i, c) in clips.withIndex()) {
                if (cancelled) break
                val display = displayName(c)
                if (exists(display)) { present++; continue }
                notifyProgress(i + 1, clips.size, c.size)
                val ok = downloadWithRetries(ImportLink.network ?: net, c, display)
                if (ok) imported++ else if (!cancelled) failed++
            }
        } catch (e: Exception) {
            DashcamImport.log("service error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            DashcamImport.log("done: imported $imported, already there $present, failed $failed${if (cancelled) " (cancelled)" else ""}")
            notifyDone(imported, present, failed)
            finish(if (cancelled) "cancelled" else "done")
        }
    }

    /** The picker hands over a live network; if it blipped, give it a few seconds to come back. */
    private fun awaitNetwork(): Network? {
        repeat(20) {
            ImportLink.network?.let { return it }
            try { Thread.sleep(500) } catch (_: InterruptedException) { return null }
        }
        return null
    }

    // ── B3 · retries with Range

    private fun downloadWithRetries(net: Network, c: DashcamImport.Remote, display: String): Boolean {
        var uri: Uri? = null
        var written = 0L
        for (attempt in 1..ATTEMPTS) {
            if (cancelled) break
            try {
                val u = uri ?: insertPending(c, display).also { uri = it }
                written = fetch(ImportLink.network ?: net, c, u, written)
                if (c.size <= 0 || written >= c.size) {
                    contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                    DashcamImport.log("saved $display ${written / 1048576} MB${if (attempt > 1) " (try $attempt)" else ""}")
                    return true
                }
                DashcamImport.log("$display: stream ended at ${written / 1048576} of ${c.size / 1048576} MB (try $attempt)")
            } catch (e: Exception) {
                if (cancelled) break
                DashcamImport.log("$display: try $attempt failed at ${written / 1048576} MB: ${e.javaClass.simpleName}: ${e.message}")
            }
            if (attempt < ATTEMPTS) try { Thread.sleep(2_000L * attempt) } catch (_: InterruptedException) { break }
        }
        uri?.let { runCatching { contentResolver.delete(it, null, null) } }
        if (!cancelled) DashcamImport.log("$display: given up after $ATTEMPTS tries")
        return false
    }

    /** One GET; resumes from [from] with Range when possible. Returns the bytes now in the file. */
    private fun fetch(net: Network, c: DashcamImport.Remote, uri: Uri, from: Long): Long {
        val conn = net.openConnection(URL(c.url)) as HttpURLConnection
        try {
            conn.connectTimeout = 5000; conn.readTimeout = 15000
            ClipMath.rangeHeader(from)?.let { conn.setRequestProperty("Range", it) }
            val code = conn.responseCode
            val mode = ClipMath.resumeMode(code, from)
            if (mode == ClipMath.Resume.FAIL) error("HTTP $code")
            if (from > 0) DashcamImport.log("${c.name.takeLast(30)}: ${if (mode == ClipMath.Resume.APPEND) "resuming at ${from / 1048576} MB" else "server restarted the file"}")
            var total = if (mode == ClipMath.Resume.APPEND) from else 0L
            val out = contentResolver.openOutputStream(uri, if (mode == ClipMath.Resume.APPEND) "wa" else "wt") ?: error("gallery stream")
            out.use { o ->
                conn.inputStream.use { ins ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (cancelled) throw InterruptedException("cancelled")
                        val n = ins.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        total += n
                    }
                }
            }
            return total
        } finally { conn.disconnect() }
    }

    private fun insertPending(c: DashcamImport.Remote, display: String): Uri {
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, display)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RideScreen dashcam")
            put(MediaStore.Video.Media.DATE_TAKEN, c.startMs)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: error("gallery refused the file")
    }

    /** A name the trip video maker already understands (date+time of the START, then the camera). */
    private fun displayName(c: DashcamImport.Remote) =
        "DASHCAM_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(c.startMs)) + "_" + c.angle + ".mp4"

    private fun exists(display: String): Boolean =
        contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DISPLAY_NAME} = ?", arrayOf(display), null)?.use { it.count > 0 } ?: false

    // ── locks and notifications

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RideScreen:dashcam").apply { setReferenceCounted(false); acquire() }
        } catch (e: Exception) { DashcamImport.log("Wi-Fi lock failed: $e") }
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RideScreen:dashcam").apply { setReferenceCounted(false); acquire(3 * 3600_000L) }
        } catch (e: Exception) { DashcamImport.log("wake lock failed: $e") }
        DashcamImport.log("locks held (Wi-Fi high-performance, CPU)")
    }

    private fun finish(why: String) {
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }; wifiLock = null
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }; wakeLock = null
        ImportLink.release(this, why)
        DashcamImport.active = false
        DashcamImport.log("locks released, keyless-box reads and automatic connects back on ($why)")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelled = true
        if (DashcamImport.active || wifiLock != null || wakeLock != null) finish("service destroyed")
        super.onDestroy()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.dashcam_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun progress(n: Int, of: Int, bytes: Long): android.app.Notification {
        val cancel = PendingIntent.getService(this, 31,
            Intent(this, DashcamImportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.dashcam_title))
            .setContentText(if (of == 0) getString(R.string.dashcam_looking) else getString(R.string.dashcam_progress, n, of, (bytes / 1048576).toInt()))
            .setProgress(of, (n - 1).coerceAtLeast(0), of == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(android.R.string.cancel), cancel)
            .build()
    }

    private fun notifyProgress(n: Int, of: Int, bytes: Long) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_PROGRESS, progress(n, of, bytes))
    }

    private fun notifyDone(imported: Int, present: Int, failed: Int) {
        val open = PendingIntent.getActivity(this, 32,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val text = getString(R.string.dashcam_done, imported, present, failed)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.dashcam_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" + getString(R.string.dashcam_done_hint)))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_DONE, n)
    }
}
