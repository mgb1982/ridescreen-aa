// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Bike card" 4×2 widget: the Garage photo as a header with the bike's name and the last keyless-box
 * reading (lock state, voltage, time), and a band with five buttons — Connect, Unlock, Lock, Seat and
 * Fuel cap. Same look as the home screen: the app's greens, gold Archivo Black title, line icons in a
 * tinted circle per function. Connect behaves like [ConnectWidget] and shows its status; Unlock and Lock
 * go out on one tap through [PkeActionService]; Seat and Fuel cap first ask in [PkeConfirmActivity].
 * RemoteViews can't draw text in a custom font or use fractions, so the header and each button are
 * drawn as bitmaps at the widget's real size.
 */
class ControlWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val app = ctx.applicationContext
        Thread({ render(app, mgr, ids, stateOf(ConnectionState.phase)) }, "control-widget").start()
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        val app = ctx.applicationContext
        Thread({ render(app, mgr, intArrayOf(id), stateOf(ConnectionState.phase)) }, "control-widget").start()
    }

    private enum class St { READY, CONNECTING, CONNECTED }

    companion object {
        @Volatile private var app: Context? = null
        @Volatile private var lastState: St? = null
        @Volatile private var lastReadingKey: String? = null

        /** Remember the app context and redraw once (also after the Garage photo or bike changed). */
        fun init(ctx: Context) { app = ctx.applicationContext; lastState = null; onPhase(ConnectionState.phase) }

        fun refresh(ctx: Context) = init(ctx)

        /** Redraw when the keyless-box reading shown in the header changed (cheap no-op otherwise). */
        fun onReading(ctx: Context) {
            val r = PkeBattery.last(ctx)
            val key = "${r?.locked}|${r?.volts?.let { "%.1f".format(it) }}|${r?.atMs?.div(60_000L)}"
            if (key == lastReadingKey) return
            lastReadingKey = key
            app = ctx.applicationContext
            lastState = null
            onPhase(ConnectionState.phase)
        }

        private fun stateOf(p: Phase) = when {
            p == Phase.STREAMING || p == Phase.MIRRORING -> St.CONNECTED
            p.busy -> St.CONNECTING
            else -> St.READY
        }

        /** Called from [ConnectionState.set]; cheap when the visible state hasn't changed. */
        fun onPhase(p: Phase) {
            val ctx = app ?: return
            val st = stateOf(p)
            if (st == lastState) return
            lastState = st
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ControlWidget::class.java))
            if (ids.isEmpty()) return
            Thread({ render(ctx, mgr, ids, st) }, "control-widget").start()
        }

        // The app's palette (colors.xml) and the per-function accents of the home tiles.
        private const val SURFACE = 0xFF243028.toInt()
        private const val GOLD = 0xFFD1A955.toInt()
        private const val GREEN = 0xFF5FD07A.toInt()
        private const val BLUE = 0xFF5B8DEF.toInt()
        private const val TEAL = 0xFF4FB6A6.toInt()
        private const val ORANGE = 0xFFE5774E.toInt()
        private const val TEXT = 0xFFF1F4EC.toInt()
        private const val TEXT2 = 0xFFAEBAAA.toInt()
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /** Header share of the widget height; the rest is the button band (matches the weights in widget_control.xml). */
        private const val HEADER = 0.61f

        private class Spec(val id: Int, val icon: Int, val accent: Int, val label: Int)

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, st: St) {
            try {
                val connectPi = PendingIntent.getActivity(
                    ctx, 4321,
                    Intent(ctx, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_WIDGET_CONNECT, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    FLAGS,
                )
                val pis = mapOf(
                    R.id.wk_connect to connectPi,
                    R.id.wk_unlock to servicePi(ctx, 4322, PkeUnlock.Action.UNLOCK),
                    R.id.wk_lock to servicePi(ctx, 4323, PkeUnlock.Action.LOCK),
                    R.id.wk_seat to confirmPi(ctx, 4324, PkeUnlock.Action.CUSHION),
                    R.id.wk_fuel to confirmPi(ctx, 4325, PkeUnlock.Action.OILBOX),
                )
                val connectIcon = if (st == St.CONNECTED) R.drawable.ic_line_check else R.drawable.ic_line_power
                val connectAccent = if (st == St.CONNECTED) GREEN else GOLD
                val specs = listOf(
                    Spec(R.id.wk_connect, connectIcon, connectAccent, R.string.widget_connect_label),
                    Spec(R.id.wk_unlock, R.drawable.ic_line_unlock, GREEN, R.string.pke_short_unlock),
                    Spec(R.id.wk_lock, R.drawable.ic_line_lock, BLUE, R.string.pke_short_lock),
                    Spec(R.id.wk_seat, R.drawable.ic_line_seat, TEAL, R.string.pke_short_seat),
                    Spec(R.id.wk_fuel, R.drawable.ic_line_fuel, ORANGE, R.string.pke_short_fuel),
                )
                for (id in ids) {
                    val (wDp, hDp, k) = sizeFor(ctx, mgr, id)
                    val w = (wDp * k).toInt()
                    val h = (hDp * k).toInt()
                    val v = RemoteViews(ctx.packageName, R.layout.widget_control)
                    v.setImageViewBitmap(R.id.wk_bg, header(ctx, w, h, k))
                    for (s in specs) {
                        v.setImageViewBitmap(s.id, button(ctx, w / 5, h - (h * HEADER).toInt(), k, s, st == St.CONNECTING && s.id == R.id.wk_connect))
                        v.setOnClickPendingIntent(s.id, pis.getValue(s.id))
                    }
                    mgr.updateAppWidget(id, v)
                }
            } catch (e: Exception) {
                LogBus.log("[widget] control widget draw failed: $e")
            }
        }

        private fun servicePi(ctx: Context, code: Int, a: PkeUnlock.Action): PendingIntent =
            PendingIntent.getForegroundService(
                ctx, code, Intent(ctx, PkeActionService::class.java).putExtra(PkeActionService.EXTRA_ACTION, a.name), FLAGS,
            )

        private fun confirmPi(ctx: Context, code: Int, a: PkeUnlock.Action): PendingIntent =
            PendingIntent.getActivity(
                ctx, code,
                Intent(ctx, PkeConfirmActivity::class.java)
                    .putExtra(PkeActionService.EXTRA_ACTION, a.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                FLAGS,
            )

        /** Width and height in dp, and the pixels per dp to draw at (capped so the bitmaps stay small). */
        private fun sizeFor(ctx: Context, mgr: AppWidgetManager, id: Int): Triple<Float, Float, Float> {
            val o = mgr.getAppWidgetOptions(id)
            val d = ctx.resources.displayMetrics.density
            var w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
            var h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).toFloat()
            if (w <= 0f || h <= 0f) { w = 300f; h = 120f }
            return Triple(w, h, minOf(d, 960f / w))
        }

        private fun font(ctx: Context, res: Int): Typeface = ResourcesCompat.getFont(ctx, res) ?: Typeface.DEFAULT_BOLD

        private fun icon(ctx: Context, res: Int, color: Int): Drawable =
            ContextCompat.getDrawable(ctx, res)!!.mutate().also { it.setTint(color) }

        private fun drawIcon(c: Canvas, d: Drawable, cx: Float, cy: Float, size: Float) {
            d.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt())
            d.draw(c)
        }

        /** Photo header + name + last reading, over the surface colour, in a rounded card with a gold edge. */
        private fun header(ctx: Context, w: Int, h: Int, k: Float): Bitmap {
            val content = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(content)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            c.drawColor(SURFACE)
            val ph = (h * HEADER).toInt()

            val bike = runCatching { BikeMemory.selected(ctx) }.getOrNull()
            val src = bike?.photoPath?.let { runCatching { decode(it, w) }.getOrNull() }
            if (src != null) {
                // Centre-crop to the header, biased towards the top so the rider's head and the bike's front stay in.
                val s = maxOf(w.toFloat() / src.width, ph.toFloat() / src.height)
                val rw = minOf(src.width.toFloat(), w / s)
                val rh = minOf(src.height.toFloat(), ph / s)
                val left = ((src.width - rw) / 2f).toInt()
                val top = ((src.height - rh) * 0.10f).toInt()
                c.drawBitmap(src, Rect(left, top, left + rw.toInt(), top + rh.toInt()), Rect(0, 0, w, ph), p)
                src.recycle()
            }
            p.shader = LinearGradient(0f, 0f, 0f, ph.toFloat(), 0x000A0E0C, 0xEB0A0E0C.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), ph.toFloat(), p)
            p.shader = null

            val padX = 14f * k
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
            tp.typeface = font(ctx, R.font.archivo_black)
            tp.color = GOLD
            tp.textSize = 21f * k
            val name = bike?.name?.takeIf { it.isNotBlank() } ?: "RideScreen AA"
            c.drawText(TextUtils.ellipsize(name, tp, w - 2 * padX, TextUtils.TruncateAt.END).toString(), padX, ph - 30f * k, tp)

            val r = runCatching { PkeBattery.last(ctx) }.getOrNull()
            val parts = buildList {
                when (r?.locked) {
                    true -> add(ctx.getString(R.string.widget_state_locked))
                    false -> add(ctx.getString(R.string.widget_state_unlocked))
                    null -> Unit
                }
                r?.volts?.let { add(String.format(Locale.getDefault(), "%.1f V", it)) }
                r?.let { add(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.atMs))) }
            }
            val status = if (parts.isEmpty()) ctx.getString(R.string.widget_state_none) else parts.joinToString(" · ")
            val sp = TextPaint(Paint.ANTI_ALIAS_FLAG)
            sp.typeface = font(ctx, R.font.archivo_regular)
            sp.color = TEXT2
            sp.textSize = 11f * k
            val glyphRes = when (r?.locked) { true -> R.drawable.ic_line_lock; false -> R.drawable.ic_line_unlock; null -> 0 }
            var x = padX
            if (glyphRes != 0) {
                drawIcon(c, icon(ctx, glyphRes, TEXT2), padX + 7f * k, ph - 13f * k, 15f * k)
                x += 17f * k
            }
            c.drawText(TextUtils.ellipsize(status, sp, w - x - padX, TextUtils.TruncateAt.END).toString(), x, ph - 9f * k, sp)

            // Round the corners and add the gold edge.
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val oc = Canvas(out)
            val rad = 22f * k
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            val mp = Paint(Paint.ANTI_ALIAS_FLAG)
            oc.drawRoundRect(rect, rad, rad, mp)
            mp.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            oc.drawBitmap(content, 0f, 0f, mp)
            content.recycle()
            val ep = Paint(Paint.ANTI_ALIAS_FLAG)
            ep.style = Paint.Style.STROKE
            ep.strokeWidth = k
            ep.color = GOLD
            oc.drawRoundRect(RectF(k / 2, k / 2, w - k / 2, h - k / 2), rad, rad, ep)
            return out
        }

        /** One button of the band: a tinted circle with the line icon, and its label underneath. */
        private fun button(ctx: Context, w: Int, h: Int, k: Float, s: Spec, ring: Boolean): Bitmap {
            val out = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val d = minOf(h * 0.58f, w * 0.70f)
            val labelSize = (h * 0.20f).coerceIn(8f * k, 11f * k)
            val gap = 3f * k
            val total = d + gap + labelSize * 1.15f
            val top = (h - total) / 2f
            val cx = w / 2f
            val cy = top + d / 2f

            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = (s.accent and 0x00FFFFFF) or 0x42000000
            c.drawCircle(cx, cy, d / 2f, p)
            if (ring) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2f * k
                p.strokeCap = Paint.Cap.ROUND
                p.color = s.accent
                c.drawArc(RectF(cx - d / 2f + k, cy - d / 2f + k, cx + d / 2f - k, cy + d / 2f - k), -90f, 260f, false, p)
            }
            drawIcon(c, icon(ctx, s.icon, s.accent), cx, cy, d * 0.58f)

            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
            tp.typeface = font(ctx, R.font.archivo_medium)
            tp.color = TEXT
            tp.textSize = labelSize
            val label = ctx.getString(s.label)
            while (tp.measureText(label) > w - 2f * k && tp.textSize > 7f * k) tp.textSize -= 0.5f * k
            tp.textAlign = Paint.Align.CENTER
            c.drawText(label, cx, top + d + gap + tp.textSize * 0.9f, tp)
            return out
        }

        private fun decode(path: String, targetW: Int): Bitmap? {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            if (b.outWidth <= 0) return null
            var sample = 1
            while (b.outWidth / (sample * 2) >= targetW && b.outHeight / (sample * 2) >= 1) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            return trimBars(bmp)
        }

        /** Cuts uniform near-black bars off the sides/top/bottom of a photo, so they never show up in the crop. */
        private fun trimBars(b: Bitmap): Bitmap {
            fun dark(x: Int, y: Int): Boolean {
                val p = b.getPixel(x, y)
                return (Color.red(p) + Color.green(p) + Color.blue(p)) / 3 < 14
            }
            fun colDark(x: Int): Boolean {
                val step = maxOf(1, b.height / 32); var n = 0; var d = 0; var y = 0
                while (y < b.height) { if (dark(x, y)) d++; n++; y += step }
                return d * 100 >= n * 97
            }
            fun rowDark(y: Int): Boolean {
                val step = maxOf(1, b.width / 32); var n = 0; var d = 0; var x = 0
                while (x < b.width) { if (dark(x, y)) d++; n++; x += step }
                return d * 100 >= n * 97
            }
            var l = 0; var r = b.width - 1; var t = 0; var bt = b.height - 1
            while (l < b.width / 3 && colDark(l)) l++
            while (r > b.width * 2 / 3 && colDark(r)) r--
            while (t < b.height / 3 && rowDark(t)) t++
            while (bt > b.height * 2 / 3 && rowDark(bt)) bt--
            if (l == 0 && r == b.width - 1 && t == 0 && bt == b.height - 1) return b
            return Bitmap.createBitmap(b, l, t, r - l + 1, bt - t + 1)
        }
    }
}
