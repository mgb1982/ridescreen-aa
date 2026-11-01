// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale

/**
 * Draws the "Bars" dashboard (design B of the 1 Nov mock-up) on a 800×416 design grid, scaled to whatever
 * area the dash gives us (the 125X asks for 800×416). Called from the compositor's GL thread every
 * 125 ms, so it reuses one bitmap and a handful of paints and allocates nothing per frame.
 */
internal object DashboardRenderer {
    private const val DW = 800f
    private const val DH = 416f
    private const val SEGMENTS = 40

    private class Palette(
        val bg: Int, val fg: Int, val muted: Int, val track: Int, val tile: Int,
        val accent: Int, val red: Int, val amber: Int, val redOff: Int,
    )

    private val DARK = Palette(
        bg = 0xFF0E1511.toInt(), fg = 0xFFF1F4EC.toInt(), muted = 0xFF8E9B8B.toInt(), track = 0xFF26332A.toInt(),
        tile = 0xFF17211A.toInt(), accent = 0xFFD1A955.toInt(), red = 0xFFE5534B.toInt(), amber = 0xFFF0A63A.toInt(),
        redOff = 0xFF3A2423.toInt(),
    )
    private val LIGHT = Palette(
        bg = 0xFFF2F0E8.toInt(), fg = 0xFF14181A.toInt(), muted = 0xFF5B6460.toInt(), track = 0xFFD4D2C6.toInt(),
        tile = 0xFFE4E1D5.toInt(), accent = 0xFF8A6A1E.toInt(), red = 0xFFC62828.toInt(), amber = 0xFFB86E00.toInt(),
        redOff = 0xFFEBCBC6.toInt(),
    )

    private var bmp: Bitmap? = null
    private var canvas: Canvas? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val bold: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val plain: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun render(ctx: Context, w: Int, h: Int, d: DashboardMode.Data, light: Boolean): Bitmap {
        var b = bmp
        if (b == null || b.width != w || b.height != h) {
            b?.recycle()
            b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp = b
            canvas = Canvas(b)
        }
        val c = canvas!!
        val p = if (light) LIGHT else DARK
        c.drawColor(p.bg)
        val s = minOf(w / DW, h / DH)
        c.save()
        c.translate((w - DW * s) / 2f, (h - DH * s) / 2f)
        c.scale(s, s)
        draw(ctx, c, d, p)
        c.restore()
        return b
    }

    private fun draw(ctx: Context, c: Canvas, d: DashboardMode.Data, p: Palette) {
        // RPM bar: 40 segments across the top, the last tenth is the red zone.
        val gap = 4f
        val segW = (740f - gap * (SEGMENTS - 1)) / SEGMENTS
        val lit = DashboardMode.litSegments(d.rpm, SEGMENTS)
        val redFrom = Math.round((DashboardMode.RPM_RED / DashboardMode.RPM_MAX * SEGMENTS).toFloat())
        for (i in 0 until SEGMENTS) {
            val inRed = i >= redFrom
            fill.color = when {
                i < lit -> if (inRed) p.red else p.accent
                inRed -> p.redOff
                else -> p.track
            }
            val x = 30f + i * (segW + gap)
            rect.set(x, 22f, x + segW, 62f)
            c.drawRoundRect(rect, 3f, 3f, fill)
        }

        // Speed, huge, on the left. Grey when it comes from the phone's GPS instead of the bike.
        val speedTxt = d.speedKmh?.let { Math.round(it).toString() } ?: "—"
        label(c, speedTxt, 36f, 300f, 230f, if (d.speedKmh == null) p.muted else p.fg, bold)
        label(c, if (d.speedFromGps) "km/h · GPS" else "km/h", 40f, 350f, 30f, p.muted, plain)
        val foot = when {
            !d.obdLive -> ctx.getString(R.string.dash_no_obd)
            d.rpm != null -> "${Math.round(d.rpm)} rpm"
            else -> ""
        }
        label(c, foot, 40f, 392f, 22f, p.muted, plain)

        // Three tiles on the right.
        val coolCol = d.coolant?.let {
            when (DashboardMode.coolantLevel(it)) {
                DashboardMode.Level.ALERT -> p.red
                DashboardMode.Level.WARN -> p.amber
                DashboardMode.Level.NORMAL -> p.fg
            }
        } ?: p.muted
        tile(c, p, 92f, ctx.getString(R.string.dash_coolant), d.coolant?.let { "${Math.round(it)} °C" }, coolCol,
            d.coolant?.let { ((it - 60.0) / 65.0).toFloat() })
        tile(c, p, 190f, ctx.getString(R.string.dash_throttle), d.throttle?.let { "${Math.round(it)} %" }, if (d.throttle == null) p.muted else p.fg,
            d.throttle?.let { (it / 100.0).toFloat() })
        tile(c, p, 288f, ctx.getString(R.string.dash_battery), d.volt?.let { String.format(Locale.getDefault(), "%.1f V", it) }, if (d.volt == null) p.muted else p.fg,
            d.volt?.let { ((it - 11.0) / 4.0).toFloat() })
    }

    private fun tile(c: Canvas, p: Palette, y: Float, name: String, value: String?, color: Int, frac: Float?) {
        fill.color = p.tile
        rect.set(500f, y, 770f, y + 84f)
        c.drawRoundRect(rect, 12f, 12f, fill)
        text.letterSpacing = 0.08f
        label(c, name.uppercase(Locale.getDefault()), 518f, y + 28f, 17f, p.muted, plain)
        text.letterSpacing = 0f
        label(c, value ?: "—", 518f, y + 64f, 40f, color, bold)
        fill.color = p.track
        rect.set(518f, y + 74f, 752f, y + 80f)
        c.drawRoundRect(rect, 3f, 3f, fill)
        if (frac != null && frac > 0.01f) {
            fill.color = if (color == p.fg) p.accent else color
            rect.set(518f, y + 74f, 518f + 234f * frac.coerceIn(0f, 1f), y + 80f)
            c.drawRoundRect(rect, 3f, 3f, fill)
        }
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface) {
        if (s.isEmpty()) return
        text.typeface = face
        text.textSize = size
        text.color = color
        c.drawText(s, x, y, text)
    }
}
