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
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.core.content.res.ResourcesCompat
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/**
 * v2.4 · welcome card (3 s when the dash starts showing us) and goodbye card (5 s when the rider taps
 * Stop / "park with photo"), drawn into the dash video instead of Android Auto by [AaCompositor.showCard].
 *
 * Content comes only from what the phone already knows: bike name + photo, time of day, saved trips,
 * the live ride, the parking photo and one Open-Meteo request (temperature, sky, rain odds, tomorrow
 * 08:00), cached. Missing data simply leaves its piece out. Nothing here talks to the bike.
 * Not yet: minutes to work, fuel on the way, sun in the face, range, green-light streak.
 */
object DashCards {
    private const val PREFS = "dash_cards"
    private const val WELCOME_MS = 3_000L
    private const val FAREWELL_MS = 5_000L
    private const val WX_MAX_AGE_MS = 8 * 3_600_000L

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "dash-cards").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastWelcomeAt = 0L
    @Volatile private var lastWxAt = 0L
    private var pendingStop: Runnable? = null

    private const val GOLD = 0xFFD1A955.toInt()
    private const val INK = 0xFF1A231D.toInt()
    private const val WHITE = 0xFFF1F4EC.toInt()
    private const val MUTED = 0xFFAEBAAA.toInt()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("on", true)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("on", on).apply()

    // ── welcome

    /** Called by [VideoPipeline] right after the bike canvas is configured. */
    fun onDashReady(ctx: Context, vp: VideoPipeline) {
        if (!enabled(ctx)) return
        val now = System.currentTimeMillis()
        if (now - lastWelcomeAt < 120_000L) return   // a reconnect within 2 min is not a new arrival
        lastWelcomeAt = now
        val app = ctx.applicationContext
        vp.showCard(WELCOME_MS) { w, h -> welcomeBitmap(app, w, h) }
    }

    // ── goodbye: show the card, then run the real stop

    /** Runs [then] (the real Stop) now, or after the goodbye card when Android Auto is on the dash. */
    fun farewell(ctx: Context, then: () -> Unit) {
        pendingStop?.let { main.removeCallbacks(it); pendingStop = null; then(); return }   // second tap = stop now
        val vp = AaVideoBridge.pipeline
        if (!enabled(ctx) || vp == null || ConnectionState.phase != Phase.STREAMING) { then(); return }
        val app = ctx.applicationContext
        LogBus.log("[CARD] goodbye card, stopping in ${FAREWELL_MS / 1000} s (tap Stop again to skip)")
        vp.showCard(FAREWELL_MS) { w, h -> farewellBitmap(app, w, h) }
        val r = Runnable { pendingStop = null; then() }
        pendingStop = r
        main.postDelayed(r, FAREWELL_MS)
    }

    // ── weather (one request per session start / park, cached)

    private data class Wx(val temp: Int, val code: Int, val rainSoon: Int, val rainTomorrow: Int, val at: Long)

    private fun wx(ctx: Context): Wx? {
        val s = prefs(ctx).getString("wx", null) ?: return null
        return try {
            val o = JSONObject(s)
            Wx(o.getInt("t"), o.getInt("c"), o.getInt("r"), o.getInt("m"), o.getLong("at"))
                .takeIf { System.currentTimeMillis() - it.at <= WX_MAX_AGE_MS }
        } catch (_: Exception) { null }
    }

    fun refreshWeather(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastWxAt < 20 * 60_000L) return
        lastWxAt = now
        val app = ctx.applicationContext
        io.execute {
            try {
                val loc = RideExtras.lastLocation(app, 6 * 3_600_000L) ?: run { lastWxAt = 0L; return@execute }
                val lat = String.format(Locale.ROOT, "%.2f", loc.latitude)
                val lon = String.format(Locale.ROOT, "%.2f", loc.longitude)
                val body = AppHttp.getText(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code&hourly=precipitation_probability" +
                        "&forecast_days=2&timezone=auto",
                )
                val root = JSONObject(body)
                val cur = root.getJSONObject("current")
                val hourly = root.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val prob = hourly.getJSONArray("precipitation_probability")
                val curTime = cur.getString("time")
                var idx = 0
                for (i in 0 until times.length()) if (times.getString(i) <= curTime) idx = i
                var soon = 0
                for (i in idx..minOf(idx + 2, prob.length() - 1)) soon = maxOf(soon, prob.optInt(i, 0))
                val tomorrow8 = java.time.LocalDateTime.parse(curTime).toLocalDate().plusDays(1).atTime(8, 0).toString()
                var tom = 0
                for (i in 0 until times.length()) if (times.getString(i).startsWith(tomorrow8.take(16))) tom = prob.optInt(i, 0)
                val o = JSONObject().put("t", Math.round(cur.getDouble("temperature_2m")).toInt())
                    .put("c", cur.optInt("weather_code", 0)).put("r", soon).put("m", tom).put("at", System.currentTimeMillis())
                prefs(app).edit().putString("wx", o.toString()).apply()
                LogBus.log("[CARD] weather cached: ${o.getInt("t")}° code=${o.getInt("c")} rain≤3h=$soon% tomorrow8=$tom%")
            } catch (e: Exception) {
                lastWxAt = 0L
                LogBus.log("[CARD] weather failed: ${e.message}")
            }
        }
    }

    private fun skyEmojiText(ctx: Context, code: Int): Pair<String, String> = when (code) {
        0, 1 -> "☀️" to ctx.getString(R.string.dc_wx_clear)
        2, 3 -> "⛅" to ctx.getString(R.string.dc_wx_cloudy)
        45, 48 -> "🌫️" to ctx.getString(R.string.dc_wx_fog)
        in 51..67, in 80..82 -> "🌧️" to ctx.getString(R.string.dc_wx_rain)
        in 71..77, 85, 86 -> "❄️" to ctx.getString(R.string.dc_wx_snow)
        in 95..99 -> "⛈️" to ctx.getString(R.string.dc_wx_storm)
        else -> "⛅" to ctx.getString(R.string.dc_wx_cloudy)
    }

    // ── drawing

    private fun font(ctx: Context, id: Int): Typeface = ResourcesCompat.getFont(ctx, id) ?: Typeface.DEFAULT

    private fun paint(color: Int, size: Float, tf: Typeface, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size; typeface = tf; letterSpacing = spacing
    }

    /** Shrinks [p] until [text] fits [maxW]. */
    private fun fit(p: Paint, text: String, maxW: Float) {
        while (p.measureText(text) > maxW && p.textSize > 12f) p.textSize -= 2f
    }

    private fun background(ctx: Context, c: Canvas, w: Int, h: Int, photo: Boolean) {
        c.drawColor(INK)
        if (!photo) return
        val path = BikeMemory.selected(ctx)?.photoPath ?: return
        val bmp = try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null } ?: return
        val k = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
        val dw = bmp.width * k; val dh = bmp.height * k
        val l = (w - dw) / 2f; val t = (h - dh) / 2f
        c.drawBitmap(bmp, null, android.graphics.RectF(l, t, l + dw, t + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        bmp.recycle()
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, intArrayOf(0xF0141B16.toInt(), 0xB0141B16.toInt(), 0x30141B16), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
    }

    private fun welcomeBitmap(ctx: Context, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = minOf(w / 800f, h / 480f)
        background(ctx, c, w, h, photo = true)
        val x = 44f * s
        var y = 62f * s
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greet = ctx.getString(when { hour < 6 || hour >= 21 -> R.string.dc_good_evening; hour < 14 -> R.string.dc_good_morning; else -> R.string.dc_good_afternoon })
        c.drawText(greet.uppercase(), x, y, paint(GOLD, 24f * s, font(ctx, R.font.archivo_semibold), 0.08f))
        val name = BikeMemory.selected(ctx)?.name.orEmpty().ifBlank { "RideScreen AA" }
        val maxW = w * 0.62f
        val sp = name.indexOf(' ')
        val lines = if (sp > 0) listOf(name.substring(0, sp) to WHITE, name.substring(sp + 1) to GOLD) else listOf(name to WHITE)
        for ((text, col) in lines) {
            val p = paint(col, 70f * s, font(ctx, R.font.archivo_black))
            fit(p, text, maxW)
            y += p.textSize * 1.05f
            c.drawText(text, x, y, p)
        }
        y += 40f * s
        var cx = x
        val big = paint(WHITE, 44f * s, font(ctx, R.font.archivo_black))
        val small = paint(MUTED, 24f * s, font(ctx, R.font.archivo_regular))
        wx(ctx)?.let { f ->
            val (emo, txt) = skyEmojiText(ctx, f.code)
            val t = "${f.temp}°"
            c.drawText(t, cx, y, big)
            c.drawText("$emo $txt", cx, y + 30f * s, small)
            cx += maxOf(big.measureText(t), small.measureText("$emo $txt")) + 36f * s
        }
        val streak = try { Trophies.currentStreak(TripStore.summaries(ctx)) } catch (_: Exception) { 0 }
        if (streak >= 2) {
            val t = "$streak"
            c.drawText(t, cx, y, big)
            c.drawText("🔥 ${ctx.getString(R.string.dc_streak_days)}", cx, y + 30f * s, small)
        }
        wx(ctx)?.takeIf { it.rainSoon >= 60 }?.let {
            c.drawText("☔ " + ctx.getString(R.string.dc_rain_soon, it.rainSoon), x, y + 84f * s, paint(GOLD, 24f * s, font(ctx, R.font.archivo_semibold)))
        }
        return bmp
    }

    private fun farewellBitmap(ctx: Context, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = minOf(w / 800f, h / 480f)
        background(ctx, c, w, h, photo = false)
        val x = 44f * s
        c.drawText(ctx.getString(R.string.dc_bye_title).uppercase(), x, 62f * s, paint(GOLD, 24f * s, font(ctx, R.font.archivo_semibold), 0.08f))
        // Today = saved trips + the ride in progress.
        val since = TripsListActivity.startOfDay(System.currentTimeMillis())
        val today = try { TripStore.summaries(ctx).filter { it.start >= since } } catch (_: Exception) { emptyList() }
        val live = TripLogger.current?.takeIf { it.recording }?.snapshot()
        val km = today.sumOf { it.distanceKm } + (live?.distanceMeters ?: 0.0) / 1000.0
        val ms = today.sumOf { it.movingTimeMs } + (live?.movingTimeMs ?: 0L)
        val max = maxOf(today.maxOfOrNull { it.maxKmh } ?: 0, ((live?.maxSpeedMs ?: 0f) * 3.6f).toInt())
        val avg = if (ms > 0) (km * 3_600_000.0 / ms).toInt() else 0
        val big = paint(WHITE, 64f * s, font(ctx, R.font.archivo_black))
        val bigGold = paint(GOLD, 64f * s, font(ctx, R.font.archivo_black))
        val small = paint(MUTED, 24f * s, font(ctx, R.font.archivo_regular))
        val items = listOf(
            String.format(Locale.getDefault(), "%.1f", km) to R.string.dc_km_today,
            "${ms / 60_000}" to R.string.dc_min,
            "$avg" to R.string.dc_kmh_avg,
            "$max" to R.string.dc_kmh_max,
        )
        var cx = x
        val y0 = 160f * s
        for ((i, it) in items.withIndex()) {
            val p = if (i == 0) bigGold else big
            c.drawText(it.first, cx, y0, p)
            val label = ctx.getString(it.second)
            c.drawText(label, cx, y0 + 32f * s, small)
            cx += maxOf(p.measureText(it.first), small.measureText(label)) + 40f * s
        }
        var y = y0 + 110f * s
        val chip = paint(WHITE, 26f * s, font(ctx, R.font.archivo_regular))
        if (Parking.get(ctx) != null) {
            val t = "🅿️ " + ctx.getString(if (Parking.hasPhoto(ctx)) R.string.dc_parked_photo else R.string.dc_parked)
            val tw = chip.measureText(t)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF263029.toInt() }
            c.drawRoundRect(x, y - 34f * s, x + tw + 36f * s, y + 18f * s, 26f * s, 26f * s, bg)
            c.drawText(t, x + 18f * s, y, chip)
            y += 80f * s
        }
        wx(ctx)?.takeIf { it.rainTomorrow >= 50 }?.let {
            c.drawText("🌧️ " + ctx.getString(R.string.dc_rain_tomorrow, it.rainTomorrow), x, y, small)
        }
        return bmp
    }
}
