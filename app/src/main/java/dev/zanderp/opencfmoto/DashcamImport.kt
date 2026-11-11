package dev.zanderp.opencfmoto

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Imports the clips filmed by the Zontes 368G's built-in cameras during a trip into the phone gallery,
 * so the trip video maker finds them like any other clip ([DashcamImportService] does the download).
 *
 * Protocol (PCAPdroid capture of the official app, plain HTTP, no login, dash 192.168.3.1:7686):
 *  - POST {"METHOD":"VIDEO.DATE","previewType":"front"|"rear"}                → dateList
 *  - POST {"METHOD":"VIDEO.TIME","date":"2026-11-11","previewType":…}         → timeBeanList[].time ("10-11")
 *  - POST {"METHOD":"VIDEO.INFO.LIST","date":…,"index":0,"num":120,"previewType":…,"sort":"des","time":"10-11"}
 *      → videoBeanList[]: name, duration, size, recordTime, playUrl, thumbUrl
 *  - GET  http://<playUrl> (Range supported) → the MP4 (≈60 MB per 60 s clip)
 * READ-ONLY on the dash: only these list requests and GETs.
 *
 * B1 · never while projecting: the bike's Wi-Fi can't carry the clips and the video at once (11 Nov log:
 * three aborts, two took the whole link down). The rider is offered to stop the projection; then the
 * app joins the bike's network itself ([ImportLink], same request as Connect, no Android Auto) and
 * releases it when the import ends.
 */
object DashcamImport {
    private const val HOST = "192.168.3.1:7686"
    private const val MARGIN_MS = 60_000L
    internal fun log(s: String) = LogBus.log("[DASHCAM] $s")

    /** True while [DashcamImportService] runs: keyless-box reads and automatic connects stay off (B4). */
    @Volatile var active = false

    /** Import running, or the bike's Wi-Fi held for listing clips: box reads and automatic connects wait. */
    fun busy(): Boolean = active || ImportLink.network != null

    class Remote(
        val angle: String, val name: String, val url: String, val thumbUrl: String?,
        val size: Long, val startMs: Long, val durMs: Long,
    ) {
        fun toJson(): JSONObject = JSONObject().put("angle", angle).put("name", name).put("url", url)
            .put("thumb", thumbUrl ?: "").put("size", size).put("start", startMs).put("dur", durMs)
        companion object {
            fun fromJson(o: JSONObject) = Remote(
                o.getString("angle"), o.getString("name"), o.getString("url"),
                o.optString("thumb").ifEmpty { null }, o.getLong("size"), o.getLong("start"), o.getLong("dur"),
            )
        }
    }

    private fun projecting(): Boolean = ConnectionState.phase.let {
        it == Phase.STREAMING || it == Phase.MIRRORING || it.busy
    }

    /** Help entry. [stopProjection] is the same Stop as the main button (no automatic reconnect). */
    fun showForTrips(activity: Activity, stopProjection: () -> Unit) {
        if (active) { Toast.makeText(activity, R.string.dashcam_already_running, Toast.LENGTH_SHORT).show(); return }
        if (projecting()) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dashcam_stop_first_title)
                .setMessage(R.string.dashcam_stop_first_msg)
                .setPositiveButton(R.string.dashcam_stop_and_continue) { _, _ ->
                    log("projection stopped by the rider to import clips")
                    stopProjection()
                    pickTrip(activity)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        pickTrip(activity)
    }

    private fun pickTrip(activity: Activity) {
        val trips = TripStore.list(activity).filter { !it.archivedByMerge }.sortedByDescending { it.start }.take(12)
        if (trips.isEmpty()) { Toast.makeText(activity, R.string.dashcam_no_trips, Toast.LENGTH_SHORT).show(); return }
        val labels = trips.map { TripNames.title(activity, it) + " · " + it.timeRangeText() }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dashcam_pick_trip)
            .setItems(labels) { _, i -> list(activity, trips[i]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Joins the bike's Wi-Fi, lists the trip's clips on both cameras, then shows the picker (B5). */
    private fun list(activity: Activity, trip: Trip) {
        val dlg = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dashcam_title)
            .setMessage(R.string.dashcam_joining)
            .setNegativeButton(android.R.string.cancel) { _, _ -> ImportLink.release(activity, "cancelled") }
            .setCancelable(false).show()
        ImportLink.open(activity, onReady = { net ->
            activity.runOnUiThread { dlg.setMessage(activity.getString(R.string.dashcam_looking)) }
            Thread({
                val found = try { clipsFor(net, trip) } catch (e: Exception) { log("list failed: ${e.javaClass.simpleName}: ${e.message}"); null }
                activity.runOnUiThread {
                    if (!dlg.isShowing) return@runOnUiThread
                    dlg.dismiss()
                    when {
                        found == null -> { ImportLink.release(activity, "list failed"); toast(activity, R.string.dashcam_join_failed) }
                        found.isEmpty() -> { ImportLink.release(activity, "no clips"); toast(activity, R.string.dashcam_none) }
                        else -> pick(activity, net, found.sortedBy { it.startMs })
                    }
                }
            }, "dashcam-list").start()
        }, onFail = {
            activity.runOnUiThread {
                if (dlg.isShowing) dlg.dismiss()
                toast(activity, R.string.dashcam_join_failed)
            }
        })
    }

    private fun toast(a: Activity, res: Int) = Toast.makeText(a, res, Toast.LENGTH_LONG).show()

    /** Blocking: every clip of both cameras that overlaps the trip (±1 min). */
    internal fun clipsFor(net: Network, trip: Trip): List<Remote> {
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val wantDays = linkedSetOf(dayFmt.format(Date(trip.start - MARGIN_MS)), dayFmt.format(Date(trip.end + MARGIN_MS)))
        log("trip ${dayFmt.format(Date(trip.start))} → looking for days $wantDays")
        val found = ArrayList<Remote>()
        for (angle in listOf("front", "rear")) {
            val dates = listDates(net, angle)
            log("$angle: dates=$dates")
            for (day in wantDays.filter { it in dates }) {
                for (slot in listSlots(net, angle, day)) {
                    for (c in listClips(net, angle, day, slot)) {
                        if (ClipMath.overlaps(c.startMs, c.durMs, trip.start, trip.end, MARGIN_MS)) found += c
                    }
                }
            }
        }
        log("${found.size} clips overlap the trip (${found.sumOf { it.size } / 1048576} MB)")
        return found
    }

    // ── B5 · choose camera and clips

    private fun pick(activity: Activity, net: Network, all: List<Remote>) {
        val dp = activity.resources.displayMetrics.density
        val checked = HashSet<Remote>(all)
        var camera = "both"
        val thumbs = HashMap<String, Bitmap?>()
        val pool = Executors.newFixedThreadPool(2)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0) }
        val toggle = MaterialButtonToggleGroup(activity).apply { isSingleSelection = true; isSelectionRequired = true }
        fun seg(id: Int, text: Int) = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.id = id; setText(text)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val idFront = View.generateViewId(); val idRear = View.generateViewId(); val idBoth = View.generateViewId()
        toggle.addView(seg(idFront, R.string.dashcam_camera_front)); toggle.addView(seg(idRear, R.string.dashcam_camera_rear)); toggle.addView(seg(idBoth, R.string.dashcam_camera_both))
        toggle.check(idBoth)
        root.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val total = TextView(activity).apply { setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt()) }
        root.addView(total)
        val listView = ListView(activity)
        root.addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (360 * dp).toInt()))

        fun visible() = all.filter { camera == "both" || it.angle == camera }
        val adapter = object : BaseAdapter() {
            override fun getCount() = visible().size
            override fun getItem(p: Int) = visible()[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val c = visible()[p]
                val row = (convert as? LinearLayout) ?: LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                    addView(ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP },
                        LinearLayout.LayoutParams((96 * dp).toInt(), (54 * dp).toInt()))
                    addView(TextView(activity).apply { setPadding((12 * dp).toInt(), 0, 0, 0) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(CheckBox(activity).apply { isClickable = false; isFocusable = false })
                }
                val img = row.getChildAt(0) as ImageView
                val label = row.getChildAt(1) as TextView
                val box = row.getChildAt(2) as CheckBox
                val cam = activity.getString(if (c.angle == "rear") R.string.dashcam_camera_rear else R.string.dashcam_camera_front)
                label.text = "${timeFmt.format(Date(c.startMs))} · $cam · ${c.size / 1048576} MB"
                box.isChecked = c in checked
                img.tag = c.name
                val cached = thumbs[c.name]
                img.setImageBitmap(cached)
                if (cached == null && !thumbs.containsKey(c.name) && c.thumbUrl != null) {
                    thumbs[c.name] = null
                    pool.execute {
                        val bmp = try { fetchThumb(net, c.thumbUrl) } catch (_: Exception) { null }
                        activity.runOnUiThread { thumbs[c.name] = bmp; if (img.tag == c.name) img.setImageBitmap(bmp) }
                    }
                }
                return row
            }
        }
        listView.adapter = adapter

        lateinit var dialog: androidx.appcompat.app.AlertDialog
        fun refresh() {
            val chosen = visible().filter { it in checked }
            val mb = chosen.sumOf { it.size } / 1048576
            total.text = activity.getString(R.string.dashcam_total, chosen.size, mb)
            adapter.notifyDataSetChanged()
            if (::dialog.isInitialized) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.isEnabled = chosen.isNotEmpty()
        }
        listView.setOnItemClickListener { _, _, p, _ ->
            val c = visible()[p]
            if (!checked.remove(c)) checked += c
            refresh()
        }
        toggle.addOnButtonCheckedListener { _, id, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            camera = when (id) { idFront -> "front"; idRear -> "rear"; else -> "both" }
            refresh()
        }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dashcam_title)
            .setView(root)
            .setPositiveButton(R.string.dashcam_import, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> ImportLink.release(activity, "cancelled at the clip list") }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            refresh()
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val chosen = visible().filter { it in checked }
                val need = chosen.sumOf { it.size }
                val free = freeBytes()
                if (!ClipMath.fits(need, free)) {
                    Toast.makeText(activity, activity.getString(R.string.dashcam_no_space, need / 1048576, free / 1048576), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                pool.shutdownNow()
                log("rider chose ${chosen.size} clips (${need / 1048576} MB, camera $camera) · free ${free / 1048576} MB")
                DashcamImportService.start(activity, chosen)
            }
        }
        dialog.setOnDismissListener { pool.shutdownNow() }
        dialog.show()
    }

    private fun freeBytes(): Long = try {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    } catch (_: Exception) { Long.MAX_VALUE }

    // ── dash HTTP (read only)

    private fun post(net: Network, body: JSONObject): JSONObject {
        val c = net.openConnection(URL("http://$HOST/")) as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 4000; c.readTimeout = 8000
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
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
            val start = ClipMath.startFromName(name) ?: continue
            out += Remote(angle, name, http(o.optString("playUrl")), o.optString("thumbUrl").ifEmpty { null }?.let(::http),
                o.optLong("size"), start, o.optLong("duration", 60) * 1000L)
        }
        return out
    }

    private fun http(u: String) = if (u.startsWith("http")) u else "http://$u"

    private fun fetchThumb(net: Network, url: String): Bitmap? {
        val c = net.openConnection(URL(url)) as HttpURLConnection
        try {
            c.connectTimeout = 4000; c.readTimeout = 8000
            val bytes = c.inputStream.use { it.readBytes() }
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } finally { c.disconnect() }
    }

    /** For the service: one clip list round-trip as JSON (intent extra). */
    internal fun encode(list: List<Remote>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    internal fun decode(s: String?): List<Remote> {
        val a = JSONArray(s ?: return emptyList())
        return (0 until a.length()).map { Remote.fromJson(a.getJSONObject(it)) }
    }
}

/**
 * B1 · the app's own hold on the bike's Wi-Fi for an import: the same request Connect makes (saved SSID
 * and password, no internet capability, approved once, so no picker) but without Android Auto and
 * without [BikeWifi]'s reconnect logic. Released when the import ends or is cancelled.
 */
object ImportLink {
    @Volatile var network: Network? = null
        private set
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun open(ctx: Context, onReady: (Network) -> Unit, onFail: () -> Unit) {
        val app = ctx.applicationContext
        network?.let { onReady(it); return }
        val qr = BikeMemory.lastQr(app)
        if (qr == null || qr.ssid.isBlank()) { DashcamImport.log("no saved bike"); onFail(); return }
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: run { onFail(); return }
        val spec = WifiNetworkSpecifier.Builder().setSsid(qr.ssid).apply { if (qr.pwd.isNotEmpty()) setWpa2Passphrase(qr.pwd) }.build()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        var delivered = false
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                network = n
                if (!delivered) { delivered = true; DashcamImport.log("joined the bike's Wi-Fi for the import"); onReady(n) }
            }
            override fun onLost(n: Network) {
                if (network == n) network = null
                DashcamImport.log("the bike's Wi-Fi was lost during the import")
            }
            override fun onUnavailable() {
                DashcamImport.log("the bike's Wi-Fi didn't appear (bike off or out of range?)")
                callback = null
                if (!delivered) onFail()
            }
        }
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = cb
        try {
            cm.requestNetwork(req, cb, 30_000)
            DashcamImport.log("asking Android for the bike's Wi-Fi (no Android Auto)")
        } catch (e: Exception) {
            callback = null
            DashcamImport.log("Wi-Fi request failed: ${e.javaClass.simpleName}")
            onFail()
        }
    }

    fun release(ctx: Context, why: String) {
        val cb = callback ?: return
        callback = null
        network = null
        runCatching { ctx.applicationContext.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        DashcamImport.log("released the bike's Wi-Fi ($why)")
    }
}

/** Pure helpers (unit tested). */
internal object ClipMath {
    fun overlaps(startMs: Long, durMs: Long, tripStart: Long, tripEnd: Long, marginMs: Long): Boolean =
        startMs <= tripEnd + marginMs && startMs + durMs >= tripStart - marginMs

    /** "1794398380-2026-11-11-11-59-40-front.mp4" → local start time. */
    fun startFromName(name: String): Long? {
        val m = Regex("""(\d{4})-(\d{2})-(\d{2})-(\d{2})-(\d{2})-(\d{2})""").find(name) ?: return null
        return try { SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(m.groupValues.drop(1).joinToString(""))?.time } catch (_: Exception) { null }
    }

    /** Keep 200 MB spare so the phone isn't left completely full. */
    const val SPARE_BYTES = 200L * 1024 * 1024
    fun fits(needBytes: Long, freeBytes: Long): Boolean = needBytes + SPARE_BYTES <= freeBytes

    /** B3 · Range header to resume after [written] bytes (null: start from zero). */
    fun rangeHeader(written: Long): String? = if (written > 0) "bytes=$written-" else null

    enum class Resume { APPEND, RESTART, FAIL }
    /** What to do with the partial file given the server's answer to a (possibly ranged) GET. */
    fun resumeMode(httpCode: Int, written: Long): Resume = when {
        httpCode == 206 && written > 0 -> Resume.APPEND
        httpCode == 200 -> Resume.RESTART
        else -> Resume.FAIL
    }
}
