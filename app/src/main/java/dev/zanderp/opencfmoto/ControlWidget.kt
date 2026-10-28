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
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews

/**
 * Home-screen strip with five buttons over the Garage photo: Connect, Unlock, Lock, Seat and Fuel cap.
 * Connect behaves like [ConnectWidget] (and shows the same status ring). Unlock and Lock go out on one
 * tap through [PkeActionService]; Seat and Fuel cap first ask in [PkeConfirmActivity], which opens the
 * link to the box while the rider reads the question. The photo and the buttons are drawn as bitmaps
 * (RemoteViews can't draw), the photo at the widget's real size so it is neither stretched nor
 * cropped at the corners.
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

        /** Remember the app context and redraw once (also after the Garage photo or bike changed). */
        fun init(ctx: Context) { app = ctx.applicationContext; lastState = null; onPhase(ConnectionState.phase) }

        fun refresh(ctx: Context) = init(ctx)

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

        private const val ICON = 160
        private const val GOLD = 0xFFD1A955.toInt()
        private const val GREEN = 0xFF6FBF73.toInt()
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, st: St) {
            try {
                val connect = draw(st, null)
                val unlock = draw(null, "🔓")
                val lock = draw(null, "🔒")
                val seat = draw(null, "🪑")
                val fuel = draw(null, "⛽")
                val connectPi = PendingIntent.getActivity(
                    ctx, 4321,
                    Intent(ctx, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_WIDGET_CONNECT, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    FLAGS,
                )
                val unlockPi = servicePi(ctx, 4322, PkeUnlock.Action.UNLOCK)
                val lockPi = servicePi(ctx, 4323, PkeUnlock.Action.LOCK)
                val seatPi = confirmPi(ctx, 4324, PkeUnlock.Action.CUSHION)
                val fuelPi = confirmPi(ctx, 4325, PkeUnlock.Action.OILBOX)
                for (id in ids) {
                    val (w, h) = sizeFor(ctx, mgr, id)
                    val v = RemoteViews(ctx.packageName, R.layout.widget_control)
                    v.setImageViewBitmap(R.id.wk_bg, background(ctx, w, h))
                    v.setImageViewBitmap(R.id.wk_connect, connect)
                    v.setImageViewBitmap(R.id.wk_unlock, unlock)
                    v.setImageViewBitmap(R.id.wk_lock, lock)
                    v.setImageViewBitmap(R.id.wk_seat, seat)
                    v.setImageViewBitmap(R.id.wk_fuel, fuel)
                    v.setOnClickPendingIntent(R.id.wk_connect, connectPi)
                    v.setOnClickPendingIntent(R.id.wk_unlock, unlockPi)
                    v.setOnClickPendingIntent(R.id.wk_lock, lockPi)
                    v.setOnClickPendingIntent(R.id.wk_seat, seatPi)
                    v.setOnClickPendingIntent(R.id.wk_fuel, fuelPi)
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

        /** The widget's real size in pixels (capped), so the photo is drawn at its own aspect ratio. */
        private fun sizeFor(ctx: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
            val o = mgr.getAppWidgetOptions(id)
            val d = ctx.resources.displayMetrics.density
            var w = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) * d).toInt()
            var h = (o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) * d).toInt()
            if (w <= 0 || h <= 0) { w = (280 * d).toInt(); h = (72 * d).toInt() }
            val scale = minOf(1f, 960f / w)
            return maxOf(1, (w * scale).toInt()) to maxOf(1, (h * scale).toInt())
        }

        private fun background(ctx: Context, w: Int, h: Int): Bitmap {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val r = minOf(w, h) * 0.24f
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            c.drawRoundRect(rect, r, r, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val photo = runCatching { BikeMemory.selected(ctx)?.photoPath }.getOrNull()
            val src = photo?.let { runCatching { decode(it, w) }.getOrNull() }
            if (src != null) {
                // Centre-crop to the widget's aspect ratio.
                val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
                val rw = (w / scale).toInt().coerceAtMost(src.width)
                val rh = (h / scale).toInt().coerceAtMost(src.height)
                val left = (src.width - rw) / 2
                val top = (src.height - rh) / 2
                c.drawBitmap(src, Rect(left, top, left + rw, top + rh), rect, p)
                src.recycle()
            } else {
                p.color = 0xFF243028.toInt()
                c.drawRect(rect, p)
            }
            // Dim so the buttons always read. SRC_ATOP keeps the photo underneath (SRC_IN would replace it).
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            p.color = 0x8C0E1410.toInt()
            c.drawRect(rect, p)
            p.xfermode = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = minOf(w, h) * 0.02f
            p.color = GOLD
            val inset = p.strokeWidth / 2
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r, r, p)
            return out
        }

        /** One round button: the connect ring (state != null) or an emoji glyph. */
        private fun draw(state: St?, glyph: String?): Bitmap {
            val s = ICON.toFloat()
            val out = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = s / 2
            val cy = s / 2
            p.style = Paint.Style.FILL
            p.color = 0xB3101614.toInt()
            c.drawCircle(cx, cy, s * 0.46f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.03f
            p.color = GOLD
            c.drawCircle(cx, cy, s * 0.46f - p.strokeWidth / 2, p)
            if (glyph != null) {
                val t = Paint(Paint.ANTI_ALIAS_FLAG)
                t.textSize = s * 0.46f
                t.textAlign = Paint.Align.CENTER
                val fm = t.fontMetrics
                c.drawText(glyph, cx, cy - (fm.ascent + fm.descent) / 2, t)
                return out
            }
            val ringR = s * 0.33f
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.05f
            when (state ?: St.READY) {
                St.READY -> {
                    p.color = 0x59FFFFFF
                    c.drawCircle(cx, cy, ringR, p)
                    ConnectWidget.power(c, p, cx, cy, s, 0xFFF1F4EC.toInt())
                }
                St.CONNECTING -> {
                    p.color = 0x40FFFFFF
                    c.drawCircle(cx, cy, ringR, p)
                    p.color = GOLD
                    c.drawArc(RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR), -90f, 260f, false, p)
                    ConnectWidget.power(c, p, cx, cy, s, GOLD)
                }
                St.CONNECTED -> {
                    p.color = GREEN
                    c.drawCircle(cx, cy, ringR, p)
                    p.strokeWidth = s * 0.065f
                    p.strokeJoin = Paint.Join.ROUND
                    c.drawPath(
                        Path().apply { moveTo(s * 0.333f, s * 0.512f); lineTo(s * 0.452f, s * 0.631f); lineTo(s * 0.667f, s * 0.393f) },
                        p,
                    )
                }
            }
            return out
        }

        private fun decode(path: String, targetW: Int): Bitmap? {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            if (b.outWidth <= 0) return null
            var sample = 1
            while (b.outWidth / (sample * 2) >= targetW && b.outHeight / (sample * 2) >= 1) sample *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
