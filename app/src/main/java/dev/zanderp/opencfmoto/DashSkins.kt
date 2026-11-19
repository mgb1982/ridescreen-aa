// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * v2.7 · five "photo-real" dash faces, each with a day and a night look, drawn in the same 800×416 space and
 * in the same two passes as [DashboardRenderer] (cached static layer + live values):
 *
 *  - LED: brushed metal, an arc of LEDs, a red seven-segment speed read-out, LED bar meters.
 *  - ANALOG: two chrome-rimmed dials (speed, RPM) and two small ones (coolant, battery).
 *  - VFD: a vacuum-fluorescent display (cyan at night, blue on grey by day).
 *  - MINIMAL: the speed, huge, under a thin RPM arc, with one line of readings.
 *  - COCKPIT: green (night) or amber (day) on black, an aircraft-style vertical tape and bar gauges.
 *
 * Without OBD ([DashboardMode.Data.noObd]) the OBD slots carry the phone readings chosen by
 * [DashFallback.plan] (they arrive under the slot id, "coolant", "volt"…, with their own [DashboardRenderer.Metric.id]),
 * and the RPM part shows what [DashSkinMath.secondary] picks: the speed, an analog clock, or an altitude tape.
 *
 * Everything readable stays above [DashboardRenderer.VIS_BOTTOM] (and [DashboardRenderer.VIS_BOTTOM_RIGHT]
 * right of [DashboardRenderer.VIS_RIGHT_STEP_X]): the Zontes dash lays its own strip over the bottom.
 */
internal object DashSkins {
    private const val W = 800f
    private const val H = 416f

    val DESIGNS = setOf(
        DashboardMode.Design.LED, DashboardMode.Design.ANALOG, DashboardMode.Design.VFD,
        DashboardMode.Design.MINIMAL, DashboardMode.Design.COCKPIT,
    )

    /** What one call draws. [still]: the cached static pass; otherwise the live one. [drawBg]: no rider picture behind. */
    class Frame(
        val c: Canvas, val d: DashboardMode.Data, val a: Map<String, DashboardRenderer.Metric>,
        val day: Boolean, val still: Boolean, val drawBg: Boolean,
        val speedMax: Int, val rpmMax: Int, val plan: DashFallback.Plan?, val accent: Int,
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val matrix = Matrix()

    private val MONO: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val SANS: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val SANS_LIGHT: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    private const val RED = 0xFFFF201C.toInt()
    private const val AMBER = 0xFFFFAA14.toInt()
    private const val GREEN = 0xFF28E646.toInt()

    fun draw(design: DashboardMode.Design, f: Frame) {
        reset()
        when (design) {
            DashboardMode.Design.LED -> led(f)
            DashboardMode.Design.ANALOG -> analog(f)
            DashboardMode.Design.VFD -> vfd(f)
            DashboardMode.Design.MINIMAL -> minimal(f)
            DashboardMode.Design.COCKPIT -> cockpit(f)
            else -> Unit
        }
        reset()
    }

    // ───────────────────────── shared helpers

    private fun reset() {
        for (p in listOf(fill, stroke, text)) {
            p.shader = null; p.maskFilter = null; p.clearShadowLayer(); p.alpha = 255
        }
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.pathEffect = null
    }

    private fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    private fun scale(color: Int, k: Float): Int = Color.rgb(
        (Color.red(color) * k).toInt().coerceIn(0, 255), (Color.green(color) * k).toInt().coerceIn(0, 255),
        (Color.blue(color) * k).toInt().coerceIn(0, 255))
    private fun plus(color: Int, n: Int): Int = Color.rgb(
        (Color.red(color) + n).coerceIn(0, 255), (Color.green(color) + n).coerceIn(0, 255), (Color.blue(color) + n).coerceIn(0, 255))

    private fun round(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, p: Paint) {
        rect.set(x, y, x + w, y + h); c.drawRoundRect(rect, r, r, p)
    }

    private fun txt(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface,
        align: Paint.Align = Paint.Align.CENTER, shadow: Int = 0, glow: Float = 0f,
    ) {
        text.reset(); text.isAntiAlias = true
        text.typeface = tf; text.textSize = size; text.textAlign = align
        val base = y + size * 0.36f
        if (shadow != 0) { text.color = shadow; c.drawText(s, x + 1f, base + 1.2f, text) }
        if (glow > 0f) text.setShadowLayer(glow, 0f, 0f, alpha(color, .9f))
        text.color = color
        c.drawText(s, x, base, text)
        text.clearShadowLayer()
    }

    /** Text shrunk so it fits [maxW]. */
    private fun fitSize(s: String, tf: Typeface, start: Float, maxW: Float): Float {
        text.typeface = tf; text.textSize = start
        val w = text.measureText(s)
        return if (w <= maxW || w <= 0f) start else start * maxW / w
    }

    /** Brushed lines: a fixed seed so every rebuilt layer looks the same. */
    private fun brushed(c: Canvas, n: Int, a: Float) {
        val rnd = java.util.Random(7)
        stroke.strokeWidth = .6f
        repeat(n) {
            val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H; val l = 6f + rnd.nextFloat() * 30f; val k = rnd.nextFloat() * a
            stroke.color = if (rnd.nextBoolean()) alpha(Color.WHITE, k) else alpha(Color.BLACK, k * 1.5f)
            c.drawLine(x, y, x + l, y, stroke)
        }
    }

    private fun vignette(c: Canvas, a: Float) {
        fill.shader = RadialGradient(400f, 208f, 560f, intArrayOf(0, 0, alpha(Color.BLACK, a)), floatArrayOf(0f, .21f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, fill); fill.shader = null
    }

    private fun vertical(c: Canvas, top: Int, mid: Int, bottom: Int) {
        fill.shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(top, mid, bottom), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, fill); fill.shader = null
    }

    private fun bezel(c: Canvas, colors: IntArray) {
        stroke.strokeWidth = 6f
        stroke.shader = LinearGradient(0f, 0f, 0f, H, colors, floatArrayOf(0f, .1f, .9f, 1f), Shader.TileMode.CLAMP)
        rect.set(4f, 4f, W - 4f, H - 4f); c.drawRoundRect(rect, 14f, 14f, stroke)
        stroke.shader = null; stroke.strokeWidth = 1f; stroke.color = alpha(Color.WHITE, .12f)
        rect.set(8f, 8f, W - 8f, H - 8f); c.drawRoundRect(rect, 11f, 11f, stroke)
    }

    private fun screw(c: Canvas, x: Float, y: Float) {
        fill.shader = RadialGradient(x - 2f, y - 2f, 8f, intArrayOf(0xFFC9CBCF.toInt(), 0xFF6D6F74.toInt(), 0xFF222222.toInt()),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 6f, fill); fill.shader = null
        stroke.color = alpha(Color.BLACK, .7f); stroke.strokeWidth = 1.6f
        c.drawLine(x - 4f, y + 1.5f, x + 4f, y - 1.5f, stroke)
    }

    private fun screws(c: Canvas) { screw(c, 24f, 24f); screw(c, W - 24f, 24f); screw(c, 24f, H - 24f); screw(c, W - 24f, H - 24f) }

    /** A round LED: socket, ring and dark lens in the static pass; the lit lens and its glow in the live one. */
    private fun led(c: Canvas, x: Float, y: Float, r: Float, on: Boolean, col: Int, still: Boolean) {
        if (still) {
            fill.color = alpha(Color.BLACK, .8f); c.drawCircle(x, y + 1f, r + 2.2f, fill)
            fill.shader = LinearGradient(x, y - r - 2f, x, y + r + 2f, 0xFF111111.toInt(), 0xFF5A5C60.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r + 1.6f, fill)
            fill.shader = RadialGradient(x - r * .3f, y - r * .35f, r * 1.2f, scale(col, .35f), scale(col, .11f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, fill); fill.shader = null
            highlight(c, x, y, r)
        } else if (on) {
            fill.color = col
            fill.maskFilter = BlurMaskFilter(r * 1.6f, BlurMaskFilter.Blur.NORMAL)
            fill.alpha = 235; c.drawCircle(x, y, r * 1.4f, fill)
            fill.maskFilter = null; fill.alpha = 255
            fill.shader = RadialGradient(x - r * .3f, y - r * .35f, r * 1.25f,
                intArrayOf(0xFFFFF6F0.toInt(), plus(col, 60), col, scale(col, .45f)), floatArrayOf(0f, .25f, .7f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, fill); fill.shader = null
            highlight(c, x, y, r)
        }
    }

    private fun highlight(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = alpha(Color.WHITE, .55f)
        c.save(); c.rotate(-28.6f, x - r * .32f, y - r * .42f)
        rect.set(x - r * .7f, y - r * .62f, x + r * .06f, y - r * .22f); c.drawOval(rect, fill)
        c.restore()
    }

    /** A segment of a bar meter: dark in the static pass, lit with a glow in the live one. */
    private fun barSeg(c: Canvas, x: Float, y: Float, w: Float, h: Float, col: Int, on: Boolean, still: Boolean, flat: Boolean = false) {
        if (still) {
            if (!flat) { fill.color = alpha(Color.BLACK, .85f); round(c, x, y, w, h, 1.5f, fill) }
            fill.shader = if (flat) null else LinearGradient(0f, y, 0f, y + h, scale(col, .3f), scale(col, .14f), Shader.TileMode.CLAMP)
            if (flat) fill.color = alpha(col, .14f)
            round(c, x + 1f, y + 1f, w - 2f, h - 2f, 1f, fill); fill.shader = null
        } else if (on) {
            fill.color = col; fill.maskFilter = BlurMaskFilter(4.5f, BlurMaskFilter.Blur.NORMAL)
            round(c, x, y, w, h, 2f, fill); fill.maskFilter = null
            fill.shader = if (flat) null else LinearGradient(0f, y, 0f, y + h,
                intArrayOf(plus(col, 90), col, scale(col, .6f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            if (flat) fill.color = col
            round(c, x + 1f, y + 1f, w - 2f, h - 2f, 1f, fill); fill.shader = null
        }
    }

    private val SEGMENTS = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd",
        '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g", ' ' to "",
    )

    /** One seven-segment digit; unlit segments in the static pass (the "ghost 8"), lit ones in the live pass. */
    private fun seg7(c: Canvas, x: Float, y: Float, w: Float, h: Float, ch: Char, on: Int, off: Int, skew: Float, still: Boolean) {
        val lit = SEGMENTS[ch] ?: ""
        val t = w * .17f; val hh = h / 2f
        val shapes = mapOf(
            'a' to floatArrayOf(t * .6f, 0f, w - t * .6f, 0f, w - t * 1.5f, t, t * 1.5f, t),
            'd' to floatArrayOf(t * 1.5f, h - t, w - t * 1.5f, h - t, w - t * .6f, h, t * .6f, h),
            'g' to floatArrayOf(t * .9f, hh, t * 1.7f, hh - t / 2, w - t * 1.7f, hh - t / 2, w - t * .9f, hh, w - t * 1.7f, hh + t / 2, t * 1.7f, hh + t / 2),
            'f' to floatArrayOf(0f, t * .6f, t, t * 1.5f, t, hh - t * .6f, t * .5f, hh - .2f * t, 0f, hh - t * .3f),
            'b' to floatArrayOf(w, t * .6f, w, hh - t * .3f, w - t * .5f, hh - .2f * t, w - t, hh - t * .6f, w - t, t * 1.5f),
            'e' to floatArrayOf(0f, hh + t * .3f, t * .5f, hh + t * .2f, t, hh + t * .6f, t, h - t * 1.5f, 0f, h - t * .6f),
            'c' to floatArrayOf(w, hh + t * .3f, w, h - t * .6f, w - t, h - t * 1.5f, w - t, hh + t * .6f, w - t * .5f, hh + t * .2f),
        )
        for ((k, pts) in shapes) {
            // Static pass: every segment dim (the "ghost 8"); live pass: the lit ones on top.
            if (!still && k !in lit) continue
            path.reset()
            var i = 0
            while (i < pts.size) {
                val px = pts[i]; val py = pts[i + 1]
                val X = x + px + skew * (1f - py / h); val Y = y + py
                if (i == 0) path.moveTo(X, Y) else path.lineTo(X, Y)
                i += 2
            }
            path.close()
            if (still) { fill.color = off; c.drawPath(path, fill) }
            else {
                fill.color = on; fill.maskFilter = BlurMaskFilter(7f, BlurMaskFilter.Blur.NORMAL); c.drawPath(path, fill)
                fill.maskFilter = null; c.drawPath(path, fill)
            }
        }
    }

    private fun value(m: DashboardRenderer.Metric?): String = m?.display ?: m?.value?.let {
        if (m.unit == "V") String.format(Locale.getDefault(), "%.1f", it) else String.format(Locale.US, "%.0f", it)
    } ?: "—"

    private fun valueUnit(m: DashboardRenderer.Metric): String {
        val v = value(m)
        return if (m.unit.isEmpty() || v == "—") v else "$v ${m.unit}"
    }

    // ───────────────────────── LED

    private fun led(f: Frame) {
        val c = f.c; val day = f.day
        val num = if (day) 0xFF1B1C1F.toInt() else 0xFFE9E6E1.toInt()
        val numSh = if (day) alpha(Color.WHITE, .8f) else alpha(Color.BLACK, .8f)
        val red = if (day) 0xFFD4190D.toInt() else 0xFFFF4A3A.toInt()
        val sub = if (day) 0xFF4D5055.toInt() else 0xFF9C9A96.toInt()
        val ledCol = if (f.accent != 0) f.accent else RED
        if (f.still && f.drawBg) {
            if (day) vertical(c, 0xFFDFE1E4.toInt(), 0xFFC6C9CD.toInt(), 0xFFA7AAAF.toInt())
            else vertical(c, 0xFF2A2C30.toInt(), 0xFF1A1B1E.toInt(), 0xFF0E0F11.toInt())
            brushed(c, 5000, .05f)
            var x = 78f
            while (x < W) {
                fill.shader = LinearGradient(x - 4f, 0f, x + 4f, 0f,
                    intArrayOf(0, if (day) alpha(Color.BLACK, .22f) else alpha(Color.BLACK, .55f), if (day) alpha(Color.WHITE, .7f) else alpha(Color.WHITE, .1f), 0),
                    floatArrayOf(0f, .35f, .55f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(x - 4f, 10f, x + 4f, H - 10f, fill); x += 58f
            }
            fill.shader = null
            vignette(c, if (day) .28f else .75f)
            bezel(c, if (day) intArrayOf(0xFFFAFAFB.toInt(), 0xFFA3A6AB.toInt(), 0xFF6E7176.toInt(), 0xFFD2D4D8.toInt())
                else intArrayOf(0xFF77797E.toInt(), 0xFF3A3C40.toInt(), 0xFF1B1C1E.toInt(), 0xFF55575B.toInt()))
            screws(c)
        }
        // arc of LEDs: RPM, or (without RPM) the speed
        val sec = DashSkinMath.secondary(DashboardMode.Design.LED, f.d.noObd, "rpm" in f.a, f.plan)
        val arc = DashSkinMath.arcScale(sec, f.speedMax, f.rpmMax)
        val cx = 400f; val cy = 250f; val rx = 352f; val ry = 196f; val a0 = 192f; val a1 = 348f; val n = 44
        fun px(t: Float, dr: Float) = cx + (rx + dr) * cos(Math.toRadians((a0 + (a1 - a0) * t).toDouble())).toFloat()
        fun py(t: Float, dr: Float) = cy + (ry + dr * .7f) * sin(Math.toRadians((a0 + (a1 - a0) * t).toDouble())).toFloat()
        if (f.still) {
            if (arc.redFrom < 1f) {
                stroke.strokeWidth = 7f; stroke.color = alpha(0xFFC81414.toInt(), .55f)
                path.reset(); var t = arc.redFrom; path.moveTo(px(t, -17f), py(t, -17f))
                while (t <= 1.0001f) { path.lineTo(px(t, -17f), py(t, -17f)); t += .005f }
                c.drawPath(path, stroke)
            }
            for ((t, label) in arc.labels) {
                val hot = t >= arc.redFrom
                txt(c, label, px(t, 28f), py(t, 28f), 15f, if (hot) red else num, MONO, shadow = numSh)
                stroke.color = if (hot) red else if (day) 0xFF35373B.toInt() else 0xFFBDBAB5.toInt(); stroke.strokeWidth = 2f
                c.drawLine(px(t, -12f), py(t, -12f), px(t, -22f), py(t, -22f), stroke)
            }
            txt(c, arc.caption, 400f, 83f, 11f, sub, MONO)
        }
        val frac = DashSkinMath.secondaryFraction(sec, f.d, f.a, arc)
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val hot = t >= arc.redFrom
            led(c, px(t, 0f), py(t, 0f), if (hot) 8f else 7f, frac > 0f && t <= frac, if (hot) 0xFFFF1428.toInt() else ledCol, f.still)
        }
        // speed read-out
        val bx = 290f; val by = 106f; val bw = 220f; val bh = 104f
        if (f.still) {
            fill.color = 0xFF2B2D31.toInt(); fill.setShadowLayer(7f, 0f, 4f, alpha(Color.BLACK, .9f))
            round(c, bx - 8f, by - 8f, bw + 16f, bh + 16f, 8f, fill); fill.clearShadowLayer()
            stroke.strokeWidth = 3f
            stroke.shader = LinearGradient(0f, by - 8f, 0f, by + bh + 8f, intArrayOf(0xFF86888D.toInt(), 0xFF2E3034.toInt(), 0xFF6A6C70.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            round(c, bx - 8f, by - 8f, bw + 16f, bh + 16f, 8f, stroke); stroke.shader = null
            fill.shader = LinearGradient(0f, by, 0f, by + bh, 0xFF1C0605.toInt(), 0xFF0B0202.toInt(), Shader.TileMode.CLAMP)
            round(c, bx, by, bw, bh, 4f, fill); fill.shader = null
        }
        val digits = DashSkinMath.speedDigits(f.a["speed"]?.value)
        for (i in 0..2) seg7(c, bx + 16f + i * 66f, by + 12f, 54f, bh - 24f, digits[i], 0xFFFF2A1C.toInt(), alpha(0xFF780E0A.toInt(), .32f), 0f, f.still)
        if (!f.still) {   // glass reflection over the lit segments
            c.save(); rect.set(bx, by, bx + bw, by + bh); c.clipRect(rect)
            fill.shader = LinearGradient(bx, by, bx + bw * .6f, by + bh, intArrayOf(alpha(Color.WHITE, .13f), alpha(Color.WHITE, .04f), 0), floatArrayOf(0f, .45f, .46f), Shader.TileMode.CLAMP)
            c.drawRect(rect, fill); fill.shader = null; c.restore()
        }
        val ux = bx + bw + 20f; val uy = by + 22f; val uw = 70f; val uh = 34f
        if (f.still) {
            fill.color = 0xFF55575B.toInt(); round(c, ux - 3f, uy - 3f, uw + 6f, uh + 6f, 4f, fill)
            fill.color = 0xFF071A0B.toInt(); round(c, ux, uy, uw, uh, 3f, fill)
            txt(c, "KM/H", ux + uw / 2f, uy + uh / 2f, 20f, 0xFF46F06A.toInt(), Typeface.create(MONO, Typeface.BOLD_ITALIC), glow = 4f)
        }
        sourceChips(f, ux, uy + uh + 14f)
        // bar meters
        val bw2 = 206f
        ledBar(f, 50f, 258f, bw2, f.a["throttle"], ledCol, num, numSh, red)
        ledBar(f, 50f, 304f, bw2, f.a["coolant"], ledCol, num, numSh, red)
        ledBar(f, 458f, 258f, bw2, f.a["load"], ledCol, num, numSh, red)
        ledBar(f, 458f, 304f, bw2, f.a["volt"], ledCol, num, numSh, red)
    }

    /** OBD / GPS lamps: which source the speed comes from. */
    private fun sourceChips(f: Frame, x: Float, y: Float) {
        if (f.a["speed"] == null) return
        val gps = f.d.speedFromGps || f.d.noObd
        for ((i, label) in listOf("OBD", "GPS").withIndex()) {
            val on = (i == 1) == gps
            val bx = x + i * 37f
            if (f.still) { fill.color = 0xFF141516.toInt(); round(c = f.c, x = bx, y = y, w = 32f, h = 16f, r = 3f, p = fill)
                txt(f.c, label, bx + 16f, y + 8f, 11f, 0xFF3C3E40.toInt(), MONO) }
            else if (on) { fill.color = 0xFF0F3A18.toInt(); round(f.c, bx, y, 32f, 16f, 3f, fill)
                txt(f.c, label, bx + 16f, y + 8f, 11f, 0xFF5CFF7C.toInt(), MONO) }
        }
    }

    private fun ledBar(f: Frame, x: Float, y: Float, bw: Float, m: DashboardRenderer.Metric?, main: Int, num: Int, sh: Int, red: Int) {
        m ?: return
        val c = f.c
        val label = m.title.uppercase(Locale.getDefault())
        if (f.still) txt(c, label, x, y - 14f, fitSize(label, MONO, 14f, bw - 80f), num, MONO, Paint.Align.LEFT, sh)
        else txt(c, valueUnit(m), x + bw, y - 14f, 14f, red, MONO, Paint.Align.RIGHT)
        val range = DashSkinMath.barRange(m.id, m.max) ?: return   // a reading without a scale (clock, trip…): text only
        val n = 14; val w = bw / n
        val fr = DashSkinMath.barFraction(m.value, range)
        for (i in 0 until n) {
            val t = (i + .5f) / n
            val col = when (DashSkinMath.zone(m.id, range.first + t * (range.second - range.first))) {
                DashSkinMath.Zone.ALERT -> RED; DashSkinMath.Zone.WARN -> AMBER; DashSkinMath.Zone.OK -> GREEN; DashSkinMath.Zone.PLAIN -> main
            }
            barSeg(c, x + i * w, y - 3f, w - 3f, 11f, col, m.value != null && t <= fr, f.still)
        }
    }

    // ───────────────────────── ANALOG

    private class Dial(
        val min: Double, val max: Double, val major: Double, val minor: Double,
        val label: (Double) -> String, val zones: List<Triple<Double, Double, Int>> = emptyList(),
        val redFrom: Double? = null, val title: String = "", val labelR: Float = .58f, val labelSize: Float = 0f,
    )

    private fun analog(f: Frame) {
        val c = f.c; val day = f.day
        if (f.still && f.drawBg) {
            if (day) { vertical(c, 0xFFCFD2D6.toInt(), 0xFFBCBFC3.toInt(), 0xFFA9ACB1.toInt()); brushed(c, 5000, .06f); vignette(c, .25f) }
            else {
                vertical(c, 0xFF1B1C1F.toInt(), 0xFF121315.toInt(), 0xFF08090A.toInt())
                fill.color = alpha(Color.WHITE, .035f)
                var y = 0f
                while (y < H) { var x = if (((y / 4f).toInt() % 2) == 0) 0f else 4f; while (x < W) { c.drawRect(x, y, x + 4f, y + 2f, fill); x += 8f }; y += 4f }
                vignette(c, .7f)
            }
            screws(c)
        }
        val speed = f.a["speed"]
        if (speed != null) {
            val step = if (f.speedMax > 160) 40.0 else 20.0
            dial(f, 215f, 178f, 120f, 12f, Dial(0.0, f.speedMax.toDouble(), step, step / 4, { it.toInt().toString() }, title = "KM/H"),
                speed.value, speed.value?.let { String.format(Locale.US, "%.0f", it) } ?: "—")
        }
        val sec = DashSkinMath.secondary(DashboardMode.Design.ANALOG, f.d.noObd, "rpm" in f.a, f.plan)
        if (sec == DashSkinMath.Secondary.CLOCK) chromeClock(f, 585f, 178f, 120f, 12f)
        else f.a["rpm"]?.let { rpm ->
            val k = f.rpmMax / 1000.0
            val red = DashboardMode.RPM_RED / 1000.0
            dial(f, 585f, 178f, 120f, 12f, Dial(0.0, k, if (k > 10) 2.0 else 1.0, .5, { it.toInt().toString() },
                zones = listOf(Triple(red, k, 0xFFD4190D.toInt())), redFrom = red, title = "×1000 r/min"),
                rpm.value?.div(1000.0), rpm.value?.let { String.format(Locale.US, "%.1f", it / 1000.0) } ?: "—")
        }
        smallDial(f, 400f, 104f, f.a["coolant"])
        smallDial(f, 400f, 240f, f.a["volt"])
        if (f.still && speed != null) txt(c, if (f.d.speedFromGps || f.d.noObd) "GPS" else "OBD", 400f, 172f, 10f,
            if (day) 0xFF2B6B35.toInt() else 0xFF46E06A.toInt(), MONO)
    }

    private fun smallDial(f: Frame, cx: Float, cy: Float, m: DashboardRenderer.Metric?) {
        m ?: return
        val dial = when (m.id) {
            "coolant" -> Dial(40.0, 130.0, 15.0, 5.0, { if (it == 40.0) "C" else if (it == 130.0) "H" else "" },
                zones = listOf(Triple(DashboardMode.COOLANT_AMBER, DashboardMode.COOLANT_RED, 0xFFE8A00C.toInt()),
                    Triple(DashboardMode.COOLANT_RED, 130.0, 0xFFD4190D.toInt())), title = "°C", labelR = .62f, labelSize = 11f)
            "volt" -> Dial(10.0, 16.0, 1.0, .5, { if (it.toInt() % 2 == 0) it.toInt().toString() else "" },
                zones = listOf(Triple(10.0, 12.0, 0xFFD4190D.toInt())), title = "V", labelR = .62f, labelSize = 10f)
            else -> if (m.max > 0.0) Dial(0.0, m.max, m.max / 4, m.max / 20, { "" }, title = m.unit, labelR = .62f, labelSize = 10f) else null
        }
        if (dial != null) dial(f, cx, cy, 36f, 8f, dial, m.value, null)
        else {   // a reading with no scale (clock, trip…): a small window in a chrome ring
            dialShell(f, cx, cy, 36f, 8f)
            if (f.still) txt(f.c, m.title.uppercase(Locale.getDefault()), cx, cy + 18f, fitSize(m.title.uppercase(Locale.getDefault()), SANS, 8.5f, 56f),
                if (f.day) 0xFF55575C.toInt() else 0xFF9A9CA1.toInt(), SANS)
            else txt(f.c, value(m), cx, cy - 2f, fitSize(value(m), SANS, 15f, 58f), if (f.day) 0xFF24262A.toInt() else 0xFFECE9E3.toInt(), SANS)
        }
    }

    /** Shadow, chrome rim and face of a dial (static pass). */
    private fun dialShell(f: Frame, cx: Float, cy: Float, r: Float, bz: Float) {
        if (!f.still) return
        val c = f.c
        fill.color = Color.BLACK; fill.setShadowLayer(r * .11f, 0f, r * .07f, alpha(Color.BLACK, .75f))
        c.drawCircle(cx, cy, r + bz, fill); fill.clearShadowLayer()
        val sweep = SweepGradient(cx, cy, intArrayOf(0xFFF4F5F7.toInt(), 0xFF6C6F75.toInt(), 0xFFD7D9DC.toInt(), 0xFF4A4C51.toInt(),
            0xFFF1F2F4.toInt(), 0xFF6C6F75.toInt(), 0xFFD7D9DC.toInt(), 0xFF4A4C51.toInt(), 0xFFF4F5F7.toInt()),
            floatArrayOf(0f, .12f, .25f, .37f, .5f, .62f, .75f, .87f, 1f))
        matrix.reset(); matrix.setRotate(135f, cx, cy); sweep.setLocalMatrix(matrix)
        fill.shader = sweep; c.drawCircle(cx, cy, r + bz, fill); fill.shader = null
        stroke.strokeWidth = 1.5f; stroke.color = alpha(Color.BLACK, .55f); c.drawCircle(cx, cy, r + bz - .8f, stroke)
        stroke.strokeWidth = 1f; stroke.color = alpha(Color.WHITE, .5f); c.drawCircle(cx, cy, r + bz * .55f, stroke)
        fill.shader = if (f.day) RadialGradient(cx - r * .25f, cy - r * .3f, r * 1.3f,
            intArrayOf(0xFFFCF8EC.toInt(), 0xFFEBE5D1.toInt(), 0xFFC9C1A6.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        else RadialGradient(cx - r * .25f, cy - r * .3f, r * 1.3f,
            intArrayOf(0xFF2D2E32.toInt(), 0xFF0F1012.toInt(), 0xFF040405.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null
        // edge shade
        fill.shader = RadialGradient(cx, cy, r, intArrayOf(0, 0, alpha(Color.BLACK, .35f)), floatArrayOf(0f, .78f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill); fill.shader = null
    }

    /** Glass reflection, drawn live so it lies over the needle. */
    private fun dialGlass(f: Frame, cx: Float, cy: Float, r: Float) {
        if (f.still) return
        val c = f.c
        c.save(); path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); c.clipPath(path)
        fill.shader = LinearGradient(cx - r * .6f, cy - r, cx + r * .3f, cy, alpha(Color.WHITE, if (f.day) .45f else .18f), 0, Shader.TileMode.CLAMP)
        c.rotate(-16f, cx - r * .12f, cy - r * .52f)
        rect.set(cx - r * .92f, cy - r * .94f, cx + r * .68f, cy - r * .1f); c.drawOval(rect, fill)
        fill.shader = null; c.restore()
    }

    private fun dial(f: Frame, cx: Float, cy: Float, r: Float, bz: Float, o: Dial, v: Double?, read: String?) {
        val c = f.c
        val ink = if (f.day) 0xFF24262A.toInt() else 0xFFECE9E3.toInt()
        fun ang(x: Double) = (135.0 + 270.0 * ((x - o.min) / (o.max - o.min)).coerceIn(0.0, 1.0))
        dialShell(f, cx, cy, r, bz)
        if (f.still) {
            for ((a, b, col) in o.zones) {
                stroke.color = col; stroke.strokeWidth = r * .07f
                rect.set(cx - r * .9f, cy - r * .9f, cx + r * .9f, cy + r * .9f)
                c.drawArc(rect, ang(a).toFloat(), (ang(b) - ang(a)).toFloat(), false, stroke)
            }
            val ticks = Math.round((o.max - o.min) / o.minor).toInt()
            for (i in 0..ticks) {
                val x = o.min + i * o.minor
                val majorIdx = (x - o.min) / o.major
                val maj = kotlin.math.abs(majorIdx - Math.round(majorIdx)) < 1e-6
                val a = Math.toRadians(ang(x))
                val r1 = r * .83f; val r2 = if (maj) r * .7f else r * .76f
                stroke.color = ink; stroke.strokeWidth = if (maj) r * .025f + 1f else 1f
                c.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), stroke)
                val label = if (maj) o.label(x) else ""
                if (label.isNotEmpty()) {
                    val rl = r * o.labelR
                    txt(c, label, cx + rl * cos(a).toFloat(), cy + rl * sin(a).toFloat(), if (o.labelSize > 0f) o.labelSize else r * .13f,
                        if (o.redFrom != null && x >= o.redFrom) 0xFFD4190D.toInt() else ink, SANS)
                }
            }
            if (o.title.isNotEmpty()) txt(c, o.title, cx, cy + r * (if (read != null) .7f else .55f), r * .1f + 2f,
                if (f.day) 0xFF55575C.toInt() else 0xFF9A9CA1.toInt(), SANS)
            if (read != null) {
                val w = r * .5f; val h = r * .2f; val x = cx - w / 2f; val y = cy + r * .3f
                fill.color = if (f.day) 0xFF7A7C80.toInt() else 0xFF3D3F43.toInt(); round(c, x - 2f, y - 2f, w + 4f, h + 4f, 3f, fill)
                fill.color = 0xFF07090A.toInt(); round(c, x, y, w, h, 2f, fill)
            }
        } else {
            if (read != null) txt(c, read, cx, cy + r * .4f, r * .16f, 0xFFFF8A2A.toInt(), MONO, glow = 3.5f)
            if (v != null) needle(c, cx, cy, r, ang(v).toFloat())
            hub(c, cx, cy, r)
        }
        dialGlass(f, cx, cy, r)
    }

    private fun needle(c: Canvas, cx: Float, cy: Float, r: Float, deg: Float) {
        c.save(); c.translate(cx, cy); c.rotate(deg)
        val l = r * .86f
        path.reset(); path.moveTo(l, 0f); path.lineTo(r * .02f, -r * .032f); path.lineTo(-r * .2f, -r * .026f)
        path.lineTo(-r * .2f, r * .026f); path.lineTo(r * .02f, r * .032f); path.close()
        fill.shader = LinearGradient(0f, -4f, 0f, 4f, intArrayOf(0xFFFF9A50.toInt(), 0xFFFF5A14.toInt(), 0xFFB83808.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        fill.setShadowLayer(3.5f, 3f, 4f, alpha(Color.BLACK, .6f))
        c.drawPath(path, fill); fill.clearShadowLayer(); fill.shader = null
        c.restore()
    }

    private fun hub(c: Canvas, cx: Float, cy: Float, r: Float) {
        fill.shader = RadialGradient(cx - r * .03f, cy - r * .03f, r * .1f, intArrayOf(0xFFF3F4F6.toInt(), 0xFF7B7E84.toInt(), 0xFF2A2B2E.toInt()),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * .095f, fill); fill.shader = null
    }

    /** Without RPM the right-hand dial is a clock, in the same chrome. */
    private fun chromeClock(f: Frame, cx: Float, cy: Float, r: Float, bz: Float) {
        val c = f.c
        val ink = if (f.day) 0xFF24262A.toInt() else 0xFFECE9E3.toInt()
        dialShell(f, cx, cy, r, bz)
        if (f.still) {
            for (i in 0 until 60) {
                val maj = i % 5 == 0
                val a = Math.toRadians(i * 6.0 - 90.0)
                val r1 = r * .86f; val r2 = if (maj) r * .73f else r * .8f
                stroke.color = ink; stroke.strokeWidth = if (maj) 4f else 1f
                c.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), stroke)
            }
            for (h in listOf(12, 3, 6, 9)) {
                val a = Math.toRadians((h % 12) * 30.0 - 90.0)
                txt(c, h.toString(), cx + r * .58f * cos(a).toFloat(), cy + r * .58f * sin(a).toFloat(), r * .17f, ink, SANS)
            }
        } else {
            val ms = f.d.clockMs
            if (ms != null) {
                val hands = DashPhoneMath.hands(ms, TimeZone.getDefault().getOffset(ms))
                hand(c, cx, cy, r * .5f, hands.hour - 90f, 7f, ink)
                hand(c, cx, cy, r * .76f, hands.minute - 90f, 4.5f, ink)
                stroke.color = 0xFFFF5A14.toInt(); stroke.strokeWidth = 2f
                val a = Math.toRadians(hands.second - 90.0)
                c.drawLine(cx - r * .16f * cos(a).toFloat(), cy - r * .16f * sin(a).toFloat(), cx + r * .84f * cos(a).toFloat(), cy + r * .84f * sin(a).toFloat(), stroke)
            }
            hub(c, cx, cy, r)
        }
        dialGlass(f, cx, cy, r)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, len: Float, deg: Float, w: Float, col: Int) {
        stroke.color = col; stroke.strokeWidth = w; stroke.strokeCap = Paint.Cap.ROUND
        stroke.setShadowLayer(2.5f, 2f, 3f, alpha(Color.BLACK, .5f))
        val a = Math.toRadians(deg.toDouble())
        c.drawLine(cx, cy, cx + len * cos(a).toFloat(), cy + len * sin(a).toFloat(), stroke)
        stroke.clearShadowLayer(); stroke.strokeCap = Paint.Cap.BUTT
    }

    // ───────────────────────── VFD

    private fun vfd(f: Frame) {
        val c = f.c; val day = f.day
        val on = if (day) 0xFF1A56C8.toInt() else 0xFF5CFFE2.toInt()
        val dim = if (day) alpha(0xFF1A56C8.toInt(), .12f) else alpha(0xFF28786E.toInt(), .3f)
        val label = if (day) 0xFF33507A.toInt() else 0xFF3FCDB7.toInt()
        val hot = if (day) 0xFFD4190D.toInt() else 0xFFFF5A3C.toInt()
        val warn = if (day) 0xFFC98A00.toInt() else 0xFFFFC23C.toInt()
        if (f.still && f.drawBg) {
            if (day) vertical(c, 0xFFD8DBDE.toInt(), 0xFFC8CCD0.toInt(), 0xFFB9BDC2.toInt())
            else vertical(c, 0xFF06100F.toInt(), 0xFF040C0B.toInt(), 0xFF020707.toInt())
            if (!day) {
                fill.shader = RadialGradient(400f, 200f, 520f, alpha(0xFF28C8B4.toInt(), .1f), 0, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W, H, fill); fill.shader = null
            }
            stroke.strokeWidth = 1f; stroke.color = if (day) alpha(Color.BLACK, .04f) else alpha(0xFF3CFFDC.toInt(), .05f)
            var x = 0f; while (x < W) { c.drawLine(x, 0f, x, H, stroke); x += 3f }
            var y = 0f; while (y < H) { c.drawLine(0f, y, W, y, stroke); y += 3f }
        }
        val sec = DashSkinMath.secondary(DashboardMode.Design.VFD, f.d.noObd, "rpm" in f.a, f.plan)
        val arc = DashSkinMath.arcScale(sec, f.speedMax, f.rpmMax)
        val n = 50
        if (f.still) {
            txt(c, arc.caption.uppercase(Locale.US), 40f, 26f, 11f, label, MONO, Paint.Align.LEFT)
            for ((t, s) in arc.labels) txt(c, s, 40f + t * ((n - 1) * 14.4f) + 5f, 80f, 12f, if (t >= arc.redFrom) hot else label, MONO)
        }
        val frac = DashSkinMath.secondaryFraction(sec, f.d, f.a, arc)
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val red = t >= arc.redFrom
            barSeg(c, 40f + i * 14.4f, 38f, 10f, 26f, if (red) hot else on, frac > 0f && t <= frac, f.still, flat = true)
        }
        val digits = DashSkinMath.speedDigits(f.a["speed"]?.value)
        if (f.a["speed"] != null) {
            for (i in 0..2) seg7(c, 274f + i * 86f, 112f, 70f, 150f, digits[i], on, dim, 12f, f.still)
            if (f.still) txt(c, "KM/H", 400f, 288f, 18f, on, MONO)
        }
        vfdBar(f, 40f, 138f, f.a["throttle"], on, dim, label, hot, warn)
        vfdBar(f, 40f, 206f, f.a["load"], on, dim, label, hot, warn)
        vfdBar(f, 560f, 138f, f.a["coolant"], on, dim, label, hot, warn)
        vfdBar(f, 560f, 206f, f.a["volt"], on, dim, label, hot, warn)
        if (f.a["speed"] != null) {
            val gps = f.d.speedFromGps || f.d.noObd
            if (f.still) { txt(c, "OBD", 590f, 262f, 11f, dim, MONO, Paint.Align.LEFT); txt(c, "GPS", 640f, 262f, 11f, dim, MONO, Paint.Align.LEFT) }
            else txt(c, if (gps) "GPS" else "OBD", if (gps) 640f else 590f, 262f, 11f, on, MONO, Paint.Align.LEFT, glow = 3f)
        }
        if (f.still && f.drawBg) {
            fill.shader = LinearGradient(0f, 0f, W * .6f, H, intArrayOf(alpha(Color.WHITE, .06f), alpha(Color.WHITE, .015f), 0), floatArrayOf(0f, .4f, .41f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, W, H, fill); fill.shader = null
            vignette(c, if (day) .2f else .55f)
            stroke.strokeWidth = 5f; stroke.color = if (day) 0xFF8D9297.toInt() else 0xFF1C2523.toInt()
            rect.set(3f, 3f, W - 3f, H - 3f); c.drawRoundRect(rect, 12f, 12f, stroke)
            stroke.strokeWidth = 1f; stroke.color = if (day) alpha(Color.WHITE, .5f) else alpha(0xFF78FFEB.toInt(), .18f)
            rect.set(7f, 7f, W - 7f, H - 7f); c.drawRoundRect(rect, 9f, 9f, stroke)
        }
    }

    private fun vfdBar(f: Frame, x: Float, y: Float, m: DashboardRenderer.Metric?, on: Int, dim: Int, label: Int, hot: Int, warn: Int) {
        m ?: return
        val c = f.c
        val title = m.title.uppercase(Locale.getDefault())
        if (f.still) txt(c, title, x, y, fitSize(title, MONO, 13f, 120f), label, MONO, Paint.Align.LEFT)
        else txt(c, valueUnit(m), x + 196f, y, 13f, on, MONO, Paint.Align.RIGHT, glow = 2.5f)
        val range = DashSkinMath.barRange(m.id, m.max) ?: return
        val fr = DashSkinMath.barFraction(m.value, range)
        for (i in 0 until 14) {
            val t = (i + .5f) / 14
            val col = when (DashSkinMath.zone(m.id, range.first + t * (range.second - range.first))) {
                DashSkinMath.Zone.ALERT -> hot; DashSkinMath.Zone.WARN -> warn; else -> on
            }
            if (f.still) { fill.color = dim; round(c, x + i * 14.3f, y + 10f, 11f, 14f, 1f, fill) }
            else barSeg(c, x + i * 14.3f, y + 10f, 11f, 14f, col, m.value != null && t <= fr, false, flat = true)
        }
    }

    // ───────────────────────── MINIMAL

    private fun minimal(f: Frame) {
        val c = f.c; val day = f.day
        val ink = if (day) 0xFF111214.toInt() else 0xFFF6F6F4.toInt()
        val muted = if (day) 0xFF6E7076.toInt() else 0xFF8D8F95.toInt()
        val track = if (day) 0xFFD9DADD.toInt() else 0xFF1D1E22.toInt()
        val acc = if (f.accent != 0) f.accent else if (day) 0xFFF08A00.toInt() else 0xFFFFA21A.toInt()
        val hot = if (day) 0xFFD4190D.toInt() else 0xFFFF5A44.toInt()
        if (f.still && f.drawBg) {
            fill.color = if (day) 0xFFF4F4F1.toInt() else 0xFF040405.toInt(); c.drawRect(0f, 0f, W, H, fill)
            vignette(c, if (day) .12f else .5f)
        }
        val sec = DashSkinMath.secondary(DashboardMode.Design.MINIMAL, f.d.noObd, "rpm" in f.a, f.plan)
        val arc = DashSkinMath.arcScale(sec, f.speedMax, f.rpmMax)
        val cx = 400f; val cy = 272f; val rx = 362f; val ry = 228f; val a0 = 200f; val a1 = 340f
        fun px(t: Float, dr: Float) = cx + (rx + dr) * cos(Math.toRadians((a0 + (a1 - a0) * t).toDouble())).toFloat()
        fun py(t: Float, dr: Float) = cy + (ry + dr * .6f) * sin(Math.toRadians((a0 + (a1 - a0) * t).toDouble())).toFloat()
        fun arcPath(t0: Float, t1: Float) {
            path.reset(); var t = t0; path.moveTo(px(t, 0f), py(t, 0f))
            while (t < t1) { t = minOf(t1, t + .004f); path.lineTo(px(t, 0f), py(t, 0f)) }
        }
        stroke.strokeWidth = 6f; stroke.strokeCap = Paint.Cap.ROUND
        if (f.still) {
            stroke.color = track; arcPath(0f, 1f); c.drawPath(path, stroke)
            if (arc.redFrom < 1f) { stroke.color = if (day) alpha(hot, .35f) else 0xFF5A1A14.toInt(); arcPath(arc.redFrom, 1f); c.drawPath(path, stroke) }
            stroke.strokeCap = Paint.Cap.BUTT; stroke.strokeWidth = 1.6f
            for ((t, s) in arc.labels) {
                val red = t >= arc.redFrom
                txt(c, s, px(t, 24f), py(t, 24f), 14f, if (red) hot else muted, SANS)
                stroke.color = if (red) hot else muted; c.drawLine(px(t, -8f), py(t, -8f), px(t, -16f), py(t, -16f), stroke)
            }
            txt(c, arc.caption, 400f, 84f, 12f, muted, SANS_LIGHT)
            if (f.a["speed"] != null) txt(c, "km/h", 400f, 282f, 20f, acc, SANS)
        } else {
            val frac = DashSkinMath.secondaryFraction(sec, f.d, f.a, arc)
            if (frac > 0f) {
                stroke.color = acc; stroke.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
                arcPath(0f, minOf(frac, arc.redFrom)); c.drawPath(path, stroke); stroke.maskFilter = null; c.drawPath(path, stroke)
                if (frac > arc.redFrom) { stroke.color = hot; arcPath(arc.redFrom, frac); c.drawPath(path, stroke) }
            }
            f.a["speed"]?.let { s ->
                val v = s.value?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
                txt(c, v, 400f, 190f, 200f, ink, SANS, glow = if (day) 0f else 11f)
            }
        }
        stroke.strokeCap = Paint.Cap.BUTT
        // one line of readings
        val chips = listOf("coolant", "volt", "throttle", "load").mapNotNull { f.a[it] }
        if (chips.isEmpty()) return
        val slot = 560f / chips.size
        for ((i, m) in chips.withIndex()) {
            val x = 120f + i * slot
            val title = m.title.uppercase(Locale.getDefault())
            val ts = fitSize(title, SANS_LIGHT, 11f, slot * .45f)
            text.typeface = SANS_LIGHT; text.textSize = ts
            val tw = text.measureText(title)
            if (f.still) txt(c, title, x, 312f, ts, muted, SANS_LIGHT, Paint.Align.LEFT)
            else {
                val col = when (DashSkinMath.zone(m.id, m.value ?: 0.0)) {
                    DashSkinMath.Zone.ALERT -> hot; DashSkinMath.Zone.WARN -> if (day) 0xFFC98A00.toInt() else 0xFFFFC23C.toInt(); else -> ink
                }
                val v = valueUnit(m)
                txt(c, v, x + tw + 6f, 312f, fitSize(v, SANS, 15f, slot - tw - 14f), if (m.value == null) muted else col, SANS, Paint.Align.LEFT)
            }
        }
    }

    // ───────────────────────── COCKPIT

    private fun cockpit(f: Frame) {
        val c = f.c; val day = f.day
        val g = if (day) 0xFFFFB020.toInt() else 0xFF3DFF7A.toInt()
        val dg = if (day) 0xFF5E4410.toInt() else 0xFF14582E.toInt()
        val soft = if (day) 0xFFD08F1A.toInt() else 0xFF2FBF5E.toInt()
        val amb = if (day) 0xFFFFE070.toInt() else 0xFFFFC23C.toInt()
        val rd = 0xFFFF4A3A.toInt()
        if (f.still && f.drawBg) {
            fill.color = if (day) 0xFF080603.toInt() else 0xFF020804.toInt(); c.drawRect(0f, 0f, W, H, fill)
            fill.shader = RadialGradient(400f, 200f, 520f, alpha(g, .07f), 0, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, W, H, fill); fill.shader = null
            fill.color = alpha(Color.BLACK, .25f); var y = 0f; while (y < H) { c.drawRect(0f, y, W, y + 1f, fill); y += 3f }
            stroke.color = dg; stroke.strokeWidth = 1.4f
            for ((x, y0) in listOf(24f to 24f, W - 24f to 24f, 24f to H - 24f, W - 24f to H - 24f)) {
                val sx = if (x < W / 2) 1f else -1f; val sy = if (y0 < H / 2) 1f else -1f
                path.reset(); path.moveTo(x, y0 + sy * 22f); path.lineTo(x, y0); path.lineTo(x + sx * 22f, y0); c.drawPath(path, stroke)
            }
        }
        // left tape: RPM, or the speed / altitude without OBD
        val sec = DashSkinMath.secondary(DashboardMode.Design.COCKPIT, f.d.noObd, "rpm" in f.a, f.plan)
        if (sec == DashSkinMath.Secondary.ALTITUDE) altitudeTape(f, g, dg)
        else {
            val arc = DashSkinMath.arcScale(sec, f.speedMax, f.rpmMax)
            val y0 = 310f; val y1 = 56f
            fun yy(t: Float) = y0 - (y0 - y1) * t
            if (f.still) {
                txt(c, arc.caption.uppercase(Locale.US), 92f, 30f, 12f, g, MONO)
                if (arc.redFrom < 1f) { fill.color = alpha(rd, .28f); c.drawRect(104f, yy(1f), 116f, yy(arc.redFrom), fill) }
                stroke.color = g; stroke.strokeWidth = 1.6f; c.drawLine(110f, y0, 110f, y1, stroke)
                for ((t, s) in arc.labels) {
                    val red = t >= arc.redFrom
                    stroke.color = if (red) rd else g; c.drawLine(110f, yy(t), 126f, yy(t), stroke)
                    txt(c, s, 92f, yy(t), 13f, if (red) rd else g, MONO, Paint.Align.RIGHT)
                }
            } else {
                val frac = DashSkinMath.secondaryFraction(sec, f.d, f.a, arc)
                val y = yy(frac)
                fill.color = g; fill.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
                c.drawRect(106f, y, 110f, y0, fill); fill.maskFilter = null; c.drawRect(106f, y, 110f, y0, fill)
                path.reset(); path.moveTo(144f, y); path.lineTo(162f, y - 8f); path.lineTo(162f, y + 8f); path.close(); c.drawPath(path, fill)
                val readout = DashSkinMath.secondaryText(sec, f.d, f.a)
                txt(c, readout, 170f, y, 14f, g, MONO, Paint.Align.LEFT, glow = 3f)
            }
        }
        // speed box
        val speed = f.a["speed"]
        if (speed != null) {
            if (f.still) {
                stroke.color = g; stroke.strokeWidth = 2f; round(c, 268f, 86f, 264f, 140f, 6f, stroke)
                stroke.color = alpha(g, .35f); stroke.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
                round(c, 268f, 86f, 264f, 140f, 6f, stroke); stroke.maskFilter = null
                txt(c, "KM/H", 516f, 102f, 13f, soft, MONO, Paint.Align.RIGHT)
            } else {
                txt(c, if (f.d.speedFromGps || f.d.noObd) "GPS" else "OBD", 284f, 102f, 13f, soft, MONO, Paint.Align.LEFT)
                val v = speed.value?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
                txt(c, v, 400f, 168f, 112f, g, MONO, glow = 7f)
            }
        }
        hbar(f, 262f, f.a["throttle"], g, dg, soft, amb, rd)
        hbar(f, 300f, f.a["load"], g, dg, soft, amb, rd)
        vbar(f, 600f, f.a["coolant"], g, dg, soft, amb, rd)
        vbar(f, 660f, f.a["volt"], g, dg, soft, amb, rd)
        vbar(f, 720f, f.a["intake"], g, dg, soft, amb, rd)
        if (!f.still) {
            val (s, col) = when {
                f.d.obdLive -> "OBD LIVE" to g
                f.d.noObd -> "GPS" to g
                else -> "OBD —" to amb
            }
            txt(c, s, 690f, 268f, 11f, col, MONO)
        }
    }

    /** Aircraft-style moving altitude tape: the scale slides, the pointer stays in the middle. */
    private fun altitudeTape(f: Frame, g: Int, dg: Int) {
        val c = f.c
        val y0 = 310f; val y1 = 56f; val mid = (y0 + y1) / 2f
        if (f.still) {
            txt(c, "ALT  M", 92f, 30f, 12f, g, MONO)
            stroke.color = g; stroke.strokeWidth = 1.6f; c.drawLine(110f, y0, 110f, y1, stroke)
            return
        }
        val alt = f.d.altitudeM ?: return
        c.save(); c.clipRect(40f, y1, 200f, y0)
        val pxPerM = 1f
        var m = (Math.floor((alt - 140) / 20.0) * 20).toInt()
        while (m <= alt + 140) {
            val y = mid - ((m - alt) * pxPerM).toFloat()
            val maj = m % 100 == 0
            stroke.color = g; stroke.strokeWidth = 1.6f; c.drawLine(110f, y, 110f + if (maj) 16f else 9f, y, stroke)
            if (maj) txt(c, m.toString(), 98f, y, 13f, g, MONO, Paint.Align.RIGHT)
            m += 20
        }
        c.restore()
        fill.color = Color.BLACK; round(c, 128f, mid - 13f, 70f, 26f, 3f, fill)
        stroke.color = g; stroke.strokeWidth = 1.6f; round(c, 128f, mid - 13f, 70f, 26f, 3f, stroke)
        path.reset(); path.moveTo(118f, mid); path.lineTo(128f, mid - 7f); path.lineTo(128f, mid + 7f); path.close()
        fill.color = g; c.drawPath(path, fill)
        txt(c, String.format(Locale.US, "%.0f", alt), 163f, mid, 15f, g, MONO, glow = 3f)
        stroke.color = dg
    }

    private fun hbar(f: Frame, y: Float, m: DashboardRenderer.Metric?, g: Int, dg: Int, soft: Int, amb: Int, rd: Int) {
        m ?: return
        val c = f.c
        val title = m.title.uppercase(Locale.getDefault())
        val range = DashSkinMath.barRange(m.id, m.max)
        if (f.still) {
            txt(c, title, 268f, y - 11f, fitSize(title, MONO, 12f, 170f), soft, MONO, Paint.Align.LEFT)
            if (range != null) {
                stroke.color = dg; stroke.strokeWidth = 1f; c.drawRect(268f, y, 532f, y + 10f, stroke)
            }
            return
        }
        txt(c, valueUnit(m), 532f, y - 11f, 12f, g, MONO, Paint.Align.RIGHT)
        range ?: return
        val fr = DashSkinMath.barFraction(m.value, range)
        val col = zoneColor(m, g, amb, rd)
        fill.color = col; fill.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        c.drawRect(269f, y + 1f, 269f + 262f * fr, y + 9f, fill); fill.maskFilter = null
        c.drawRect(269f, y + 1f, 269f + 262f * fr, y + 9f, fill)
        stroke.color = alpha(Color.BLACK, .6f); stroke.strokeWidth = 1f
        for (i in 1 until 10) c.drawLine(268f + i * 26.4f, y, 268f + i * 26.4f, y + 10f, stroke)
    }

    private fun vbar(f: Frame, x: Float, m: DashboardRenderer.Metric?, g: Int, dg: Int, soft: Int, amb: Int, rd: Int) {
        m ?: return
        val c = f.c
        val title = DashSkinMath.short(m.id, m.title)
        val range = DashSkinMath.barRange(m.id, m.max)
        if (f.still) {
            txt(c, title, x, 40f, fitSize(title, MONO, 11f, 56f), soft, MONO)
            if (range != null) { stroke.color = dg; stroke.strokeWidth = 1.4f; c.drawRect(x - 12f, 56f, x + 12f, 226f, stroke) }
            return
        }
        val col = zoneColor(m, g, amb, rd)
        val v = value(m) + if (m.unit == "°C") "°" else ""
        txt(c, v, x, if (range != null) 242f else 140f, fitSize(v, MONO, 13f, 56f), col, MONO, glow = 2f)
        range ?: return
        val fr = DashSkinMath.barFraction(m.value, range)
        fill.color = col; fill.maskFilter = BlurMaskFilter(4.5f, BlurMaskFilter.Blur.NORMAL)
        c.drawRect(x - 11f, 56f + 168f * (1f - fr) + 1f, x + 11f, 225f, fill); fill.maskFilter = null
        c.drawRect(x - 11f, 56f + 168f * (1f - fr) + 1f, x + 11f, 225f, fill)
        stroke.color = alpha(Color.BLACK, .55f); stroke.strokeWidth = 1f
        for (i in 1 until 10) c.drawLine(x - 12f, 56f + i * 17f, x + 12f, 56f + i * 17f, stroke)
    }

    private fun zoneColor(m: DashboardRenderer.Metric, g: Int, amb: Int, rd: Int): Int = when (DashSkinMath.zone(m.id, m.value ?: 0.0)) {
        DashSkinMath.Zone.ALERT -> rd; DashSkinMath.Zone.WARN -> amb; else -> g
    }
}

/** Pure decisions of [DashSkins] (unit tested). */
internal object DashSkinMath {
    /** What sits where the RPM would: the RPM itself, the speed, a clock (Analog) or an altitude tape (Cockpit). */
    enum class Secondary { RPM, SPEED, CLOCK, ALTITUDE }

    fun secondary(design: DashboardMode.Design, noObd: Boolean, rpmVisible: Boolean, plan: DashFallback.Plan?): Secondary = when {
        !noObd && rpmVisible -> Secondary.RPM
        design == DashboardMode.Design.ANALOG -> if (plan?.clockDial == true) Secondary.CLOCK else Secondary.SPEED
        design == DashboardMode.Design.COCKPIT && plan?.altTape == true -> Secondary.ALTITUDE
        else -> Secondary.SPEED
    }

    /** The scale of the RPM arc/bar/tape, or of the speed drawn in its place. */
    data class ArcScale(val max: Double, val redFrom: Float, val labels: List<Pair<Float, String>>, val caption: String)

    fun arcScale(sec: Secondary, speedMax: Int, rpmMax: Int): ArcScale = if (sec == Secondary.RPM) {
        val k = rpmMax / 1000
        val every = if (k <= 10) 1 else 2
        ArcScale(rpmMax.toDouble(), DashDesignMath.redFraction(DashboardMode.RPM_RED, rpmMax.toDouble()),
            (0..k step every).map { it.toFloat() / k to it.toString() }, "×1000 r/min")
    } else {
        val every = if (speedMax > 160) 40 else 20
        ArcScale(speedMax.toDouble(), 1f, (0..speedMax step every).map { it.toFloat() / speedMax to it.toString() }, "km/h")
    }

    fun secondaryFraction(sec: Secondary, d: DashboardMode.Data, a: Map<String, DashboardRenderer.Metric>, s: ArcScale): Float = when (sec) {
        Secondary.RPM -> DashDesignMath.fraction(a["rpm"]?.value, s.max)
        Secondary.SPEED -> DashDesignMath.fraction(a["speed"]?.value ?: d.speedKmh, s.max)
        else -> 0f
    }

    fun secondaryText(sec: Secondary, d: DashboardMode.Data, a: Map<String, DashboardRenderer.Metric>): String = when (sec) {
        Secondary.RPM -> a["rpm"]?.value?.let { String.format(Locale.US, "%.1f", it / 1000.0) } ?: "—"
        Secondary.SPEED -> (a["speed"]?.value ?: d.speedKmh)?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
        else -> ""
    }

    /** Three characters for the seven-segment read-out: "097", or "---" with no speed. */
    fun speedDigits(kmh: Double?): String =
        if (kmh == null || kmh.isNaN()) "---" else String.format(Locale.US, "%03d", Math.round(kmh.coerceIn(0.0, 999.0)).toInt())

    /** Value range of a bar, or null for a reading with no scale (shown as text only). */
    fun barRange(id: String, max: Double): Pair<Double, Double>? = when (id) {
        "coolant" -> 40.0 to 130.0
        "volt" -> 11.0 to 15.0
        else -> if (max > 0.0) 0.0 to max else null
    }

    fun barFraction(v: Double?, range: Pair<Double, Double>): Float =
        if (v == null || range.second <= range.first) 0f else ((v - range.first) / (range.second - range.first)).toFloat().coerceIn(0f, 1f)

    enum class Zone { OK, WARN, ALERT, PLAIN }

    /** Colour zone of [v] for reading [id]: coolant and battery levels have meaning, the rest use the design colour. */
    fun zone(id: String, v: Double): Zone = when (id) {
        "coolant" -> when { v >= DashboardMode.COOLANT_RED -> Zone.ALERT; v >= DashboardMode.COOLANT_AMBER -> Zone.WARN; else -> Zone.OK }
        "volt" -> when { v < 12.0 -> Zone.ALERT; v < 12.6 -> Zone.WARN; else -> Zone.OK }
        "battery" -> when { v < 15.0 -> Zone.ALERT; v < 30.0 -> Zone.WARN; else -> Zone.OK }
        else -> Zone.PLAIN
    }

    /** Four-letter titles for Cockpit's narrow columns. */
    fun short(id: String, title: String): String = when (id) {
        "coolant" -> "COOL"; "volt" -> "BATT"; "intake" -> "INTK"; "load" -> "LOAD"; "throttle" -> "THR"
        else -> title.uppercase(Locale.getDefault()).take(5)
    }
}
