package dev.zanderp.opencfmoto

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.net.Network
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TEST build: imports the clips filmed by the Zontes 368G's built-in cameras during a trip into the
 * phone gallery, so the trip video creator finds them like any other clip.
 *
 * Protocol (seen in a PCAPdroid capture of the official app, plain HTTP, no login, dash 192.168.3.1:7686):
 *  - POST {"METHOD":"VIDEO.DATE","previewType":"front"}                      → dateList
 *  - POST {"METHOD":"VIDEO.TIME","date":"2026-11-11","previewType":"front"}  → timeBeanList[].time ("10-11")
 *  - POST {"METHOD":"VIDEO.INFO.LIST","date":…,"index":0,"num":120,"previewType":"front","sort":"des","time":"10-11"}
 *      → videoBeanList[]: name, duration, size, recordTime, playUrl, thumbUrl
 *  - GET  http://<playUrl>  → the MP4 (≈60 MB per 60 s clip, ≈0.7 MB/s)
 * READ-ONLY on the dash: only list requests and GETs. Rear camera previewType is "rear" (confirmed by capture).
 */
object DashcamImport {
    private const val HOST = "192.168.3.1:7686"
    private val ANGLES = listOf("front", "rear")
    private const val MARGIN_MS = 60_000L
    private fun log(s: String) = LogBus.log("[DASHCAM] $s")

    class Remote(val angle: String, val name: String, val url: String, val size: Long, val startMs: Long, val durMs: Long)

    fun showForTrips(activity: Activity) {
        val trips = TripStore.list(activity).filter { !it.archivedByMerge }.sortedByDescending { it.start }.take(12)
        if (trips.isEmpty()) { Toast.makeText(activity, "No trips yet", Toast.LENGTH_SHORT).show(); return }
        val labels = trips.map { TripNames.title(activity, it) + " · " + it.timeRangeText() }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle("Import bike camera clips for…")
            .setItems(labels) { _, i -> start(activity, trips[i]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun start(activity: Activity, trip: Trip) {
        val cancelled = AtomicBoolean(false)
        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle("Bike camera import (test)")
            .setMessage("Looking for clips… Keep the phone on the bike's Wi‑Fi (RideScreen connected, or joined by hand).")
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled.set(true) }
            .setCancelable(false).show()
        val app = activity.applicationContext
        fun say(msg: String) = activity.runOnUiThread { dlg.setMessage(msg) }
        Thread({
            try {
                val r = importTrip(app, trip, cancelled, ::say)
                say(r); log("done: $r")
            } catch (e: Exception) {
                log("failed: ${e.javaClass.simpleName}: ${e.message}")
                say("Failed: ${e.message}")
            }
            activity.runOnUiThread { dlg.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)?.text = "Close"; }
        }, "dashcam-import").start()
    }

    /** Blocking. Returns a one-line summary. */
    fun importTrip(ctx: Context, trip: Trip, cancelled: AtomicBoolean, say: (String) -> Unit): String {
        val net = DvrProbe.pickNetwork(ctx) ?: return "No Wi‑Fi: join the bike's Wi‑Fi first."
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val wantDays = linkedSetOf(dayFmt.format(Date(trip.start - MARGIN_MS)), dayFmt.format(Date(trip.end + MARGIN_MS)))
        log("trip ${dayFmt.format(Date(trip.start))} → looking for days $wantDays")
        val found = ArrayList<Remote>()
        for (angle in ANGLES) {
            if (cancelled.get()) break
            val dates = try { listDates(net, angle) } catch (e: Exception) { log("$angle: DATE failed ${e.javaClass.simpleName}: ${e.message}"); emptyList() }
            log("$angle: dates=$dates")
            for (day in wantDays.filter { it in dates }) {
                val slots = try { listSlots(net, angle, day) } catch (e: Exception) { log("$angle $day: TIME failed ${e.javaClass.simpleName}: ${e.message}"); emptyList() }
                for (slot in slots) {
                    for (c in listClips(net, angle, day, slot)) {
                        val end = c.startMs + c.durMs
                        if (c.startMs <= trip.end + MARGIN_MS && end >= trip.start - MARGIN_MS) found += c
                    }
                }
            }
        }
        if (found.isEmpty()) return "No camera clips found for this trip."
        val total = found.sumOf { it.size }
        log("${found.size} clips overlap the trip, ${total / 1048576} MB")
        var done = 0; var skipped = 0
        for (c in found.sortedBy { it.startMs }) {
            if (cancelled.get()) return "Cancelled: $done of ${found.size} imported."
            val display = displayName(c)
            if (exists(ctx, display)) { skipped++; continue }
            say("Importing ${done + skipped + 1} of ${found.size} (${c.size / 1048576} MB)…")
            download(ctx, net, c, display)
            done++
        }
        return "Imported $done clips" + (if (skipped > 0) " ($skipped already in the gallery)" else "") +
            ". Open the trip's video maker: they are picked up by time."
    }

    private fun post(net: Network, body: JSONObject): JSONObject {
        val c = net.openConnection(URL("http://$HOST/")) as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 4000; c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text)
        } finally { c.disconnect() }
    }

    private fun listDates(net: Network, angle: String): List<String> {
        val j = post(net, JSONObject().put("previewType", angle).put("METHOD", "VIDEO.DATE"))
        if (j.optInt("code", -1) != 0) { log("$angle: DATE answered ${j.toString().take(200)}"); return emptyList() }
        val a = j.optJSONArray("dateList") ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    private fun listSlots(net: Network, angle: String, day: String): List<String> {
        val j = post(net, JSONObject().put("METHOD", "VIDEO.TIME").put("date", day).put("previewType", angle))
        val a = j.optJSONArray("timeBeanList") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.getJSONObject(it).optString("time").ifEmpty { null } }
    }

    private fun listClips(net: Network, angle: String, day: String, slot: String): List<Remote> {
        val j = post(net, JSONObject().put("date", day).put("index", 0).put("num", 120)
            .put("previewType", angle).put("sort", "des").put("time", slot).put("METHOD", "VIDEO.INFO.LIST"))
        val a = j.optJSONArray("videoBeanList") ?: return emptyList()
        val out = ArrayList<Remote>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val name = o.optString("name")
            val start = startFromName(name) ?: continue
            val url = o.optString("playUrl").let { if (it.startsWith("http")) it else "http://$it" }
            out += Remote(angle, name, url, o.optLong("size"), start, o.optLong("duration", 60) * 1000L)
        }
        return out
    }

    /** "1794398380-2026-11-11-11-59-40-front.mp4" → local start time. */
    private fun startFromName(name: String): Long? {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})-(\d{2})-(\d{2})-(\d{2})""").find(name) ?: return null
        return try { SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(m.groupValues.drop(1).joinToString(""))?.time } catch (_: Exception) { null }
    }

    /** A name the trip video maker already understands (date+time of the START, then the angle). */
    private fun displayName(c: Remote) =
        "DASHCAM_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(c.startMs)) + "_" + c.angle + ".mp4"

    private fun exists(ctx: Context, display: String): Boolean =
        ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID),
            "${MediaStore.Video.Media.DISPLAY_NAME} = ?", arrayOf(display), null)?.use { it.count > 0 } ?: false

    private fun download(ctx: Context, net: Network, c: Remote, display: String) {
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, display)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/RideScreen dashcam")
            put(MediaStore.Video.Media.DATE_TAKEN, c.startMs)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: error("gallery refused the file")
        val conn = net.openConnection(URL(c.url)) as HttpURLConnection
        try {
            conn.connectTimeout = 4000; conn.readTimeout = 15000
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            var n = 0L
            conn.inputStream.use { ins -> ctx.contentResolver.openOutputStream(uri)!!.use { out -> n = ins.copyTo(out, 256 * 1024) } }
            ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            log("saved $display ${n / 1048576} MB")
        } catch (e: Exception) {
            ctx.contentResolver.delete(uri, null, null)
            throw e
        } finally { conn.disconnect() }
    }
}
