// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/** v2.3 · The look of a trip video: overlay style + kind of map. */
enum class VideoStyle(val labelRes: Int) {
    CLASSIC(R.string.tvs_classic), SPORT(R.string.tvs_sport), MINIMAL(R.string.tvs_minimal),
    CINEMA(R.string.tvs_cinema), RETRO(R.string.tvs_retro), TELEMETRY(R.string.tvs_telemetry),
    /** v2.4: dials from the trip's OBD data (RPM, speed, throttle, load, coolant) — English only until 2.5. */
    GAUGES(R.string.tvs_gauges),
    /** v2.5: big numbers straight on the picture (lean, G, heart rate, OBD); English only for now. */
    HUD(R.string.tvs_hud),
}

fun nightStyle(s: VideoStyle) = s == VideoStyle.SPORT || s == VideoStyle.TELEMETRY || s == VideoStyle.RETRO ||
    s == VideoStyle.CINEMA || s == VideoStyle.GAUGES || s == VideoStyle.HUD

enum class VideoMap(val labelRes: Int) { LINE(R.string.tvm_line), SPEED(R.string.tvm_speed), STREETS(R.string.tvm_streets) }

/**
 * Real streets for the mini-map, rendered by the app's own map engine (MapLibre + OpenFreeMap, the
 * same as the dash map) into 1024 px blocks along the route, once, and cached on disk. Pixel space
 * is classic Web Mercator with 256 px tiles at [zoom] (MapLibre zoom = zoom − 1 for 512 px tiles).
 */
class StreetTiles private constructor(private val dir: File, val zoom: Int) {
    private val cache = object : LruCache<String, Bitmap>(8) {}

    fun worldPx(lat: Double, lon: Double): PointF {
        val n = 256.0 * (1 shl zoom)
        val x = (lon + 180.0) / 360.0 * n
        val r = Math.toRadians(lat)
        val y = (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n
        return PointF(x.toFloat(), y.toFloat())
    }

    fun metersPerPx(lat: Double) = 156_543.03 * cos(Math.toRadians(lat)) / (1 shl zoom)

    fun block(bx: Int, by: Int): Bitmap? {
        val key = "$bx/$by"
        cache.get(key)?.let { return it }
        val f = File(dir, "${bx}_$by.png")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)?.also { cache.put(key, it) }
    }

    companion object {
        const val WINDOW_M = 260.0            // map shown = this many metres around you (radius)
        const val BLOCK = 1024
        private const val MAX_BLOCKS = 60

        private fun latLonOf(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
            val n = 256.0 * (1 shl zoom)
            val lon = x / n * 360.0 - 180.0
            val lat = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(PI * (1 - 2 * y / n))))
            return lat to lon
        }

        /** Render what the route needs (blocking; call off the main thread). Null if it can't. */
        fun prepare(ctx: Context, trip: Trip, night: Boolean, cancelled: () -> Boolean = { false },
                    progress: (Int, Int) -> Unit): StreetTiles? {
            val style = if (night) MapLibreDashController.STYLE_NIGHT else MapLibreDashController.STYLE_DAY
            File(ctx.cacheDir, "osm-tiles").deleteRecursively()   // the old raster cache (got "access blocked" tiles)
            for (z in intArrayOf(16, 15, 14)) {
                if (cancelled()) return null
                val dir = File(ctx.cacheDir, "video-streets/${if (night) "n" else "d"}$z").apply { mkdirs() }
                val t = StreetTiles(dir, z)
                val need = LinkedHashSet<Long>()
                val step = max(1, trip.points.size / 600)
                for (i in trip.points.indices step step) {
                    val p = trip.points[i]
                    val c = t.worldPx(p.lat, p.lon)
                    val r = (WINDOW_M * 1.5 / t.metersPerPx(p.lat)).toFloat()
                    for (bx in floor((c.x - r) / BLOCK).toInt()..floor((c.x + r) / BLOCK).toInt())
                        for (by in floor((c.y - r) / BLOCK).toInt()..floor((c.y + r) / BLOCK).toInt())
                            need += (bx.toLong() shl 32) or (by.toLong() and 0xffffffffL)
                }
                if (need.size > MAX_BLOCKS) continue
                var done = 0; var failed = 0
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                for (k in need) {
                    if (cancelled()) return null
                    val bx = (k shr 32).toInt(); val by = k.toInt()
                    val f = File(dir, "${bx}_$by.png")
                    if (!f.exists()) {
                        val (lat, lon) = latLonOf((bx + 0.5) * BLOCK, (by + 0.5) * BLOCK, z)
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var bmp: Bitmap? = null
                        var snap: org.maplibre.android.snapshotter.MapSnapshotter? = null
                        main.post {
                            if (cancelled()) { latch.countDown(); return@post }
                            try {
                                snap = org.maplibre.android.snapshotter.MapSnapshotter(ctx.applicationContext,
                                    org.maplibre.android.snapshotter.MapSnapshotter.Options(BLOCK, BLOCK)
                                        .withStyleBuilder(org.maplibre.android.maps.Style.Builder().fromUri(style))
                                        .withCameraPosition(org.maplibre.android.camera.CameraPosition.Builder()
                                            .target(org.maplibre.android.geometry.LatLng(lat, lon)).zoom(z - 1.0).build())
                                        .withPixelRatio(1f)
                                        .withLogo(false))
                                snap?.start({ s -> bmp = s.bitmap.copy(Bitmap.Config.ARGB_8888, false); latch.countDown() },
                                    { e -> LogBus.log("[VIDEO] street block failed: $e"); latch.countDown() })
                            } catch (e: Exception) { LogBus.log("[VIDEO] snapshotter: $e"); latch.countDown() }
                        }
                        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
                        while (!latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                            if (cancelled()) {
                                main.post { try { snap?.cancel() } catch (_: Exception) {} }
                                return null
                            }
                            if (System.nanoTime() >= deadline) break
                        }
                        main.post { try { snap?.cancel() } catch (_: Exception) {} }
                        if (cancelled()) return null
                        val b = bmp
                        if (b != null) { f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
                        else if (++failed > 3 && failed > need.size / 3) { LogBus.log("[VIDEO] street map unavailable"); return null }
                    }
                    progress(++done, need.size)
                }
                LogBus.log("[VIDEO] street map z$z: ${need.size} blocks ($failed failed)")
                return t
            }
            return null
        }
    }
}

/** v2.3: the overlay pieces the rider can drag in the preview; the export draws them where they were left. */
enum class OverlayPart { TITLE, SPEED, MAP, EXTRA, THROTTLE, DISTANCE, MOTION, READOUT_L, READOUT_R }

/** Offset of a piece from its home spot, as fractions of the frame width and height. */
data class PartOffset(val dx: Float, val dy: Float)

/** Per-look piece offsets; stored per style as "MAP:0.1,-0.2;SPEED:…". */
data class OverlayLayout(val offsets: Map<OverlayPart, PartOffset> = emptyMap()) {
    fun with(part: OverlayPart, dx: Float, dy: Float) = OverlayLayout(offsets + (part to PartOffset(dx, dy)))
    val isDefault get() = offsets.values.all { it.dx == 0f && it.dy == 0f }
    fun encode() = offsets.entries.filter { it.value.dx != 0f || it.value.dy != 0f }
        .joinToString(";") { "${it.key.name}:${it.value.dx},${it.value.dy}" }

    companion object {
        fun decode(s: String?): OverlayLayout = OverlayLayout(s.orEmpty().split(';').mapNotNull { e ->
            val kv = e.split(':')
            if (kv.size != 2) return@mapNotNull null
            val part = runCatching { OverlayPart.valueOf(kv[0]) }.getOrNull() ?: return@mapNotNull null
            val xy = kv[1].split(',').mapNotNull { it.toFloatOrNull() }
            if (xy.size == 2) part to PartOffset(xy[0], xy[1]) else null
        }.toMap())
    }
}

/**
 * Draws one look on a canvas: the per-frame overlay ([drawFrame]), the opening card ([drawIntro])
 * and the closing card ([drawOutro]). Shared by the video export and the in-app preview.
 */
class TripLook(
    private val ctx: Context,
    private val trip: Trip,
    private val title: String,
    val style: VideoStyle,
    val map: VideoMap,
    private val streets: StreetTiles?,
    /** v2.3: where the rider dragged each piece in the preview. */
    var layout: OverlayLayout = OverlayLayout(),
    /** The data the rider wants in the picture (default: everything). */
    var fields: Set<VideoField> = VideoFields.ALL,
) {
    private fun on(f: VideoField) = f in fields
    /** True when the lean/G/heart-rate block has something to show. */
    private val motionOn get() = hasMotion && (on(VideoField.LEAN) || on(VideoField.G_FORCE) || on(VideoField.HEART_RATE))
    private fun maskMotion(m: MotionTripSample?): MotionTripSample? = m?.copy(
        leanDeg = m.leanDeg.takeIf { on(VideoField.LEAN) },
        gLat = m.gLat.takeIf { on(VideoField.G_FORCE) }, gLon = m.gLon.takeIf { on(VideoField.G_FORCE) },
        bpm = m.bpm.takeIf { on(VideoField.HEART_RATE) })

    private val pts = trip.points
    private val minLat = pts.minOf { it.lat }; private val maxLat = pts.maxOf { it.lat }
    private val minLon = pts.minOf { it.lon }; private val maxLon = pts.maxOf { it.lon }
    private val cosLat = cos(Math.toRadians((minLat + maxLat) / 2))
    private val cumKm: DoubleArray = DoubleArray(pts.size).also { a ->
        for (i in 1 until pts.size) a[i] = a[i - 1] + metres(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon) / 1000.0
    }
    private val totalKm = max(cumKm.lastOrNull() ?: 0.0, trip.distanceKm)
    private val maxKmh = max(20f, pts.maxOf { it.speedMs } * 3.6f)
    private val speedSeries: FloatArray = run {
        val n = 160
        FloatArray(n) { i -> pts[(i.toLong() * (pts.size - 1) / (n - 1)).toInt()].speedMs * 3.6f }
    }

    private val amber = Color.rgb(255, 176, 32)
    private val gold = Color.rgb(214, 180, 110)
    private val sky = Color.rgb(110, 190, 255)
    private val yellow = Color.rgb(255, 193, 7)
    private val red = Color.rgb(229, 57, 53)

    private val tfCondItalic = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    private val tfCond = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    // Weight 300 from the default sans: some phones resolve "sans-serif-light" to the very same
    // Typeface object as monospace bold, which sent Minimal/Cinema text through the pixel font.
    private val tfLight = Typeface.create(Typeface.SANS_SERIF, 300, false)
    private val tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfSerifItalic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    private val tfMono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    /** Dot-matrix letters: only the Retro look, never by font identity alone. */
    private fun pixel(tf: Typeface) = style == VideoStyle.RETRO && tf === tfMono

    /** Width as [text] will draw it (pixel font included). */
    private fun measure(s: String, tf: Typeface, size: Float): Float {
        if (pixel(tf)) return PixelFont.width(s, size)
        p.reset(); p.isAntiAlias = true; p.typeface = tf; p.textSize = size
        return p.measureText(s)
    }
    private val tfArchivo = try { ResourcesCompat.getFont(ctx, R.font.archivo_semibold) } catch (_: Exception) { null } ?: Typeface.DEFAULT_BOLD
    private val tfArchivoBlack = try { ResourcesCompat.getFont(ctx, R.font.archivo_black) } catch (_: Exception) { null } ?: Typeface.DEFAULT_BOLD

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    /** Dark map tiles for the dark looks. */
    fun night() = nightStyle(style)

    // ── where we are

    private fun indexAt(tMs: Long): Int {
        var i = pts.binarySearchBy(tMs) { it.time }
        if (i < 0) i = (-i - 1).coerceIn(0, pts.lastIndex)
        return i
    }

    private fun bearing(i: Int): Float {
        val a = pts[(i - 4).coerceAtLeast(0)]; val b = pts[(i + 4).coerceAtMost(pts.lastIndex)]
        if (metres(a.lat, a.lon, b.lat, b.lon) < 8) return lastBearing
        val y = (b.lon - a.lon) * cosLat; val x = b.lat - a.lat
        lastBearing = Math.toDegrees(atan2(y, x)).toFloat()
        return lastBearing
    }
    private var lastBearing = 0f

    // ── text helpers

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, tf: Typeface, color: Int = Color.WHITE,
                      align: Paint.Align = Paint.Align.LEFT, shadow: Boolean = true) {
        if (pixel(tf)) { PixelFont.draw(c, s, x, y, size, color, align, shadow); return }
        p.reset(); p.isAntiAlias = true
        p.typeface = tf; p.textSize = size; p.color = color; p.textAlign = align
        if (shadow) p.setShadowLayer(size * 0.12f, 0f, size * 0.04f, Color.argb(170, 0, 0, 0))
        c.drawText(s, x, y, p)
    }

    private fun fill(color: Int) = p.apply { reset(); isAntiAlias = true; this.color = color; style = Paint.Style.FILL }

    private fun km(v: Double) = String.format(Locale.getDefault(), "%.1f", v)
    private val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())

    // ── map

    /** Route fitted north-up into [box] (line or speed-coloured); the ridden part in [done]. */
    private fun routeFit(c: Canvas, box: RectF, color: Int, done: Int?, width: Float, idx: Int, dotR: Float) {
        val spanLat = (maxLat - minLat).coerceAtLeast(1e-4)
        val spanLon = (maxLon - minLon).coerceAtLeast(1e-4) * cosLat
        val s = min(box.width() / spanLon, box.height() / spanLat).toFloat()
        fun x(lon: Double) = box.centerX() + ((lon - (minLon + maxLon) / 2) * cosLat * s).toFloat()
        fun y(lat: Double) = box.centerY() - ((lat - (minLat + maxLat) / 2) * s).toFloat()
        val step = max(1, pts.size / 500)
        stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND; stroke.strokeWidth = width
        if (map == VideoMap.SPEED) {
            var i = 0
            while (i + step < pts.size) {
                val a = pts[i]; val b = pts[i + step]
                stroke.color = speedColor(b.speedMs * 3.6f, if (i <= idx) 255 else 110)
                c.drawLine(x(a.lon), y(a.lat), x(b.lon), y(b.lat), stroke)
                i += step
            }
        } else {
            val all = Path(); val ridden = Path()
            for (i in pts.indices step step) {
                val q = pts[i]
                if (i == 0) { all.moveTo(x(q.lon), y(q.lat)); ridden.moveTo(x(q.lon), y(q.lat)) }
                else { all.lineTo(x(q.lon), y(q.lat)); if (i <= idx) ridden.lineTo(x(q.lon), y(q.lat)) }
            }
            stroke.color = color; c.drawPath(all, stroke)
            if (done != null) { stroke.color = done; c.drawPath(ridden, stroke) }
        }
        if (idx >= 0 && dotR > 0f) dot(c, x(pts[idx].lon), y(pts[idx].lat), dotR)
    }

    private fun dot(c: Canvas, x: Float, y: Float, r: Float) {
        c.drawCircle(x, y, r * 1.35f, fill(Color.WHITE))
        c.drawCircle(x, y, r, fill(red))
    }

    private fun speedColor(kmh: Float, alpha: Int): Int {
        val t = (kmh / maxKmh).coerceIn(0f, 1f)
        val hsv = floatArrayOf(220f - 220f * t, 0.85f, 1f)
        return Color.HSVToColor(alpha, hsv)
    }

    /** Real streets around the current position, heading up, clipped to [box] (circle or rounded). */
    private fun streetsAround(c: Canvas, box: RectF, circle: Boolean, idx: Int, routeColor: Int, dotR: Float) {
        val st = streets ?: return
        val here = pts[idx]
        val centre = st.worldPx(here.lat, here.lon)
        val radiusPx = (StreetTiles.WINDOW_M / st.metersPerPx(here.lat)).toFloat()
        val scale = (min(box.width(), box.height()) / 2f) / radiusPx
        c.save()
        val clip = Path().apply {
            if (circle) addCircle(box.centerX(), box.centerY(), min(box.width(), box.height()) / 2f, Path.Direction.CW)
            else addRoundRect(box, box.width() * 0.08f, box.width() * 0.08f, Path.Direction.CW)
        }
        c.clipPath(clip)
        c.drawColor(if (night()) Color.rgb(30, 34, 38) else Color.rgb(230, 228, 222))
        c.translate(box.centerX(), box.centerY())
        c.rotate(-bearing(idx))
        c.scale(scale, scale)
        c.translate(-centre.x, -centre.y)
        val reach = radiusPx * 1.5f
        val bp = Paint(Paint.FILTER_BITMAP_FLAG)
        val B = StreetTiles.BLOCK
        for (bx in floor((centre.x - reach) / B).toInt()..floor((centre.x + reach) / B).toInt())
            for (by in floor((centre.y - reach) / B).toInt()..floor((centre.y + reach) / B).toInt()) {
                st.block(bx, by)?.let { c.drawBitmap(it, bx * B.toFloat(), by * B.toFloat(), bp) }
            }
        // The route on top of the streets.
        val path = Path()
        val step = max(1, pts.size / 800)
        for (i in pts.indices step step) {
            val w = st.worldPx(pts[i].lat, pts[i].lon)
            if (i == 0) path.moveTo(w.x, w.y) else path.lineTo(w.x, w.y)
        }
        stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeWidth = dotR * 0.9f / scale; stroke.color = routeColor
        c.drawPath(path, stroke)
        c.restore()
        // Slight shade so white text around stays readable, then "you are here" and attribution.
        dot(c, box.centerX(), box.centerY(), dotR)
        text(c, "© OpenStreetMap", box.right - box.width() * 0.06f, box.bottom - box.width() * 0.05f, box.width() * 0.055f,
            Typeface.DEFAULT, if (night()) Color.argb(200, 220, 220, 220) else Color.argb(200, 40, 40, 40), Paint.Align.RIGHT, shadow = false)
        if (circle) {
            stroke.strokeWidth = box.width() * 0.025f; stroke.color = Color.WHITE
            c.drawCircle(box.centerX(), box.centerY(), min(box.width(), box.height()) / 2f, stroke)
        }
    }

    /** The map in this style's slot: streets if chosen and available, else the fitted route. */
    private fun minimap(c: Canvas, box: RectF, circle: Boolean, panel: Int?, border: Int?, line: Int, done: Int?, idx: Int, u: Float) {
        if (map == VideoMap.STREETS && streets != null) {
            streetsAround(c, box, circle, idx, done ?: line, 1.5f * u)
            if (border != null) {
                stroke.strokeWidth = 0.6f * u; stroke.color = border
                if (circle) c.drawCircle(box.centerX(), box.centerY(), box.width() / 2f, stroke)
                else c.drawRoundRect(box, box.width() * 0.08f, box.width() * 0.08f, stroke)
            }
            return
        }
        if (panel != null) {
            if (circle) c.drawCircle(box.centerX(), box.centerY(), box.width() / 2f, fill(panel))
            else c.drawRoundRect(box, 2 * u, 2 * u, fill(panel))
        }
        if (border != null) {
            stroke.style = Paint.Style.STROKE; stroke.strokeWidth = 0.6f * u; stroke.color = border
            if (circle) c.drawCircle(box.centerX(), box.centerY(), box.width() / 2f, stroke)
            else c.drawRoundRect(box, 2 * u, 2 * u, stroke)
        }
        val inset = box.width() * (if (circle) 0.2f else 0.1f)
        routeFit(c, RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), line, done, 0.7f * u, idx, 1.3f * u)
    }

    // ── per-frame overlay

    fun drawFrame(c: Canvas, w: Int, h: Int, tMs: Long) {
        val u = min(w, h) / 100f
        val idx = indexAt(tMs)
        val pt = pts[idx]
        val kmh = (pt.speedMs * 3.6f).roundToInt().coerceAtLeast(0)
        val clock = hhmm.format(Date(tMs))
        val kmDone = cumKm[idx]
        placed.clear()
        val mo = if (hasMotion) trip.motionAt(tMs) else null
        when (style) {
            VideoStyle.CLASSIC -> {
                val ts = fitSize(title, Typeface.DEFAULT_BOLD, w - 40 * u, 4.2f * u)
                part(c, OverlayPart.TITLE, w, h, RectF(2 * u, 2 * u, 4 * u + max(measure(title, Typeface.DEFAULT_BOLD, ts), measure(clock, Typeface.DEFAULT, 3.4f * u)), 13 * u)) {
                    text(c, title, 3 * u, 7 * u, ts, Typeface.DEFAULT_BOLD)
                    text(c, clock, 3 * u, 12 * u, 3.4f * u, Typeface.DEFAULT)
                }
                val kw = measure("$kmh", Typeface.DEFAULT_BOLD, 12 * u)
                if (on(VideoField.SPEED)) part(c, OverlayPart.SPEED, w, h, RectF(2 * u, h - 16 * u, 5.5f * u + kw + measure("km/h", Typeface.DEFAULT, 4 * u), h - 4.5f * u)) {
                    text(c, "$kmh", 3 * u, h - 6 * u, 12 * u, Typeface.DEFAULT_BOLD)
                    text(c, "km/h", 4.5f * u + kw, h - 6 * u, 4 * u, Typeface.DEFAULT)
                }
                val s = 30 * u
                val box = RectF(w - s - 3 * u, 3 * u, w - 3 * u, 3 * u + s)
                if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, box) {
                    minimap(c, box, false, Color.argb(110, 0, 0, 0), null,
                        Color.argb(100, Color.red(yellow), Color.green(yellow), Color.blue(yellow)), yellow, idx, u)
                }
                if (motionOn) {
                    val mb = RectF(w - 37 * u, h - 17 * u, w - 3 * u, h - 3 * u)
                    part(c, OverlayPart.MOTION, w, h, mb) { motionBlock(c, mb, u, mo, Color.argb(110, 0, 0, 0), yellow, Typeface.DEFAULT_BOLD) }
                }
            }
            VideoStyle.SPORT -> {
                // Title chip + time chip. Room for the chip: everything left of the round map (26u + margins).
                val ts = fitSize(title.uppercase(), tfCondItalic, w - 3 * u - 26 * u - 3 * u - 4 * u - 5 * u, 4.6f * u)
                p.typeface = tfCondItalic; p.textSize = ts
                val tw = p.measureText(title.uppercase())
                part(c, OverlayPart.TITLE, w, h, RectF(3 * u, 3 * u, max(3 * u + tw + 5 * u, 16 * u), 16 * u)) {
                    c.drawRoundRect(RectF(3 * u, 3 * u, 3 * u + tw + 5 * u, 10.5f * u), 1.8f * u, 1.8f * u, fill(Color.argb(140, 0, 0, 0)))
                    c.drawPath(Path().apply { moveTo(3 * u, 3 * u); lineTo(4.4f * u, 3 * u); lineTo(3 * u, 10.5f * u); close() }, fill(amber))
                    text(c, title.uppercase(), 5.5f * u, 6.75f * u + ts * 0.36f, ts, tfCondItalic, shadow = false)
                    c.drawRoundRect(RectF(3 * u, 11.5f * u, 16 * u, 16 * u), 1.4f * u, 1.4f * u, fill(amber))
                    text(c, clock, 9.5f * u, 15.1f * u, 3.3f * u, tfCond, Color.rgb(20, 20, 20), Paint.Align.CENTER, false)
                }
                // Gauge
                val cx = 18 * u; val cy = h - 19 * u; val r = 13 * u
                if (on(VideoField.SPEED)) part(c, OverlayPart.SPEED, w, h, RectF(cx - r - 2.2f * u, cy - r - 2.2f * u, cx + r + 2.2f * u, cy + r + 2.2f * u)) {
                    c.drawCircle(cx, cy, r + 2.2f * u, fill(Color.argb(120, 0, 0, 0)))
                    stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeWidth = 1.6f * u
                    stroke.strokeCap = Paint.Cap.ROUND
                    val arc = RectF(cx - r, cy - r, cx + r, cy + r)
                    stroke.color = Color.argb(70, 255, 255, 255); c.drawArc(arc, 135f, 270f, false, stroke)
                    stroke.color = amber; c.drawArc(arc, 135f, 270f * (kmh / 120f).coerceIn(0f, 1f), false, stroke)
                    text(c, "$kmh", cx, cy + 3 * u, 11 * u, tfCondItalic, align = Paint.Align.CENTER)
                    text(c, "KM/H", cx, cy + 8.5f * u, 3 * u, tfCond, amber, Paint.Align.CENTER)
                }
                // Distance bar
                val bx0 = 38 * u; val bx1 = w - 4 * u; val by = h - 7 * u
                val bar = RectF(bx0 - 1.5f * u, by - 6 * u, bx1 + 1.5f * u, by + 3.5f * u)
                part(c, OverlayPart.EXTRA, w, h, bar) {
                    c.drawRoundRect(bar, 2 * u, 2 * u, fill(Color.argb(130, 0, 0, 0)))
                    text(c, "${km(kmDone)} KM", bx0, by - 1.8f * u, 3 * u, tfCond, shadow = false)
                    text(c, "${km(totalKm)} KM", bx1, by - 1.8f * u, 3 * u, tfCond, Color.argb(190, 255, 255, 255), Paint.Align.RIGHT, false)
                    c.drawRoundRect(RectF(bx0, by + 0.4f * u, bx1, by + 1.5f * u), u, u, fill(Color.argb(70, 255, 255, 255)))
                    c.drawRoundRect(RectF(bx0, by + 0.4f * u, bx0 + (bx1 - bx0) * (kmDone / totalKm).toFloat().coerceIn(0f, 1f), by + 1.5f * u), u, u, fill(amber))
                }
                // Round mini-map with north
                val ms = 26 * u
                val box = RectF(w - ms - 3 * u, 3 * u, w - 3 * u, 3 * u + ms)
                if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, box) {
                    minimap(c, box, true, Color.argb(190, 15, 18, 20), amber, Color.argb(130, 255, 255, 255), amber, idx, u)
                    if (map != VideoMap.STREETS || streets == null) text(c, "N", box.centerX(), box.top + 3.4f * u, 2.6f * u, tfCond, amber, Paint.Align.CENTER, false)
                }
                if (motionOn) {
                    val mb = RectF(w - 40 * u, h - 27 * u, w - 3 * u, h - 14.5f * u)
                    part(c, OverlayPart.MOTION, w, h, mb) { motionBlock(c, mb, u, mo, Color.argb(130, 0, 0, 0), amber, tfCondItalic) }
                }
            }
            VideoStyle.MINIMAL -> {
                val line = "$clock   ·   ${title.uppercase()}"
                val ls = fitSize(line, tfMedium, w - 8 * u, 2.6f * u)
                part(c, OverlayPart.TITLE, w, h, RectF(3 * u, 3 * u, 5 * u + measure(line, tfMedium, ls), 8 * u)) {
                    text(c, line, 4 * u, 6.5f * u, ls, tfMedium)
                }
                val kw = measure("$kmh", tfLight, 16 * u)
                if (on(VideoField.SPEED)) part(c, OverlayPart.SPEED, w, h, RectF(3 * u, h - 17 * u, 6.5f * u + kw + measure("km/h", tfLight, 3.8f * u), h - 2.5f * u)) {
                    text(c, "$kmh", 4 * u, h - 4 * u, 16 * u, tfLight)
                    text(c, "km/h", 5.5f * u + kw, h - 5 * u, 3.8f * u, tfLight)
                }
                val s = 20 * u
                val box = RectF(w - s - 3 * u, h - s - 3 * u, w - 3 * u, h - 3 * u)
                if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, box) {
                    minimap(c, box, false, null, if (map == VideoMap.STREETS) Color.WHITE else null,
                        Color.argb(85, 255, 255, 255), Color.WHITE, idx, u)
                }
                if (motionOn) {
                    val mb = RectF(w - 36 * u, h - 38 * u, w - 3 * u, h - 25 * u)
                    part(c, OverlayPart.MOTION, w, h, mb) { motionBlock(c, mb, u, mo, null, Color.WHITE, tfLight) }
                }
            }
            VideoStyle.CINEMA -> {
                val bar = 13 * u
                c.drawRect(0f, 0f, w.toFloat(), bar, fill(Color.BLACK))
                c.drawRect(0f, h - bar, w.toFloat(), h.toFloat(), fill(Color.BLACK))
                val date = DateFormat.getDateInstance(DateFormat.LONG).format(Date(tMs)).uppercase()
                val rightW = max(measure("$date · $clock", tfLight, 2.3f * u), if (on(VideoField.SPEED)) measure("$kmh KM/H", tfMedium, 2.8f * u) else 0f)
                val ts = fitSize(title, tfSerifItalic, w - 9 * u - rightW - 4 * u, 5.2f * u)
                part(c, OverlayPart.TITLE, w, h, RectF(4 * u, h - bar + u, 5 * u + measure(title, tfSerifItalic, ts), h - u)) {
                    text(c, title, 4.5f * u, h - bar / 2 + 1.8f * u, ts, tfSerifItalic, Color.rgb(240, 232, 214), shadow = false)
                }
                part(c, OverlayPart.SPEED, w, h, RectF(w - 5 * u - rightW, h - bar + u, w - 4 * u, h - u)) {
                    text(c, "$date · $clock", w - 4.5f * u, h - bar / 2 - 1.2f * u, 2.3f * u, tfLight, Color.rgb(200, 190, 170), Paint.Align.RIGHT, false)
                    if (on(VideoField.SPEED)) text(c, "$kmh KM/H", w - 4.5f * u, h - bar / 2 + 3.2f * u, 2.8f * u, tfMedium, Color.rgb(240, 232, 214), Paint.Align.RIGHT, false)
                }
                val s = bar - 3 * u
                val box = RectF(w - s - 4.5f * u, 1.5f * u, w - 4.5f * u, 1.5f * u + s)
                if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, box) {
                    minimap(c, box, false, null, null, Color.argb(90, Color.red(gold), Color.green(gold), Color.blue(gold)), gold, idx, u * 0.7f)
                }
                if (motionOn) {
                    val mb = RectF(4 * u, 1.5f * u, 40 * u, bar - 1.5f * u)
                    part(c, OverlayPart.MOTION, w, h, mb) { motionBlock(c, mb, u, mo, null, gold, tfMedium) }
                }
            }
            VideoStyle.RETRO -> {
                // Scanlines
                val sl = fill(Color.argb(40, 0, 0, 0))
                var y = 0f
                while (y < h) { c.drawRect(0f, y, w.toFloat(), y + 0.25f * u, sl); y += 0.55f * u }
                if ((tMs / 500) % 2 == 0L) c.drawCircle(6 * u, 7 * u, 1.7f * u, fill(Color.rgb(255, 40, 40)))
                text(c, "REC", 9 * u, 8.6f * u, 4.4f * u, tfMono)
                text(c, "SP ▶", w - 4 * u, 8.6f * u, 4.4f * u, tfMono, align = Paint.Align.RIGHT)
                val rt = deaccent(title.uppercase()).replace("→", ">")
                val rs = fitSize(rt, tfMono, w - 4.5f * u - 18 * u - 8 * u, 3.2f * u)
                part(c, OverlayPart.TITLE, w, h, RectF(4 * u, 14 * u - rs - 0.5f * u, 5 * u + measure(rt, tfMono, rs), 15 * u)) {
                    text(c, rt, 4.5f * u, 14 * u, rs, tfMono)
                }
                val sp = String.format(Locale.US, "%03d KM/H", kmh)
                if (on(VideoField.SPEED)) part(c, OverlayPart.SPEED, w, h, RectF(4 * u, h - 13.5f * u, 5 * u + measure(sp, tfMono, 6 * u), h - 6 * u)) {
                    text(c, sp, 4.5f * u, h - 7 * u, 6 * u, tfMono)
                }
                val d = SimpleDateFormat("MMM.dd yyyy", Locale.ENGLISH).format(Date(tMs)).uppercase()
                val hms = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(tMs))
                val dw = max(measure(d, tfMono, 4.4f * u), measure(hms, tfMono, 4.4f * u))
                part(c, OverlayPart.EXTRA, w, h, RectF(w - 5 * u - dw, h - 17 * u, w - 4 * u, h - 5.5f * u)) {
                    text(c, d, w - 4.5f * u, h - 12 * u, 4.4f * u, tfMono, align = Paint.Align.RIGHT)
                    text(c, hms, w - 4.5f * u, h - 6.5f * u, 4.4f * u, tfMono, align = Paint.Align.RIGHT)
                }
                val s = 18 * u
                val box = RectF(w - s - 4 * u, 12 * u, w - 4 * u, 12 * u + s)
                if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, box) {
                    minimap(c, box, false, null, null, Color.argb(85, 255, 255, 255), Color.WHITE, idx, u)
                }
                if (motionOn) {
                    val mb = RectF(4 * u, h - 30 * u, 40 * u, h - 15.5f * u)
                    part(c, OverlayPart.MOTION, w, h, mb) { motionBlock(c, mb, u, mo, null, Color.WHITE, tfMono) }
                }
            }
            VideoStyle.TELEMETRY -> {
                val ts = fitSize(title, tfArchivo, w - 4 * u - 24 * u - 6 * u, 4 * u)
                val sub = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(tMs)) + " · " + clock
                val ss = fitSize(sub, tfArchivo, w - 4 * u - 24 * u - 6 * u, 2.6f * u)
                part(c, OverlayPart.TITLE, w, h, RectF(3 * u, 2 * u, 5 * u + max(measure(title, tfArchivo, ts), measure(sub, tfArchivo, ss)), 12.5f * u)) {
                    text(c, title, 4 * u, 7 * u, ts, tfArchivo)
                    text(c, sub, 4 * u, 11.5f * u, ss, tfArchivo, Color.argb(220, 255, 255, 255))
                }
                // Glass panel
                val panel = RectF(3 * u, h - 22 * u, w - 3 * u, h - 3 * u)
                part(c, OverlayPart.SPEED, w, h, panel) { telemetryPanel(c, panel, u, kmh, kmDone, idx, trip.obdAt(tMs)) }
                trip.obdAt(tMs)?.takeIf { on(VideoField.RPM) || on(VideoField.COOLANT) }?.let { sample ->
                    val box = RectF(3.5f * u, 14f * u, w - 31f * u, 25f * u)
                    part(c, OverlayPart.EXTRA, w, h, box) { obdChip(c, box, u, sample) }
                }
                if (motionOn) {
                    // Without RPM this is the star of the screen (big, where the OBD chip would sit); with it, just below.
                    val big = obd.isEmpty() || !(on(VideoField.RPM) || on(VideoField.COOLANT))
                    val mb = if (big) RectF(3.5f * u, 13.5f * u, w - 29f * u, 27 * u) else RectF(3.5f * u, 26.5f * u, w - 29f * u, 36 * u)
                    part(c, OverlayPart.MOTION, w, h, mb) { motionBlock(c, mb, u, mo, Color.argb(175, 12, 16, 20), sky, tfArchivoBlack) }
                }
                // Mini-map, glass square
                val s = 24 * u
                val box = RectF(w - s - 3 * u, 3 * u, w - 3 * u, 3 * u + s)
                if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, box) {
                    minimap(c, box, false, Color.argb(175, 12, 16, 20), Color.argb(60, 255, 255, 255),
                        Color.argb(110, 255, 255, 255), sky, idx, u)
                }
            }
            VideoStyle.GAUGES -> gaugesFrame(c, w, h, tMs, idx, clock, kmDone, u)
            VideoStyle.HUD -> hudFrame(c, w, h, tMs, idx, clock, kmDone, u)
        }
    }

    // ── v2.4 Gauges look: dials from the OBD samples recorded with the trip (English labels until 2.5)

    private val obd = trip.obdSamples
    private val hasRpm = obd.any { it.rpm != null }
    private val rpmScaleK: Int = run {
        val seen = obd.mapNotNull { it.rpm }.maxOrNull() ?: 0.0
        max(DashboardMode.rpmMax(ctx), ((seen / 1000.0).let { kotlin.math.ceil(it) } * 1000).toInt()) / 1000
    }
    private val speedScale: Int = DashboardMode.speedMax(ctx)

    /** One OBD value at [tMs], interpolated between the nearest samples that carry it (≤ 3 s away). */
    private fun obdValue(tMs: Long, pick: (ObdTripSample) -> Double?): Double? {
        if (obd.isEmpty()) return null
        val i = obd.binarySearchBy(tMs) { it.timeMs }
        if (i >= 0) pick(obd[i])?.let { return it }
        val after = if (i >= 0) i + 1 else -i - 1
        var a = after - 1
        while (a >= 0 && pick(obd[a]) == null && tMs - obd[a].timeMs <= 3_000) a--
        var b = after
        while (b <= obd.lastIndex && pick(obd[b]) == null && obd[b].timeMs - tMs <= 3_000) b++
        val va = obd.getOrNull(a)?.takeIf { tMs - it.timeMs <= 3_000 }?.let { s -> pick(s)?.let { s.timeMs to it } }
        val vb = obd.getOrNull(b)?.takeIf { it.timeMs - tMs <= 3_000 }?.let { s -> pick(s)?.let { s.timeMs to it } }
        return when {
            va != null && vb != null && vb.first != va.first ->
                va.second + (vb.second - va.second) * (tMs - va.first).toDouble() / (vb.first - va.first)
            else -> (va ?: vb)?.second
        }
    }

    /** GPS speed at [tMs], interpolated between fixes. */
    private fun gpsKmh(tMs: Long): Double {
        val i = indexAt(tMs)
        val b = pts[i]; val a = pts[(i - 1).coerceAtLeast(0)]
        if (b.time == a.time || tMs >= b.time) return b.speedMs * 3.6
        val f = ((tMs - a.time).toDouble() / (b.time - a.time)).coerceIn(0.0, 1.0)
        return (a.speedMs + (b.speedMs - a.speedMs) * f) * 3.6
    }

    private fun speedKmhAt(tMs: Long): Double = obdValue(tMs) { it.speedKmh } ?: gpsKmh(tMs)

    /** Longitudinal acceleration in g, from the speed 1.5 s either side (smooths sensor noise). */
    private fun accelG(tMs: Long): Double = (speedKmhAt(tMs + 1_500) - speedKmhAt(tMs - 1_500)) / 3.6 / 3.0 / 9.81

    private fun gaugesFrame(c: Canvas, w: Int, h: Int, tMs: Long, idx: Int, clock: String, kmDone: Double, u: Float) {
        val dim = Color.argb(200, 255, 255, 255)
        // Brand (top-left): app icon + RIDESCREEN / AA, trip name and time underneath.
        val sub = "$title · $clock"
        val subSize = fitSize(sub, tfMedium, w * 0.38f, 2.3f * u)
        part(c, OverlayPart.TITLE, w, h, RectF(2.5f * u, 2.5f * u, 2.5f * u + max(24 * u, measure(sub, tfMedium, subSize) + 1 * u), 15 * u)) {
            val icon = 7.5f * u
            try {
                ResourcesCompat.getDrawable(ctx.resources, R.mipmap.ic_launcher_round, null)?.let { d ->
                    d.setBounds((3 * u).toInt(), (3 * u).toInt(), (3 * u + icon).toInt(), (3 * u + icon).toInt()); d.draw(c)
                }
            } catch (_: Exception) {}
            text(c, "RIDESCREEN", 3 * u + icon + 1.6f * u, 6.4f * u, 3.3f * u, tfCondItalic)
            text(c, "AA", 3 * u + icon + 1.6f * u, 10f * u, 3.3f * u, tfCondItalic, amber)
            text(c, sub, 3 * u, 14 * u, subSize, tfMedium, dim)
        }

        // Distance (top-centre) with a progress line.
        val dist = RectF(w / 2f - 17 * u, 2.5f * u, w / 2f + 17 * u, 11 * u)
        part(c, OverlayPart.DISTANCE, w, h, dist) {
            val v = km(kmDone)
            val vs = 5.5f * u
            val vw = measure(v, tfCondItalic, vs)
            text(c, v, w / 2f - 1.5f * u, 8 * u, vs, tfCondItalic, align = Paint.Align.CENTER)
            text(c, "KM", w / 2f - 1.5f * u + vw / 2 + 0.8f * u, 8 * u, 2.2f * u, tfMedium, dim)
            stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeWidth = 0.4f * u; stroke.color = Color.argb(90, 255, 255, 255)
            c.drawLine(dist.left + 2 * u, 10 * u, dist.right - 2 * u, 10 * u, stroke)
            val frac = if (totalKm > 0) (kmDone / totalKm).toFloat().coerceIn(0f, 1f) else 0f
            stroke.color = amber
            c.drawLine(dist.left + 2 * u, 10 * u, dist.left + 2 * u + (dist.width() - 4 * u) * frac, 10 * u, stroke)
        }

        // Acceleration (top-right) and coolant below it.
        val accel = RectF(w - 42 * u, 2.5f * u, w - 2.5f * u, 15.5f * u)
        if (on(VideoField.G_FORCE) || on(VideoField.COOLANT)) part(c, OverlayPart.EXTRA, w, h, accel) {
            if (on(VideoField.G_FORCE)) {
                val g = accelG(tMs)
                val box = RectF(w - 18 * u, 3 * u, w - 3 * u, 9.5f * u)
                val braking = g < -0.05
                c.drawRoundRect(box, 1 * u, 1 * u, fill(if (braking) red else amber))
                text(c, String.format(Locale.US, "%.2f g", g).replace("-0.00", "0.00"), box.centerX(), box.bottom - 1.6f * u,
                    3.8f * u, tfCondItalic, Color.rgb(20, 20, 20), Paint.Align.CENTER, shadow = false)
                text(c, if (braking) "BRAKING" else "ACCELERATION", box.left - 1.5f * u, box.bottom - 1.8f * u, 2.2f * u,
                    tfMedium, if (braking) red else amber, Paint.Align.RIGHT)
            }
            if (on(VideoField.COOLANT)) obdValue(tMs) { it.coolantC }?.let { t ->
                val col = when { t >= 108 -> red; t >= 100 -> amber; else -> Color.WHITE }
                text(c, "${t.roundToInt()}°C", w - 3 * u, 14.5f * u, 3 * u, tfCondItalic, col, Paint.Align.RIGHT)
                text(c, "COOLANT", w - 3 * u - measure("${t.roundToInt()}°C", tfCondItalic, 3 * u) - 1.2f * u, 14.5f * u,
                    2 * u, tfMedium, dim, Paint.Align.RIGHT)
            }
        }

        // RPM dial with the speed inside (bottom-left). Trips without OBD get a speed dial instead.
        val r = 11 * u
        val cx = 4 * u + r + 3 * u
        val cy = h - 4 * u - r
        val useRpm = hasRpm && on(VideoField.RPM)
        if (on(VideoField.SPEED) || useRpm) part(c, OverlayPart.SPEED, w, h, RectF(cx - r - 4 * u, cy - r - 4 * u, cx + r + 4 * u, cy + r + 3 * u)) {
            val kmhNow = speedKmhAt(tMs).coerceAtLeast(0.0)
            val rpm = if (useRpm) obdValue(tMs) { it.rpm } else null
            val scale = if (useRpm) rpmScaleK else speedScale
            val value = if (useRpm) (rpm ?: 0.0) / 1000.0 else kmhNow
            dial(c, cx, cy, r, u, value, scale.toDouble(), if (useRpm) 0.85f else 2f, labelEvery = if (useRpm) 1 else 20)
            if (on(VideoField.SPEED)) {
                text(c, String.format(Locale.US, "%03d", kmhNow.roundToInt()), cx, cy + 2.6f * u, 8 * u, tfCondItalic, align = Paint.Align.CENTER)
                text(c, "KM/H", cx, cy + 5.6f * u, 2 * u, tfMedium, dim, Paint.Align.CENTER)
            }
            text(c, if (useRpm) (rpm?.let { "${(it / 10).roundToInt() * 10} RPM" } ?: "— RPM") else "GPS",
                cx, cy + r * 0.92f, 2 * u, tfMedium, if (useRpm) amber else dim, Paint.Align.CENTER)
        }

        // Throttle and engine load (bottom-centre), only when the trip has them.
        val thr = obdValue(tMs) { it.relativeThrottlePct ?: it.throttlePct }
        val load = obdValue(tMs) { it.engineLoadPct }
        if (on(VideoField.THROTTLE_LOAD) && obd.any { it.relativeThrottlePct != null || it.throttlePct != null }) {
            val tr = 8.5f * u
            val tx = w / 2f
            val ty = h - 6.5f * u
            part(c, OverlayPart.THROTTLE, w, h, RectF(tx - tr - 2 * u, ty - tr - 2 * u, tx + tr + 2 * u, ty + 5 * u)) {
                val rect = RectF(tx - tr, ty - tr, tx + tr, ty + tr)
                stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeCap = Paint.Cap.BUTT
                stroke.strokeWidth = 1.4f * u; stroke.color = Color.argb(80, 255, 255, 255)
                c.drawArc(rect, 180f, 180f, false, stroke)
                val tf = ((thr ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
                stroke.color = amber; c.drawArc(rect, 180f, 180f * tf, false, stroke)
                if (load != null) {
                    val inner = RectF(rect).apply { inset(2.2f * u, 2.2f * u) }
                    stroke.strokeWidth = 0.6f * u; stroke.color = Color.argb(60, 255, 255, 255)
                    c.drawArc(inner, 180f, 180f, false, stroke)
                    stroke.color = Color.WHITE
                    c.drawArc(inner, 180f, 180f * (load / 100.0).toFloat().coerceIn(0f, 1f), false, stroke)
                }
                text(c, thr?.let { "${it.roundToInt()}%" } ?: "—", tx, ty - 0.6f * u, 4.6f * u, tfCondItalic, align = Paint.Align.CENTER)
                text(c, "THROTTLE", tx, ty + 2.4f * u, 2 * u, tfMedium, amber, Paint.Align.CENTER)
                if (load != null) text(c, "LOAD ${load.roundToInt()}%", tx, ty + 4.8f * u, 1.8f * u, tfMedium, dim, Paint.Align.CENTER)
            }
        }

        // Lean, G and heart rate. Without RPM this takes the big bottom-centre slot (the throttle one, when free).
        if (motionOn) {
            val throttleShown = on(VideoField.THROTTLE_LOAD) && obd.any { it.relativeThrottlePct != null || it.throttlePct != null }
            val mb = if (throttleShown) RectF(w / 2f - 17 * u, h - 31 * u, w / 2f + 17 * u, h - 19 * u)
                else RectF(w / 2f - 17 * u, h - (if (useRpm) 15 else 20) * u, w / 2f + 17 * u, h - 3 * u)
            part(c, OverlayPart.MOTION, w, h, mb) { motionBlock(c, mb, u, trip.motionAt(tMs), Color.argb(90, 0, 0, 0), amber, tfCondItalic) }
        }

        // Mini-map (bottom-right).
        val s = 20 * u
        val box = RectF(w - s - 3 * u, h - s - 3 * u, w - 3 * u, h - 3 * u)
        if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, box) {
            minimap(c, box, false, Color.argb(150, 12, 14, 16), Color.argb(60, 255, 255, 255),
                Color.argb(110, 255, 255, 255), amber, idx, u)
        }
    }

    // ── ride motion (lean, G, heart rate) from the samples recorded with the trip

    private val hasMotion = trip.motionSamples.isNotEmpty()
    private val motionSummary = trip.motionSummary()

    // ── v2.5 HUD look: big numbers straight on the picture, no panels (English labels for now)

    private val hudRecords = HudMath.leanRecords(trip.motionSamples)
    private val hudBanners = HudMath.banners(pts.map { it.time to it.speedMs * 3.6 }, trip.motionSamples)
    private val hudMaxLean: Double? = motionSummary?.let { s -> listOfNotNull(s.maxLeanLeftDeg, s.maxLeanRightDeg).maxOrNull() }

    private fun hudBar(c: Canvas, x: Float, bottom: Float, wBar: Float, segH: Float, u: Float, lit: Int, n: Int, redFrom: Int) {
        val gap = 0.35f * u
        val segW = (wBar - gap * (n - 1)) / n
        c.save(); c.translate(x, bottom); c.skew(-0.3f, 0f)
        for (i in 0 until n) {
            val col = when {
                i < lit && i >= redFrom -> red
                i < lit -> amber
                i >= redFrom -> Color.argb(110, 229, 57, 53)
                else -> Color.argb(70, 255, 255, 255)
            }
            c.drawRect(i * (segW + gap), -segH, i * (segW + gap) + segW, 0f, fill(col))
        }
        c.restore()
    }

    private fun hudGDot(c: Canvas, cx: Float, cy: Float, r: Float, u: Float, tMs: Long) {
        c.drawCircle(cx, cy, r, fill(Color.argb(85, 12, 16, 20)))
        stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = 0.45f * u; stroke.color = Color.argb(150, 255, 255, 255)
        c.drawCircle(cx, cy, r, stroke)
        stroke.color = Color.argb(60, 255, 255, 255)
        c.drawCircle(cx, cy, r * 0.5f, stroke)
        c.drawLine(cx - r, cy, cx + r, cy, stroke); c.drawLine(cx, cy - r, cx, cy + r, stroke)
        // Position of the dot from the recorded G; without motion data, braking/acceleration from the speed alone.
        fun pos(m: MotionTripSample?, t: Long): Pair<Float, Float> {
            val lat = m?.gLat; val lon = m?.gLon
            return if (lat != null || lon != null) Pair((lat ?: 0.0).toFloat(), (lon ?: 0.0).toFloat()) else Pair(0f, accelG(t).toFloat())
        }
        val k = r * 0.85f
        fun xy(g: Pair<Float, Float>) = Pair(cx + (g.first * k).coerceIn(-r * 0.92f, r * 0.92f), cy - (g.second * k).coerceIn(-r * 0.92f, r * 0.92f))
        // Short tail: the dot 0.2–0.8 s ago, fading.
        for (i in 4 downTo 1) {
            val t = tMs - i * 160L
            val (x, y) = xy(pos(if (hasMotion) trip.motionAt(t) else null, t))
            c.drawCircle(x, y, r * (0.07f + 0.012f * (5 - i)), fill(Color.argb(40 + 30 * (5 - i), 255, 255, 255)))
        }
        val now = if (hasMotion) trip.motionAt(tMs) else null
        val g = pos(now, tMs)
        val (x, y) = xy(g)
        val braking = g.second < -0.08f
        c.drawCircle(x, y, r * 0.17f, fill(if (braking) red else amber))
        c.drawCircle(x, y, r * 0.07f, fill(Color.WHITE))
    }

    private fun hudHeartRow(c: Canvas, right: Float, baseline: Float, u: Float, bpm: Int, tMs: Long, dim: Int) {
        val num = "$bpm"
        val size = 6f * u
        val nw = measure(num, tfCondItalic, size)
        text(c, "BPM", right, baseline, 2.1f * u, tfMedium, dim, Paint.Align.RIGHT)
        val unitW = measure("BPM", tfMedium, 2.1f * u) + 0.8f * u
        text(c, num, right - unitW, baseline, size, tfCondItalic, align = Paint.Align.RIGHT)
        val s = 1.7f * u * (1f + 0.3f * HudMath.beat(tMs, bpm))
        heart(c, right - unitW - nw - 2.6f * u, baseline - size * 0.34f, s, red)
        text(c, "HEART RATE", right, baseline + 2.8f * u, 2f * u, tfMedium, dim, Paint.Align.RIGHT)
    }

    private fun hudReadout(c: Canvas, x: Float, right: Boolean, baseline: Float, u: Float, value: String, unit: String, label: String, dim: Int, color: Int = Color.WHITE) {
        val al = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        val size = 6f * u
        text(c, unit, x, baseline, 2.1f * u, tfMedium, dim, al)
        val unitW = measure(unit, tfMedium, 2.1f * u) + 0.8f * u
        text(c, value, if (right) x - unitW else x + unitW, baseline, size, tfCondItalic, color, al)
        text(c, label, x, baseline + 2.8f * u, 2f * u, tfMedium, dim, al)
    }

    private fun hudFrame(c: Canvas, w: Int, h: Int, tMs: Long, idx: Int, clock: String, kmDone: Double, u: Float) {
        val portrait = h > w
        val dim = Color.argb(205, 255, 255, 255)
        val mo = if (hasMotion) trip.motionAt(tMs) else null

        // Brand, trip name and time (top-left).
        val sub = "$title · $clock"
        val subSize = fitSize(sub, tfMedium, w * 0.4f, 2.3f * u)
        part(c, OverlayPart.TITLE, w, h, RectF(2.5f * u, 2.5f * u, 2.5f * u + max(24 * u, measure(sub, tfMedium, subSize) + 1 * u), 15 * u)) {
            val icon = 7f * u
            try {
                ResourcesCompat.getDrawable(ctx.resources, R.mipmap.ic_launcher_round, null)?.let { d ->
                    d.setBounds((3 * u).toInt(), (3 * u).toInt(), (3 * u + icon).toInt(), (3 * u + icon).toInt()); d.draw(c)
                }
            } catch (_: Exception) {}
            text(c, "RIDESCREEN", 3 * u + icon + 1.6f * u, 7.4f * u, 3.1f * u, tfCondItalic)
            text(c, sub, 3 * u, 13.6f * u, subSize, tfMedium, dim)
        }

        // Distance (top-centre; below the brand on a tall frame) with its progress line.
        val dy = if (portrait) 15.5f * u else 0f
        val dist = RectF(w / 2f - 17 * u, 2.5f * u + dy, w / 2f + 17 * u, 11.5f * u + dy)
        part(c, OverlayPart.DISTANCE, w, h, dist) {
            val v = km(kmDone); val vs = 6.2f * u
            val vw = measure(v, tfCondItalic, vs)
            text(c, v, w / 2f - 1.5f * u, 8.6f * u + dy, vs, tfCondItalic, align = Paint.Align.CENTER)
            text(c, "KM", w / 2f - 1.5f * u + vw / 2 + 0.8f * u, 8.6f * u + dy, 2.2f * u, tfMedium, dim)
            stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeWidth = 0.4f * u; stroke.color = Color.argb(90, 255, 255, 255)
            c.drawLine(dist.left + 2 * u, 10.6f * u + dy, dist.right - 2 * u, 10.6f * u + dy, stroke)
            val frac = if (totalKm > 0) (kmDone / totalKm).toFloat().coerceIn(0f, 1f) else 0f
            stroke.color = amber
            c.drawLine(dist.left + 2 * u, 10.6f * u + dy, dist.left + 2 * u + (dist.width() - 4 * u) * frac, 10.6f * u + dy, stroke)
        }

        // G ball (top-right): dot moves with lateral and braking/acceleration G, short tail behind it.
        val gr = 8.5f * u
        val gcx = w - 3.5f * u - gr; val gcy = 3.5f * u + gr
        if (on(VideoField.G_FORCE)) part(c, OverlayPart.EXTRA, w, h, RectF(gcx - gr - 1 * u, gcy - gr - 1 * u, gcx + gr + 1 * u, gcy + gr + 6 * u)) {
            hudGDot(c, gcx, gcy, gr, u, tMs)
            val gv = mo?.let { HudMath.combinedG(it) } ?: abs(accelG(tMs))
            val braking = (mo?.gLon ?: accelG(tMs)) < -0.08
            text(c, String.format(Locale.US, "%.2f G", gv), gcx, gcy + gr + 4.4f * u, 3.4f * u, tfCondItalic, if (braking) red else Color.WHITE, Paint.Align.CENTER)
        }

        // Side readouts. Left: coolant and load from the OBD. Right: heart rate and throttle.
        val y0 = if (portrait) h * 0.36f else 38f * u
        val coolant = obdValue(tMs) { it.coolantC }.takeIf { on(VideoField.COOLANT) }
        val load = obdValue(tMs) { it.engineLoadPct }.takeIf { on(VideoField.THROTTLE_LOAD) }
        val thr = obdValue(tMs) { it.relativeThrottlePct ?: it.throttlePct }.takeIf { on(VideoField.THROTTLE_LOAD) }
        // Each side column is as tall as the rows it holds: 10u for one, 26u for two.
        if (coolant != null || load != null) {
            val rowsL = (if (coolant != null) 1 else 0) + (if (load != null) 1 else 0)
            part(c, OverlayPart.READOUT_L, w, h, RectF(3 * u, y0 - 7 * u, 30 * u, y0 + (6 + 16 * (rowsL - 1)) * u)) {
                stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeWidth = 0.35f * u; stroke.color = Color.argb(150, 255, 176, 32)
                c.drawLine(3 * u, y0 - 6 * u, 3 * u, y0 + (4 + 16 * (rowsL - 1)) * u, stroke)
                var by = y0
                coolant?.let {
                    val col = when { it >= 108 -> red; it >= 100 -> amber; else -> Color.WHITE }
                    hudReadout(c, 5.5f * u, false, by, u, "${it.roundToInt()}", "°C", "COOLANT", dim, col); by += 16 * u
                }
                load?.let { hudReadout(c, 5.5f * u, false, by, u, "${it.roundToInt()}", "%", "ENGINE LOAD", dim) }
            }
        }
        val bpm = mo?.bpm.takeIf { on(VideoField.HEART_RATE) }
        if (bpm != null || thr != null) {
            val rowsR = (if (bpm != null) 1 else 0) + (if (thr != null) 1 else 0)
            part(c, OverlayPart.READOUT_R, w, h, RectF(w - 32 * u, y0 - 7 * u, w - 3 * u, y0 + (6 + 16 * (rowsR - 1)) * u)) {
                stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeWidth = 0.35f * u; stroke.color = Color.argb(150, 255, 176, 32)
                c.drawLine(w - 3 * u, y0 - 6 * u, w - 3 * u, y0 + (4 + 16 * (rowsR - 1)) * u, stroke)
                var by = y0
                if (bpm != null) { hudHeartRow(c, w - 5.5f * u, by, u, bpm, tMs, dim); by += 16 * u }
                thr?.let { hudReadout(c, w - 5.5f * u, true, by, u, "${it.roundToInt()}", "%", "THROTTLE", dim) }
            }
        }

        // Speed with the RPM bar above it (bottom-left); a speed bar instead when the trip has no OBD RPM.
        val lift = if (portrait) 25f * u else 0f
        val kmhNow = speedKmhAt(tMs).coerceAtLeast(0.0)
        val sb = RectF(3 * u, h - 31 * u - lift, 44 * u, h - 3 * u - lift)
        val useRpm = hasRpm && on(VideoField.RPM)
        if (on(VideoField.SPEED) || useRpm) part(c, OverlayPart.SPEED, w, h, sb) {
            val barW = 38f * u; val x0 = 4 * u; val barBottom = h - 21.5f * u - lift
            if (useRpm) {
                val rpmK = (obdValue(tMs) { it.rpm } ?: 0.0) / 1000.0
                val step = max(1, rpmScaleK / 10)
                var k = 0
                while (k <= rpmScaleK) {
                    val hot = k >= rpmScaleK - 1
                    text(c, "$k", x0 + barW * k / rpmScaleK - 0.9f * u, barBottom - 3.8f * u, 2.2f * u, tfCondItalic, if (hot) red else Color.WHITE)
                    k += step
                }
                hudBar(c, x0, barBottom, barW, 2.6f * u, u, HudMath.litSegments(rpmK, rpmScaleK.toDouble()), 20, 18)
            } else {
                hudBar(c, x0, barBottom, barW, 1.2f * u, u, HudMath.litSegments(kmhNow, speedScale.toDouble()), 20, 21)
            }
            if (on(VideoField.SPEED)) {
                val ns = String.format(Locale.US, "%03d", kmhNow.roundToInt())
                text(c, ns, x0, h - 5.5f * u - lift, 15f * u, tfCondItalic)
                text(c, "KM/H", x0 + measure(ns, tfCondItalic, 15f * u) + 1.4f * u, h - 5.5f * u - lift, 2.6f * u, tfMedium, dim)
            }
        }

        // Lean arc (bottom-centre): angle, peak of the last 10 s, flash on a new best, ride maximum under it.
        val lean = mo?.leanDeg
        if (hasMotion && lean != null && on(VideoField.LEAN)) {
            val r = 15f * u; val cx = w / 2f; val cy = h - 11f * u
            part(c, OverlayPart.MOTION, w, h, RectF(cx - r - 4 * u, cy - r - 4 * u, cx + r + 4 * u, h - 1 * u)) {
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeCap = Paint.Cap.ROUND
                val fl = HudMath.flash(hudRecords, tMs)
                if (fl > 0f) { stroke.strokeWidth = 3.4f * u; stroke.color = Color.argb((140 * fl).toInt(), 255, 176, 32); c.drawArc(rect, 210f, 120f, false, stroke) }
                stroke.strokeWidth = 0.9f * u; stroke.color = Color.argb(130, 255, 255, 255)
                c.drawArc(rect, 210f, 120f, false, stroke)
                for (t in -60..60 step 10) {
                    val ang = Math.toRadians((270 + t).toDouble()); val long = t % 30 == 0
                    stroke.strokeWidth = (if (long) 0.6f else 0.4f) * u; stroke.color = Color.argb(if (long) 220 else 150, 255, 255, 255)
                    val r0 = r * (if (long) 0.84f else 0.9f)
                    c.drawLine(cx + r0 * cos(ang).toFloat(), cy + r0 * sin(ang).toFloat(), cx + r * 1.06f * cos(ang).toFloat(), cy + r * 1.06f * sin(ang).toFloat(), stroke)
                }
                val a = lean.toFloat().coerceIn(-60f, 60f)
                stroke.strokeWidth = 1.7f * u; stroke.color = amber
                c.drawArc(rect, 270f, a, false, stroke)
                val na = Math.toRadians((270 + a).toDouble())
                stroke.strokeWidth = 0.8f * u; stroke.color = Color.WHITE
                c.drawLine(cx + r * 0.72f * cos(na).toFloat(), cy + r * 0.72f * sin(na).toFloat(), cx + r * 1.1f * cos(na).toFloat(), cy + r * 1.1f * sin(na).toFloat(), stroke)
                // Peak of the last 10 s: a small mark outside the arc.
                HudMath.peakLean(trip.motionSamples, tMs)?.let { pk ->
                    if (abs(pk) >= 5.0) {
                        val pa = Math.toRadians((270 + pk.coerceIn(-60.0, 60.0)))
                        val px = cx + r * 1.22f * cos(pa).toFloat(); val py = cy + r * 1.22f * sin(pa).toFloat()
                        c.drawCircle(px, py, 0.8f * u, fill(red))
                    }
                }
                val side = if (lean <= -1.0) "L" else if (lean >= 1.0) "R" else ""
                text(c, "${abs(lean).roundToInt()}°", cx, cy + 0.4f * u, 8.2f * u, tfCondItalic, align = Paint.Align.CENTER)
                text(c, "LEAN $side" + (hudMaxLean?.let { "  ·  MAX ${it.roundToInt()}°" } ?: ""), cx, cy + 6.6f * u, 2.3f * u, tfMedium, dim, Paint.Align.CENTER)
            }
        }

        // Mini-map (bottom-right).
        val ms = 20 * u
        val mbox = RectF(w - ms - 3 * u, h - ms - 3 * u, w - 3 * u, h - 3 * u)
        if (on(VideoField.MINIMAP)) part(c, OverlayPart.MAP, w, h, mbox) {
            minimap(c, mbox, false, Color.argb(120, 12, 14, 16), Color.argb(110, 255, 255, 255), Color.argb(110, 255, 255, 255), amber, idx, u)
        }

        // Peaks of the ride: a short banner as the video passes each one.
        var row = 0
        if (on(VideoField.PEAK_BANNERS)) for (b in hudBanners) {
            if (when (b.kind) {
                    HudMath.Kind.TOP_SPEED -> !on(VideoField.SPEED)
                    HudMath.Kind.MAX_LEAN -> !on(VideoField.LEAN)
                    HudMath.Kind.MAX_G -> !on(VideoField.G_FORCE)
                }) continue
            val al = HudMath.bannerAlpha(b, tMs)
            if (al <= 0f) continue
            val (label, value) = when (b.kind) {
                HudMath.Kind.TOP_SPEED -> "TOP SPEED" to "${b.value.roundToInt()} km/h"
                HudMath.Kind.MAX_LEAN -> "MAX LEAN" to "${b.value.roundToInt()}° " + (if (b.right) "R" else "L")
                HudMath.Kind.MAX_G -> "MAX G" to String.format(Locale.US, "%.2f G", b.value)
            }
            val by = (if (portrait) 38f * u else 25f * u) + row * 11f * u
            val a255 = (255 * al).toInt()
            text(c, label, w / 2f, by, 2.6f * u, tfMedium, Color.argb(a255, 255, 255, 255), Paint.Align.CENTER)
            text(c, value, w / 2f, by + 6.6f * u, 6.6f * u, tfCondItalic, Color.argb(a255, 255, 176, 32), Paint.Align.CENTER)
            row++
        }
    }

    /** Degrees text; the dot-matrix font has no degree sign. */
    private fun deg(v: Double, tf: Typeface) = "${v.roundToInt()}" + if (pixel(tf)) "" else "°"

    /**
     * Lean dial (needle + arc to the side you lean), G and heart rate in [box]. Anything unknown is left out;
     * nothing is drawn when the sample has no data at all. [panel] is an optional rounded background.
     */
    private fun motionBlock(c: Canvas, box: RectF, u: Float, m0: MotionTripSample?, panel: Int?, accent: Int, tf: Typeface) {
        val m = maskMotion(m0) ?: return
        if (m.leanDeg == null && m.gLat == null && m.gLon == null && m.bpm == null) return
        val bh = box.height()
        if (panel != null) c.drawRoundRect(box, 1.6f * u, 1.6f * u, fill(panel))
        var tx = box.left + bh * 0.25f
        val lean = m.leanDeg
        if (lean != null) {
            val r = bh * 0.42f
            val cx = box.left + bh * 0.62f
            val cy = box.top + bh * 0.62f
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeCap = Paint.Cap.ROUND
            stroke.strokeWidth = bh * 0.07f; stroke.color = Color.argb(90, 255, 255, 255)
            c.drawArc(rect, 210f, 120f, false, stroke)                     // -60 to +60 degrees around the top
            val a = lean.toFloat().coerceIn(-60f, 60f)
            stroke.color = accent
            c.drawArc(rect, 270f, a, false, stroke)                         // filled towards the side you lean
            stroke.strokeWidth = bh * 0.05f; stroke.color = Color.argb(120, 255, 255, 255)
            for (t in intArrayOf(-60, -30, 0, 30, 60)) {
                val ang = Math.toRadians((270 + t).toDouble())
                c.drawLine(cx + (r * 1.08f) * cos(ang).toFloat(), cy + (r * 1.08f) * sin(ang).toFloat(),
                    cx + (r * 1.22f) * cos(ang).toFloat(), cy + (r * 1.22f) * sin(ang).toFloat(), stroke)
            }
            val na = Math.toRadians((270 + a).toDouble())          // the bike: a line tilted by the lean
            stroke.strokeWidth = bh * 0.09f; stroke.color = Color.WHITE
            c.drawLine(cx, cy, cx + (r * 0.95f) * cos(na).toFloat(), cy + (r * 0.95f) * sin(na).toFloat(), stroke)
            c.drawCircle(cx, cy, bh * 0.06f, fill(Color.WHITE))
            val side = if (lean <= -1.0) "L " else if (lean >= 1.0) "R " else ""
            text(c, side + deg(abs(lean), tf), cx, box.bottom - bh * 0.05f, bh * 0.28f, tf, Color.WHITE, Paint.Align.CENTER)
            tx = box.left + bh * 1.35f
        }
        val g = if (m.gLat != null || m.gLon != null) sqrt((m.gLat ?: 0.0).let { it * it } + (m.gLon ?: 0.0).let { it * it }) else null
        val rows = (if (g != null) 1 else 0) + (if (m.bpm != null) 1 else 0)
        if (rows == 0) return
        val size = bh * (if (rows == 1) 0.38f else 0.3f)
        var y = if (rows == 1) box.centerY() + size * 0.36f else box.top + bh * 0.46f
        if (g != null) {
            val gs = String.format(Locale.US, "%.2f", g)
            text(c, gs, tx, y, size, tf, Color.WHITE)
            text(c, "G", tx + measure(gs, tf, size) + size * 0.25f, y, size * 0.55f, tf, accent)
            y += bh * 0.38f
        }
        if (m.bpm != null) {
            val hs = size * 0.8f
            heart(c, tx + hs * 0.55f, y - size * 0.34f, hs * 0.5f, red)
            text(c, "${m.bpm}", tx + hs * 1.3f, y, size, tf, Color.WHITE)
        }
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val path = Path().apply {
            moveTo(cx, cy + s * 0.9f)
            cubicTo(cx - s * 1.3f, cy - s * 0.1f, cx - s * 0.8f, cy - s * 1.0f, cx, cy - s * 0.35f)
            cubicTo(cx + s * 0.8f, cy - s * 1.0f, cx + s * 1.3f, cy - s * 0.1f, cx, cy + s * 0.9f)
            close()
        }
        c.drawPath(path, fill(color))
    }

    /** 270° dial: track, red zone from [redFrac], value arc, and a label every [labelEvery] units. */
    private fun dial(c: Canvas, cx: Float, cy: Float, r: Float, u: Float, value: Double, max: Double, redFrac: Float, labelEvery: Int) {
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val start = 135f; val sweep = 270f
        stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeCap = Paint.Cap.BUTT
        stroke.strokeWidth = 1.6f * u
        c.drawRoundRect(RectF(cx - r - 3.6f * u, cy - r - 3.6f * u, cx + r + 3.6f * u, cy + r + 3.6f * u), r, r, fill(Color.argb(90, 0, 0, 0)))
        stroke.color = Color.argb(80, 255, 255, 255); c.drawArc(rect, start, sweep, false, stroke)
        if (redFrac < 1f) { stroke.color = Color.argb(120, 229, 57, 53); c.drawArc(rect, start + sweep * redFrac, sweep * (1 - redFrac), false, stroke) }
        val f = (value / max).toFloat().coerceIn(0f, 1f)
        stroke.color = amber; c.drawArc(rect, start, sweep * min(f, redFrac), false, stroke)
        if (f > redFrac) { stroke.color = red; c.drawArc(rect, start + sweep * redFrac, sweep * (f - redFrac), false, stroke) }
        // Numbers just outside the arc.
        var k = 0
        while (k <= max.toInt()) {
            val a = Math.toRadians((start + sweep * k / max).toDouble())
            val lx = cx + (r + 2.6f * u) * cos(a).toFloat()
            val ly = cy + (r + 2.6f * u) * kotlin.math.sin(a).toFloat() + 0.8f * u
            val hot = k / max >= redFrac
            text(c, "$k", lx, ly, 2 * u, tfCondItalic, if (hot) red else Color.WHITE, Paint.Align.CENTER)
            k += labelEvery
        }
    }

    /** Telemetry's bottom glass panel: speed, distance and the speed graph up to now. */
    private fun telemetryPanel(c: Canvas, panel: RectF, u: Float, kmh: Int, kmDone: Double, idx: Int, obd: ObdTripSample?) {
        c.drawRoundRect(panel, 2.5f * u, 2.5f * u, fill(Color.argb(175, 12, 16, 20)))
        stroke.reset(); stroke.isAntiAlias = true; stroke.style = Paint.Style.STROKE; stroke.strokeWidth = 0.15f * u
        stroke.color = Color.argb(60, 255, 255, 255); c.drawRoundRect(panel, 2.5f * u, 2.5f * u, stroke)
        if (on(VideoField.SPEED)) {
            text(c, ctx.getString(R.string.tvl_speed), panel.left + 3 * u, panel.top + 4.5f * u, 2 * u, tfArchivo, sky, shadow = false)
            text(c, "$kmh", panel.left + 3 * u, panel.top + 13.5f * u, 10 * u, tfArchivoBlack, shadow = false)
            text(c, "km/h", panel.left + 3 * u, panel.top + 17.5f * u, 2.4f * u, tfArchivo, Color.argb(210, 220, 230, 240), shadow = false)
        }
        text(c, ctx.getString(R.string.tvl_distance), panel.right - 3 * u, panel.top + 4.5f * u, 2 * u, tfArchivo, sky, Paint.Align.RIGHT, false)
        text(c, km(kmDone), panel.right - 3 * u, panel.top + 12 * u, 6.5f * u, tfArchivoBlack, align = Paint.Align.RIGHT, shadow = false)
        text(c, ctx.getString(R.string.tvl_of_km, km(totalKm)), panel.right - 3 * u, panel.top + 16.5f * u, 2.2f * u, tfArchivo,
            Color.argb(210, 220, 230, 240), Paint.Align.RIGHT, false)
        // If OBD samples exist, reserve two compact readouts above the graph.
        val lines = if (obd == null) emptyList() else listOf(
            listOfNotNull(
                if (on(VideoField.RPM)) "RPM ${obd.rpm?.roundToInt() ?: "—"}" else null,
                if (on(VideoField.COOLANT)) "COOLANT ${obd.coolantC?.roundToInt()?.let { "$it°C" } ?: "—"}" else null,
            ),
            listOfNotNull(
                if (on(VideoField.THROTTLE_LOAD)) "THROTTLE ${obd.relativeThrottlePct?.roundToInt()?.let { "$it%" } ?: "—"}" else null,
                if (on(VideoField.VOLTAGE)) "VOLT ${obd.voltage?.let { String.format(Locale.US, "%.1fV", it) } ?: "—"}" else null,
            ),
        ).filter { it.isNotEmpty() }.map { it.joinToString("   ") }
        lines.forEachIndexed { i, l ->
            text(c, l, panel.left + 22 * u, panel.top + (5.5f + 3f * i) * u, 2.1f * u, tfArchivo, Color.WHITE, shadow = false)
        }
        // Speed over the whole trip, filled up to now (left out when speed is off)
        if (!on(VideoField.SPEED)) return
        val gx0 = panel.left + 22 * u; val gx1 = panel.right - 21 * u
        val gy0 = panel.top + (when (lines.size) { 0 -> 3f; 1 -> 7f; else -> 10f }) * u; val gy1 = panel.bottom - 3 * u
        val n = speedSeries.size
        val frac = (idx.toFloat() / pts.lastIndex).coerceIn(0f, 1f)
        val cur = (frac * (n - 1)).roundToInt()
        fun gx(i: Int) = gx0 + (gx1 - gx0) * i / (n - 1)
        fun gy(v: Float) = gy1 - (gy1 - gy0) * (v / maxKmh).coerceIn(0f, 1f)
        val area = Path().apply { moveTo(gx0, gy1); for (i in 0..cur) lineTo(gx(i), gy(speedSeries[i])); lineTo(gx(cur), gy1); close() }
        c.drawPath(area, fill(Color.argb(70, 80, 170, 255)))
        val all = Path(); val done = Path()
        for (i in 0 until n) { if (i == 0) { all.moveTo(gx(i), gy(speedSeries[i])); done.moveTo(gx(i), gy(speedSeries[i])) } else { all.lineTo(gx(i), gy(speedSeries[i])); if (i <= cur) done.lineTo(gx(i), gy(speedSeries[i])) } }
        stroke.strokeWidth = 0.35f * u; stroke.color = Color.argb(90, 255, 255, 255); c.drawPath(all, stroke)
        stroke.strokeWidth = 0.5f * u; stroke.color = sky; c.drawPath(done, stroke)
        stroke.strokeWidth = 0.15f * u; stroke.color = Color.argb(140, 255, 255, 255); c.drawLine(gx(cur), gy0, gx(cur), gy1, stroke)
        c.drawCircle(gx(cur), gy(speedSeries[cur]), 0.9f * u, fill(Color.WHITE))
    }

    private fun obdChip(c: Canvas, box: RectF, u: Float, sample: ObdTripSample) {
        c.drawRoundRect(box, 1.2f * u, 1.2f * u, fill(Color.argb(175, 12, 16, 20)))
        val rpm = sample.rpm?.roundToInt()?.toString() ?: "—"
        val coolant = sample.coolantC?.roundToInt()?.let { "$it°C" } ?: "—"
        text(c, "OBD  $rpm rpm  ·  $coolant", box.left + 1.2f * u, box.centerY() + 0.7f * u,
            2.2f * u, tfArchivo, Color.WHITE, shadow = false)
    }

    // ── v2.3: movable pieces

    /** Where each piece was drawn in the last [drawFrame] (frame pixels), for dragging in the preview. */
    val placed = LinkedHashMap<OverlayPart, RectF>()

    /** Draw one piece moved by its [layout] offset, kept inside the frame; [home] is its default bounds. */
    private inline fun part(c: Canvas, part: OverlayPart, w: Int, h: Int, home: RectF, draw: () -> Unit) {
        val o = layout.offsets[part]
        var dx = (o?.dx ?: 0f) * w; var dy = (o?.dy ?: 0f) * h
        dx = if (home.width() < w) dx.coerceIn(-home.left, w - home.right) else 0f
        dy = if (home.height() < h) dy.coerceIn(-home.top, h - home.bottom) else 0f
        placed[part] = RectF(home).apply { offset(dx, dy) }
        if (dx == 0f && dy == 0f) { draw() } else { c.save(); c.translate(dx, dy); draw(); c.restore() }
    }

    // ── opening and closing cards

    private fun cardBackground(c: Canvas, w: Int, h: Int, frame: Bitmap?) {
        if (frame != null) {
            val small = Bitmap.createScaledBitmap(frame, max(1, frame.width / 24), max(1, frame.height / 24), true)
            c.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawColor(Color.argb(150, 8, 10, 12))
        } else c.drawColor(Color.rgb(20, 24, 28))
        p.reset(); p.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(180, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
    }

    private fun accent() = when (style) {
        VideoStyle.SPORT, VideoStyle.GAUGES -> amber; VideoStyle.CINEMA -> gold; VideoStyle.TELEMETRY -> sky
        VideoStyle.CLASSIC -> yellow; else -> Color.WHITE
    }

    private fun headline() = when (style) {
        VideoStyle.SPORT, VideoStyle.GAUGES -> tfCondItalic; VideoStyle.MINIMAL -> tfLight; VideoStyle.CINEMA -> tfSerifItalic
        VideoStyle.RETRO -> tfMono; VideoStyle.TELEMETRY -> tfArchivoBlack; else -> Typeface.DEFAULT_BOLD
    }

    fun drawIntro(c: Canvas, w: Int, h: Int, frame: Bitmap?) {
        cardBackground(c, w, h, frame)
        val u = min(w, h) / 100f
        val t = if (style == VideoStyle.RETRO) deaccent(title.uppercase()).replace("→", ">") else if (style == VideoStyle.SPORT) title.uppercase() else title
        val size = fitSize(t, headline(), w - 12 * u, 8 * u)
        text(c, t, w / 2f, h * 0.46f, size, headline(), align = Paint.Align.CENTER)
        val date = DateFormat.getDateInstance(DateFormat.FULL).format(Date(trip.start))
        text(c, date.replaceFirstChar { it.uppercase() }, w / 2f, h * 0.46f + 6 * u, 3.2f * u, tfLight, accent(), Paint.Align.CENTER)
        val mins = ((trip.end - trip.start) / 60_000L).toInt()
        text(c, "${km(totalKm)} km  ·  $mins min", w / 2f, h * 0.46f + 11 * u, 3 * u, tfMedium, Color.argb(220, 255, 255, 255), Paint.Align.CENTER)
        val s = 26 * u
        routeFit(c, RectF(w / 2f - s / 2, h * 0.62f, w / 2f + s / 2, h * 0.62f + s), accent(), null, 0.8f * u, pts.lastIndex, 0f)
    }

    fun drawOutro(c: Canvas, w: Int, h: Int, frame: Bitmap?, watermark: Boolean) {
        cardBackground(c, w, h, frame)
        val u = min(w, h) / 100f
        val portrait = h > w
        // Portrait (19 Oct): the stats ran into the logo at the bottom. Reserve the logo's band and
        // the 2×2 stats block first, and give the map what's left (still square, centred).
        val brandTop = h - (if (watermark) 23 * u else 6 * u)
        val statsH = (if (trip.motionSummary() != null) 50 else 39) * u
        val mapBox = if (portrait) {
            val side = minOf(w - 16 * u, brandTop - statsH - h * 0.08f)
            RectF((w - side) / 2f, h * 0.08f, (w + side) / 2f, h * 0.08f + side)
        } else RectF(6 * u, 8 * u, w * 0.55f, h - 8 * u)
        routeFit(c, mapBox, accent(), null, 1.1f * u, pts.lastIndex, 1.6f * u)
        val mins = ((trip.end - trip.start) / 60_000L).toInt()
        val avg = if (trip.movingTimeMs > 0) (trip.distanceMeters / (trip.movingTimeMs / 1000.0) * 3.6).roundToInt() else 0
        val rows = ArrayList<Pair<String, String>>().apply {
            add(ctx.getString(R.string.tvl_distance) to "${km(totalKm)} km")
            add(ctx.getString(R.string.tvl_time) to "$mins min")
            add(ctx.getString(R.string.tvl_avg) to "$avg km/h")
            add(ctx.getString(R.string.tvl_max) to "${trip.maxKmh} km/h")
            val ms = motionSummary
            val tfH = headline()
            if (ms != null && (ms.maxLeanLeftDeg != null || ms.maxLeanRightDeg != null)) {
                val l = ms.maxLeanLeftDeg?.let { "L ${deg(it, tfH)}" }
                val r = ms.maxLeanRightDeg?.let { "R ${deg(it, tfH)}" }
                add(ctx.getString(R.string.tvl_lean) to listOfNotNull(l, r).joinToString("  "))
            }
            if (ms?.maxBpm != null) add(ctx.getString(R.string.tvl_pulse) to "${ms.maxBpm} bpm")
        }
        val x0 = if (portrait) 10 * u else w * 0.62f
        val many = rows.size > 4
        var y = if (portrait) mapBox.bottom + 9 * u else h * (if (many) 0.22f else 0.3f)
        text(c, title, x0, y, fitSize(title, headline(), w - x0 - 6 * u, 5.5f * u), headline())
        y += 8 * u
        if (portrait) {
            // Two columns: distance | time, average | top speed.
            val colW = (w - 2 * x0) / 2f
            rows.forEachIndexed { i, (k, v) ->
                val x = x0 + (i % 2) * colW
                val yy = y + (i / 2) * 11 * u
                text(c, k, x, yy, 2.4f * u, tfMedium, accent())
                text(c, v, x, yy + 5.2f * u, fitSize(v, headline(), colW - 3 * u, 4.6f * u), headline())
            }
        } else for ((k, v) in rows) {
            text(c, k, x0, y, 2.4f * u, tfMedium, accent())
            text(c, v, x0, y + 5.2f * u, 4.6f * u, headline())
            y += (if (many) 8.5f else 10f) * u
        }
        if (watermark) brand(c, w, h, u, portrait, x0)
    }

    /**
     * v2.3 · "Made with" + the app icon + RideScreen AA, big enough to read on a phone (it used to be
     * one small line), and the project's address under it once [BRAND_LINK] is set.
     */
    private fun brand(c: Canvas, w: Int, h: Int, u: Float, portrait: Boolean, x0: Float) {
        val icon = 9 * u
        val nameSize = 4.6f * u
        val name = "RideScreen AA"
        p.reset(); p.isAntiAlias = true; p.typeface = tfArchivoBlack; p.textSize = nameSize
        val nameW = p.measureText(name)
        val blockW = icon + 2.2f * u + nameW
        val left = if (portrait) (w - blockW) / 2f else x0
        val linkH = if (BRAND_LINK.isNotEmpty()) 4 * u else 0f
        val top = h - 5 * u - linkH - icon
        try {
            androidx.core.content.res.ResourcesCompat.getDrawable(ctx.resources, R.mipmap.ic_launcher_round, null)?.let { d ->
                d.setBounds(left.toInt(), top.toInt(), (left + icon).toInt(), (top + icon).toInt()); d.draw(c)
            }
        } catch (_: Exception) {}
        val tx = left + icon + 2.2f * u
        text(c, ctx.getString(R.string.tvl_made_with), tx, top + 3.2f * u, 2.3f * u, tfMedium, Color.argb(200, 255, 255, 255))
        text(c, name, tx, top + icon - 0.6f * u, nameSize, tfArchivoBlack)
        if (BRAND_LINK.isNotEmpty()) {
            // v2.3: web + Instagram on one line, shrunk to fit the frame if needed.
            val line = if (BRAND_SOCIAL.isNotEmpty()) "$BRAND_LINK  ·  Instagram $BRAND_SOCIAL" else BRAND_LINK
            val maxW = if (portrait) w * 0.9f else w - left - 4 * u
            text(c, line, if (portrait) w / 2f else left, h - 4.5f * u, fitSize(line, tfMedium, maxW, 2.6f * u), tfMedium, accent(),
                if (portrait) Paint.Align.CENTER else Paint.Align.LEFT)
        }
    }

    private fun fitSize(s: String, tf: Typeface, maxW: Float, start: Float): Float {
        if (pixel(tf)) { var sz = start; while (sz > 8f && PixelFont.width(s, sz) > maxW) sz *= 0.92f; return sz }
        p.reset(); p.typeface = tf; var size = start
        while (size > 8f) { p.textSize = size; if (p.measureText(s) <= maxW) break; size *= 0.92f }
        return size
    }

    private fun deaccent(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    companion object {
        fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
            val y = Math.toRadians(lat2 - lat1)
            return sqrt(x * x + y * y) * 6_371_000.0
        }
    }
}

/** 5×7 dot-matrix letters for the Retro VHS look (camcorder-style), drawn as little squares. */
/** Shown under the logo at the end of trip videos (web / Instagram); empty = nothing. */
const val BRAND_LINK = "ridescreen.app"
/** Instagram handle shown next to [BRAND_LINK]; empty = web only. */
const val BRAND_SOCIAL = "@ridescreen.app"

object PixelFont {
    private val G: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to arrayOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to arrayOf("###..", "#..#.", "#...#", "#...#", "#...#", "#..#.", "###.."),
        'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to arrayOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to arrayOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        ':' to arrayOf(".....", "..#..", "..#..", ".....", "..#..", "..#..", "....."),
        '.' to arrayOf(".....", ".....", ".....", ".....", ".....", ".##..", ".##.."),
        ',' to arrayOf(".....", ".....", ".....", ".....", ".##..", "..#..", ".#..."),
        '>' to arrayOf(".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#..."),
        '-' to arrayOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
        '/' to arrayOf("....#", "....#", "...#.", "..#..", ".#...", "#....", "#...."),
        '▶' to arrayOf("#....", "##...", "###..", "####.", "###..", "##...", "#...."),
        '·' to arrayOf(".....", ".....", ".....", "..#..", ".....", ".....", "....."),
    )
    private val px = Paint().apply { style = Paint.Style.FILL }

    private fun norm(s: String) = java.text.Normalizer.normalize(s.uppercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace("→", ">")

    /** [size] = letter height; returns the drawn width. */
    fun width(s: String, size: Float): Float { val d = size / 7f; return (norm(s).length * 6 - 1) * d }

    fun draw(c: Canvas, s0: String, x: Float, baseline: Float, size: Float, color: Int, align: Paint.Align, shadow: Boolean) {
        val s = norm(s0)
        val d = size / 7f
        val wTot = width(s, size)
        val x0 = when (align) { Paint.Align.CENTER -> x - wTot / 2; Paint.Align.RIGHT -> x - wTot; else -> x }
        val top = baseline - size
        fun pass(dx: Float, dy: Float, col: Int) {
            px.color = col
            var cx = x0 + dx
            for (ch in s) {
                val g = G[ch]
                if (g != null) for (r in 0 until 7) for (k in 0 until 5) if (g[r][k] == '#')
                    c.drawRect(cx + k * d, top + dy + r * d, cx + (k + 1) * d - d * 0.12f, top + dy + (r + 1) * d - d * 0.12f, px)
                cx += 6 * d
            }
        }
        if (shadow) pass(d * 0.6f, d * 0.6f, Color.argb(200, 0, 0, 0))
        pass(0f, 0f, color)
    }
}

