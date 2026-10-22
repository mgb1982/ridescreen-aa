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
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.widget.RemoteViews

/**
 * 1×1 home-screen widget (idea from Luis Gil): one tap starts Connect with the saved bike. The tile is
 * the Garage photo with a status ring on top — grey = ready, gold = connecting, green ✓ = connected.
 * It is drawn as one bitmap (RemoteViews can't animate or draw arcs), redrawn when the phase class
 * changes. The 3 s cancel lock lives on the app's own Connect/Stop button, not here.
 */
class ConnectWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val app = ctx.applicationContext
        Thread({ render(app, mgr, ids, stateOf(ConnectionState.phase)) }, "connect-widget").start()
    }

    private enum class St { READY, CONNECTING, CONNECTED }

    companion object {
        @Volatile private var app: Context? = null
        @Volatile private var lastState: St? = null

        /** Remember the app context and redraw once, so a tile left on "connected" by a dead process refreshes. */
        fun init(ctx: Context) { app = ctx.applicationContext; lastState = null; onPhase(ConnectionState.phase) }

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
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ConnectWidget::class.java))
            if (ids.isEmpty()) return
            Thread({ render(ctx, mgr, ids, st) }, "connect-widget").start()
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, st: St) {
            try {
                val bmp = draw(ctx, st)
                val launch = Intent(ctx, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_WIDGET_CONNECT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val pi = PendingIntent.getActivity(
                    ctx, 4311, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                for (id in ids) {
                    val v = RemoteViews(ctx.packageName, R.layout.widget_connect)
                    v.setImageViewBitmap(R.id.wc_img, bmp)
                    v.setOnClickPendingIntent(R.id.wc_root, pi)
                    mgr.updateAppWidget(id, v)
                }
            } catch (e: Exception) {
                LogBus.log("[widget] connect widget draw failed: $e")
            }
        }

        private const val SIZE = 320
        private const val GOLD = 0xFFD1A955.toInt()
        private const val GREEN = 0xFF6FBF73.toInt()

        private fun draw(ctx: Context, st: St): Bitmap {
            val s = SIZE.toFloat()
            val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val r = s * 0.24f
            val rect = RectF(0f, 0f, s, s)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Rounded mask, then the Garage photo (square centre-crop) or the plain card inside it.
            c.drawRoundRect(rect, r, r, p)
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val photo = runCatching { BikeMemory.selected(ctx)?.photoPath }.getOrNull()
            val src = photo?.let { runCatching { decodeSquare(it) }.getOrNull() }
            if (src != null) {
                val side = minOf(src.width, src.height)
                c.drawBitmap(src, Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2), rect, p)
                src.recycle()
            } else {
                p.color = 0xFF243028.toInt()
                c.drawRect(rect, p)
            }
            // Dim so the ring always reads; a green tint once connected.
            p.color = if (st == St.CONNECTED) 0x80103A1E.toInt() else 0x6B0E1410
            c.drawRect(rect, p)
            p.xfermode = null

            // Gold edge.
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.018f
            p.color = GOLD
            val inset = p.strokeWidth / 2
            c.drawRoundRect(RectF(inset, inset, s - inset, s - inset), r, r, p)

            val cx = s / 2; val cy = s / 2
            val ringR = s * 0.315f
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.046f
            val ringBox = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
            when (st) {
                St.READY -> {
                    p.color = 0x59FFFFFF
                    c.drawCircle(cx, cy, ringR, p)
                    power(c, p, cx, cy, s, 0xFFF1F4EC.toInt())
                }
                St.CONNECTING -> {
                    p.color = 0x40FFFFFF
                    c.drawCircle(cx, cy, ringR, p)
                    p.color = GOLD
                    c.drawArc(ringBox, -90f, 260f, false, p)
                    power(c, p, cx, cy, s, GOLD)
                }
                St.CONNECTED -> {
                    p.color = GREEN
                    c.drawCircle(cx, cy, ringR, p)
                    p.strokeWidth = s * 0.06f
                    p.strokeJoin = Paint.Join.ROUND
                    val path = android.graphics.Path().apply {
                        moveTo(s * 0.333f, s * 0.512f); lineTo(s * 0.452f, s * 0.631f); lineTo(s * 0.667f, s * 0.393f)
                    }
                    c.drawPath(path, p)
                }
            }
            return out
        }

        /** The ⏻ glyph: an open ring with a stroke through the gap. */
        private fun power(c: Canvas, p: Paint, cx: Float, cy: Float, s: Float, color: Int) {
            p.color = color
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = s * 0.04f
            val gr = s * 0.15f
            c.drawArc(RectF(cx - gr, cy - gr * 0.9f, cx + gr, cy + gr * 1.1f), -60f, 300f, false, p)
            c.drawLine(cx, cy - gr * 1.25f, cx, cy - gr * 0.1f, p)
        }

        private fun decodeSquare(path: String): Bitmap? {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            if (b.outWidth <= 0) return null
            var sample = 1
            while (b.outWidth / (sample * 2) >= SIZE && b.outHeight / (sample * 2) >= SIZE) sample *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
