// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The six dashboard faces from the approved reference sheet, drawn at a fixed 800×416
 * design size and scaled into the motorcycle's usable screen area.
 */
internal object DashboardRenderer {
    private const val W = 800f
    private const val H = 416f

    private data class Palette(
        val bg: Int, val fg: Int, val muted: Int, val track: Int,
        val tile: Int, val edge: Int, val accent: Int, val accent2: Int, val red: Int,
    )

    private val sportNight = Palette(
        0xFF17231B.toInt(), 0xFFF5F6F0.toInt(), 0xFFAAB4AA.toInt(),
        0xFF344237.toInt(), 0xFF202D24.toInt(), 0xFF526257.toInt(),
        0xFFE2B344.toInt(), 0xFFFFD66F.toInt(), 0xFFE9473C.toInt(),
    )
    private val sportDay = Palette(
        0xFFF4F4EE.toInt(), 0xFF171A19.toInt(), 0xFF59635D.toInt(),
        0xFFC8CDC4.toInt(), 0xFFFBFCF8.toInt(), 0xFFAEB5AA.toInt(),
        0xFFD59C24.toInt(), 0xFFF4C44E.toInt(), 0xFFC9332D.toInt(),
    )
    private val racingNight = Palette(
        0xFF090B0F.toInt(), 0xFFF7F8FA.toInt(), 0xFFA7ADB6.toInt(),
        0xFF30343D.toInt(), 0xFF15191F.toInt(), 0xFF50545D.toInt(),
        0xFFF2263B.toInt(), 0xFFFF5360.toInt(), 0xFFFF2638.toInt(),
    )
    private val racingDay = Palette(
        0xFFF1F2F5.toInt(), 0xFF15171C.toInt(), 0xFF5D626B.toInt(),
        0xFFC9CDD4.toInt(), 0xFFFFFFFF.toInt(), 0xFFAEB3BC.toInt(),
        0xFFD7192D.toInt(), 0xFFFA4050.toInt(), 0xFFC8202D.toInt(),
    )
    private val touringNight = Palette(
        0xFF0A1917.toInt(), 0xFFF0FBF7.toInt(), 0xFF9BB5AC.toInt(),
        0xFF29433C.toInt(), 0xFF122824.toInt(), 0xFF45665D.toInt(),
        0xFF32D0A8.toInt(), 0xFF7CE8C9.toInt(), 0xFFE9473C.toInt(),
    )
    private val touringDay = Palette(
        0xFFF0F7F3.toInt(), 0xFF132722.toInt(), 0xFF58746B.toInt(),
        0xFFC5D8D0.toInt(), 0xFFFCFFFD.toInt(), 0xFFA9C0B6.toInt(),
        0xFF159A7A.toInt(), 0xFF50C7A8.toInt(), 0xFFC93630.toInt(),
    )

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val bold = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val medium = Typeface.create("sans-serif", Typeface.BOLD)
    private val plain = Typeface.create("sans-serif", Typeface.NORMAL)

    private data class Metric(
        val id: String, val title: String, val value: Double?, val unit: String,
        val max: Double = 100.0,
    )

    private fun metrics(d: DashboardMode.Data) = listOf(
        Metric("speed", "SPEED", d.speedKmh, "km/h", 240.0),
        Metric("rpm", "RPM", d.rpm, "rpm", 14_000.0),
        Metric("throttle", "THROTTLE", d.relativeThrottle, "%", 100.0),
        Metric("coolant", "COOLANT", d.coolant, "°C", 130.0),
        Metric("volt", "VOLTAGE", d.volt, "V", 16.0),
        Metric("load", "ENGINE LOAD", d.load, "%", 100.0),
        Metric("intake", "INTAKE", d.intake, "°C", 80.0),
        Metric("baro", "MANIFOLD", d.baro, "kPa", 120.0),
        Metric("absolute", "ABS LOAD", d.absoluteLoad, "%", 260.0),
    )

    private fun value(m: Metric?): String = m?.value?.let {
        if (m.unit == "V") String.format(Locale.US, "%.1f", it)
        else String.format(Locale.US, "%.0f", it)
    } ?: "—"

    fun render(
        ctx: Context, w: Int, h: Int, d: DashboardMode.Data, light: Boolean,
        design: DashboardMode.Design, visible: Set<String>,
    ): Bitmap {
        var target = bitmap
        if (target == null || target.width != w || target.height != h) {
            target?.recycle()
            target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap = target
            canvas = Canvas(target)
        }
        val c = canvas!!
        val p = when (design) {
            DashboardMode.Design.SPORT -> if (light) sportDay else sportNight
            DashboardMode.Design.RACING -> if (light) racingDay else racingNight
            DashboardMode.Design.TOURING -> if (light) touringDay else touringNight
        }
        c.drawColor(p.bg)
        val scale = minOf(w / W, h / H)
        c.save()
        c.translate((w - W * scale) / 2f, (h - H * scale) / 2f)
        c.scale(scale, scale)
        drawSurface(c, p, design)
        val all = metrics(d).filter { it.id in visible }.associateBy { it.id }
        when (design) {
            DashboardMode.Design.SPORT -> drawSport(c, d, p, all)
            DashboardMode.Design.RACING -> drawRacing(ctx, c, d, p, all)
            DashboardMode.Design.TOURING -> drawTouring(ctx, c, d, p, all)
        }
        if (!d.obdLive) label(c, ctx.getString(R.string.dash_no_obd), 18f, 405f, 13f, p.muted, plain)
        c.restore()
        return target
    }

    /** Low contrast texture and the fine edge treatment from the approved screen designs. */
    private fun drawSurface(c: Canvas, p: Palette, design: DashboardMode.Design) {
        fill.color = p.bg
        c.drawRect(0f, 0f, W, H, fill)
        stroke.color = p.edge and 0x28FFFFFF
        stroke.strokeWidth = 1f
        val startY = if (design == DashboardMode.Design.RACING) 0f else 286f
        var y = startY
        while (y < H) {
            var x = -24f
            while (x < W + 20f) {
                val path = Path().apply {
                    moveTo(x + 7f, y)
                    lineTo(x + 14f, y + 5f)
                    lineTo(x + 14f, y + 13f)
                    lineTo(x + 7f, y + 18f)
                    lineTo(x, y + 13f)
                    lineTo(x, y + 5f)
                    close()
                }
                c.drawPath(path, stroke)
                x += 18f
            }
            y += 16f
        }
        fill.color = p.accent
        c.drawRect(0f, H - 3f, W, H, fill)
        fill.color = p.bg
        c.drawRect(0f, H - 2f, W, H, fill)
    }

    private fun drawShiftLeds(c: Canvas, rpm: Double?, p: Palette, y: Float = 8f) {
        val count = 10
        val width = 35f
        val gap = 7f
        val x0 = (W - (count * width + (count - 1) * gap)) / 2f
        for (i in 0 until count) {
            val threshold = (i + 1) * DashboardMode.RPM_MAX / count
            val active = rpm != null && rpm >= threshold
            val color = when {
                !active -> p.track
                i >= 8 -> p.red
                i >= 5 -> p.accent2
                else -> p.accent
            }
            fill.color = color
            box.set(x0 + i * (width + gap), y, x0 + i * (width + gap) + width, y + 8f)
            c.drawRoundRect(box, 4f, 4f, fill)
        }
    }

    /** SPORT: one large speed dial, two smaller instruments and a two-column data stack. */
    private fun drawSport(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        drawShiftLeds(c, d.rpm, p, 7f)
        drawDial(c, a["speed"], 168f, 212f, 148f, p, if (d.speedFromGps) "GPS" else null, true)
        drawDial(c, a["rpm"], 501f, 107f, 59f, p, null, false)
        drawDial(c, a["throttle"], 671f, 107f, 59f, p, null, false)
        drawMetricGrid(
            c, listOf("coolant", "volt", "load", "intake", "baro", "absolute").mapNotNull(a::get),
            360f, 174f, 2, 200f, 60f, 7f, p, CardStyle.SPORT,
        )
    }

    /** RACING: red segmented tach bar, angular speed plate, throttle gauge and five telemetry cards. */
    private fun drawRacing(ctx: Context, c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        label(c, "RPM", 38f, 29f, 12f, p.fg, medium)
        drawRacingTach(c, a["rpm"], p, 38f, 38f, 724f, 55f)
        val speed = a["speed"]
        val x = 36f; val y = 112f; val w = 366f; val h = 143f
        val plate = Path().apply {
            moveTo(x + 17f, y)
            lineTo(x + w - 18f, y)
            lineTo(x + w, y + 17f)
            lineTo(x + w - 13f, y + h)
            lineTo(x, y + h)
            lineTo(x + 12f, y + h - 17f)
            close()
        }
        fill.color = p.tile
        c.drawPath(plate, fill)
        stroke.color = p.accent
        stroke.strokeWidth = 2f
        c.drawPath(plate, stroke)
        stroke.color = p.edge
        stroke.strokeWidth = 1f
        val inner = Path().apply {
            moveTo(x + 25f, y + 8f); lineTo(x + w - 26f, y + 8f)
            lineTo(x + w - 8f, y + 24f); lineTo(x + w - 20f, y + h - 9f)
            lineTo(x + 10f, y + h - 9f); lineTo(x + 20f, y + h - 24f); close()
        }
        c.drawPath(inner, stroke)
        label(c, value(speed), 62f, 211f, 74f, p.fg, bold)
        label(c, "km/h" + if (d.speedFromGps) " · GPS" else "", 67f, 238f, 16f, p.muted, plain)
        drawCheckers(c, 326f, 215f, p)
        drawRacingThrottle(c, a["throttle"], 632f, 184f, 73f, p)
        drawMetricGrid(
            c, listOf("coolant", "volt", "load", "intake", "baro").mapNotNull(a::get),
            35f, 270f, 3, 238f, 59f, 7f, p, CardStyle.RACING,
        )
    }

    /** TOURING: arced speedometer, central vertical tach and six long range readouts. */
    private fun drawTouring(ctx: Context, c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        drawShiftLeds(c, d.rpm, p, 7f)
        drawTouringSpeed(c, a["speed"], 183f, 177f, 139f, p, if (d.speedFromGps) "GPS" else null)
        drawTouringRpm(c, a["rpm"], p, 375f, 63f, 52f, 209f)
        drawDial(c, a["throttle"], 684f, 128f, 68f, p, null, false)
        drawMetricGrid(
            c, listOf("coolant", "volt", "load", "intake", "baro", "absolute").mapNotNull(a::get),
            34f, 294f, 3, 239f, 49f, 6f, p, CardStyle.TOURING,
        )
    }

    private fun drawDial(
        c: Canvas, m: Metric?, cx: Float, cy: Float, radius: Float, p: Palette,
        hint: String?, large: Boolean,
    ) {
        if (m == null) return
        fill.color = p.tile
        c.drawCircle(cx, cy, radius, fill)
        stroke.color = p.edge
        stroke.strokeWidth = if (large) 3f else 2f
        c.drawCircle(cx, cy, radius - 1.5f, stroke)
        c.drawCircle(cx, cy, radius - 7f, stroke)
        box.set(cx - radius + 9f, cy - radius + 9f, cx + radius - 9f, cy + radius - 9f)
        stroke.color = p.track
        stroke.strokeWidth = if (large) 8f else 5f
        c.drawArc(box, 135f, 270f, false, stroke)
        val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
        stroke.color = if (m.id == "rpm" && fraction >= .88f) p.red else p.accent
        c.drawArc(box, 135f, 270f * fraction, false, stroke)
        val divisions = if (large) 48 else 20
        for (i in 0..divisions) {
            val angle = Math.toRadians(135.0 + 270.0 * i / divisions)
            val major = if (large) i % 4 == 0 else i % 4 == 0
            val inner = radius - if (major) (if (large) 27f else 16f) else (if (large) 17f else 10f)
            val outer = radius - 5f
            stroke.color = if (major) p.fg else p.muted
            stroke.strokeWidth = if (major) 1.7f else 1f
            c.drawLine(
                cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, stroke,
            )
            if (large && major) {
                val tickValue = m.max * i / divisions
                val tx = cx + cos(angle).toFloat() * (radius - 40f)
                val ty = cy + sin(angle).toFloat() * (radius - 40f) + 4f
                center(c, String.format(Locale.US, "%.0f", tickValue), tx, ty, 9f, p.muted, plain)
            }
        }

        if (!large && m.id == "rpm") {
            for (i in 0..7) {
                val angle = Math.toRadians(135.0 + 270.0 * i / 7.0)
                center(c, (i * 2).toString(),
                    cx + cos(angle).toFloat() * (radius - 18f),
                    cy + sin(angle).toFloat() * (radius - 18f) + 3f,
                    7f, p.muted, plain)
            }
        }
        val angle = Math.toRadians(135.0 + 270.0 * fraction)
        stroke.color = p.red
        stroke.strokeWidth = if (large) 3f else 2f
        c.drawLine(cx, cy, cx + cos(angle).toFloat() * radius * .6f, cy + sin(angle).toFloat() * radius * .6f, stroke)
        fill.color = p.accent2
        c.drawCircle(cx, cy, if (large) 5f else 3.5f, fill)
        val labelSize = if (large) 13f else 8f
        val titleY = cy - radius * .28f
        if (!(large && m.id == "speed")) center(c, m.title, cx, titleY, labelSize, p.muted, medium)
        center(c, value(m), cx, cy + if (large) 15f else 8f, if (large) 43f else 21f, p.fg, bold)
        val unit = (hint?.let { "$it · " } ?: "") + m.unit
        center(c, unit, cx, cy + radius * .47f, if (large) 12f else 8f, p.muted, plain)
    }

    private fun drawRacingTach(c: Canvas, m: Metric?, p: Palette, x: Float, y: Float, w: Float, h: Float) {
        fill.color = p.tile
        box.set(x, y, x + w, y + h)
        c.drawRoundRect(box, 9f, 9f, fill)
        stroke.color = p.accent
        stroke.strokeWidth = 1.5f
        c.drawRoundRect(box, 9f, 9f, stroke)
        val count = 14
        val gap = 3f
        val segmentWidth = (w - gap * (count - 1) - 20f) / count
        val start = x + 10f
        val top = y + 10f
        val bottom = y + h - 9f
        val fraction = ((m?.value ?: 0.0) / (m?.max ?: DashboardMode.RPM_MAX)).toFloat().coerceIn(0f, 1f)
        for (i in 0 until count) {
            val sx = start + i * (segmentWidth + gap)
            val path = Path().apply {
                moveTo(sx + 7f, top); lineTo(sx + segmentWidth, top)
                lineTo(sx + segmentWidth - 7f, bottom); lineTo(sx, bottom); close()
            }
            fill.color = if ((i + 1f) / count <= fraction) {
                if (i >= count - 3) p.red else if (i >= count - 6) p.accent2 else p.fg
            } else p.track
            c.drawPath(path, fill)
            stroke.color = p.edge
            stroke.strokeWidth = .7f
            c.drawPath(path, stroke)
        }
        center(c, value(m), x + w / 2f, y + h - 12f, 23f, p.fg, bold)
        for (i in 0..7) {
            val vx = start + (w - 20f) * i / 7f
            center(c, (i * 2).toString(), vx, y + h + 13f, 9f, p.muted, plain)
        }
    }

    private fun drawCheckers(c: Canvas, x: Float, y: Float, p: Palette) {
        val size = 9f
        for (row in 0..3) for (col in 0..5) {
            if ((row + col) % 2 == 0) {
                fill.color = p.accent and 0xBBFFFFFF.toInt()
                c.drawRect(x + col * size, y + row * size, x + (col + 1) * size, y + (row + 1) * size, fill)
            }
        }
    }

    private fun drawRacingThrottle(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette) {
        if (m == null) return
        fill.color = p.tile
        c.drawCircle(cx, cy, r, fill)
        stroke.color = p.accent
        stroke.strokeWidth = 3f
        c.drawCircle(cx, cy, r - 2f, stroke)
        val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
        box.set(cx - r + 7f, cy - r + 7f, cx + r - 7f, cy + r - 7f)
        stroke.color = p.track
        stroke.strokeWidth = 7f
        c.drawArc(box, 135f, 270f, false, stroke)
        stroke.color = p.accent
        c.drawArc(box, 135f, 270f * fraction, false, stroke)
        center(c, "THROTTLE", cx, cy - 14f, 10f, p.muted, medium)
        center(c, value(m), cx, cy + 12f, 28f, p.fg, bold)
        center(c, "%", cx, cy + 29f, 10f, p.muted, plain)
    }

    private fun drawTouringSpeed(c: Canvas, m: Metric?, cx: Float, cy: Float, radius: Float, p: Palette, hint: String?) {
        if (m == null) return
        fill.color = p.tile
        c.drawCircle(cx, cy, radius - 6f, fill)
        box.set(cx - radius, cy - radius, cx + radius, cy + radius)
        stroke.color = p.track
        stroke.strokeWidth = 13f
        c.drawArc(box, 135f, 270f, false, stroke)
        val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
        stroke.color = p.accent
        c.drawArc(box, 135f, 270f * fraction, false, stroke)
        for (i in 0..36) {
            val angle = Math.toRadians(135.0 + 270.0 * i / 36)
            val major = i % 3 == 0
            val inner = radius - if (major) 27f else 20f
            val outer = radius - 7f
            stroke.color = if (major) p.fg else p.muted
            stroke.strokeWidth = if (major) 1.8f else 1f
            c.drawLine(
                cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, stroke,
            )
            if (major) {
                center(c, (m.max * i / 36f).toInt().toString(), cx + cos(angle).toFloat() * (radius - 43f),
                    cy + sin(angle).toFloat() * (radius - 43f) + 3f, 8f, p.muted, plain)
            }
        }
        drawMountains(c, cx, cy + 8f, p)
        center(c, value(m), cx, cy + 14f, 52f, p.fg, bold)
        center(c, "km/h" + if (hint != null) " · $hint" else "", cx, cy + 41f, 11f, p.muted, plain)
    }


    /** Fourteen calibrated segments with the same 0–14 ×1000 scale as the Touring reference. */
    private fun drawTouringRpm(c: Canvas, m: Metric?, p: Palette, x: Float, y: Float, width: Float, height: Float) {
        label(c, "RPM", x, y - 23f, 11f, p.muted, medium)
        center(c, value(m), x + width / 2f, y - 4f, 19f, p.fg, bold)
        val count = 14
        val gap = 3f
        val segmentHeight = (height - gap * (count - 1)) / count
        val fraction = ((m?.value ?: 0.0) / (m?.max ?: 14_000.0)).toFloat().coerceIn(0f, 1f)
        for (i in 0 until count) {
            val bottom = y + height - i * (segmentHeight + gap)
            val top = bottom - segmentHeight
            fill.color = if ((i + 1f) / count <= fraction) {
                if (i >= 11) p.red else p.accent
            } else p.track
            box.set(x, top, x + width, bottom)
            c.drawRoundRect(box, 4f, 4f, fill)
        }
        for (i in 0..7) {
            val scaleValue = i * 2
            val yy = y + height - height * (scaleValue / 14f)
            rightLabel(c, scaleValue.toString(), x + width + 21f, yy + 3f, 8f, p.muted, plain)
        }
    }

    private fun drawMountains(c: Canvas, cx: Float, cy: Float, p: Palette) {
        val path = Path().apply {
            moveTo(cx - 94f, cy + 67f)
            lineTo(cx - 56f, cy + 22f)
            lineTo(cx - 30f, cy + 47f)
            lineTo(cx + 8f, cy + 6f)
            lineTo(cx + 38f, cy + 38f)
            lineTo(cx + 63f, cy + 18f)
            lineTo(cx + 96f, cy + 67f)
            close()
        }
        fill.color = (p.accent and 0x00FFFFFF) or 0x28000000
        c.drawPath(path, fill)
    }

    private enum class CardStyle { SPORT, RACING, TOURING }

    private fun drawMetricGrid(
        c: Canvas, list: List<Metric>, x: Float, y: Float, columns: Int,
        width: Float, height: Float, gap: Float, p: Palette, style: CardStyle,
    ) {
        list.forEachIndexed { index, m ->
            val col = index % columns
            val row = index / columns
            drawMetricCard(c, m, x + col * (width + gap), y + row * (height + gap), width, height, p, style)
        }
    }

    private fun drawMetricCard(
        c: Canvas, m: Metric, x: Float, y: Float, w: Float, h: Float, p: Palette, style: CardStyle,
    ) {
        box.set(x, y, x + w, y + h)
        fill.color = p.tile
        c.drawRoundRect(box, 9f, 9f, fill)
        stroke.color = p.edge
        stroke.strokeWidth = 1.2f
        c.drawRoundRect(box, 9f, 9f, stroke)
        stroke.color = p.accent2 and 0x88FFFFFF.toInt()
        stroke.strokeWidth = 1f
        box.set(x + 1f, y + 1f, x + w - 1f, y + h - 1f)
        c.drawRoundRect(box, 8f, 8f, stroke)
        when (style) {
            CardStyle.SPORT -> {
                drawMetricIcon(c, m.id, x + 20f, y + h / 2f, 10f, p.accent)
                label(c, m.title, x + 38f, y + 19f, 9f, p.muted, medium)
                label(c, value(m), x + 38f, y + h - 12f, 21f, p.fg, bold)
                rightLabel(c, m.unit, x + w - 10f, y + h - 12f, 9f, p.muted, plain)
            }
            CardStyle.RACING -> {
                drawMetricIcon(c, m.id, x + 17f, y + h / 2f, 8f, p.accent)
                label(c, m.title, x + 31f, y + 16f, 8f, p.muted, medium)
                label(c, value(m), x + 31f, y + h - 8f, 17f, p.fg, bold)
                rightLabel(c, m.unit, x + w - 9f, y + h - 8f, 8f, p.muted, plain)
                drawProgress(c, m, x + w - 55f, y + h - 5f, 42f, p)
            }
            CardStyle.TOURING -> {
                drawMetricIcon(c, m.id, x + 16f, y + h / 2f, 8f, p.accent)
                label(c, m.title, x + 29f, y + 15f, 8f, p.muted, medium)
                label(c, value(m), x + 29f, y + h - 8f, 17f, p.fg, bold)
                rightLabel(c, m.unit, x + w - 8f, y + h - 8f, 8f, p.muted, plain)
                drawProgress(c, m, x + w - 50f, y + h - 5f, 38f, p)
            }
        }
    }

    private fun drawProgress(c: Canvas, m: Metric, x: Float, y: Float, width: Float, p: Palette) {
        fill.color = p.track
        box.set(x, y, x + width, y + 2f)
        c.drawRoundRect(box, 1f, 1f, fill)
        m.value?.let {
            fill.color = if (m.id == "coolant" && it >= DashboardMode.COOLANT_AMBER) p.red else p.accent
            box.set(x, y, x + width * (it / m.max).toFloat().coerceIn(0f, 1f), y + 2f)
            c.drawRoundRect(box, 1f, 1f, fill)
        }
    }

    private fun drawMetricIcon(c: Canvas, id: String, cx: Float, cy: Float, r: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.7f
        fill.color = color
        when (id) {
            "coolant" -> {
                c.drawLine(cx - 2f, cy - r, cx - 2f, cy + 3f, stroke)
                c.drawLine(cx + 2f, cy - r, cx + 2f, cy + 3f, stroke)
                c.drawArc(cx - 6f, cy - 1f, cx + 6f, cy + 10f, 0f, 180f, false, stroke)
                c.drawCircle(cx, cy + 5f, 2.5f, fill)
            }
            "volt" -> {
                box.set(cx - r, cy - r * .65f, cx + r, cy + r * .65f)
                c.drawRoundRect(box, 2f, 2f, stroke)
                c.drawRect(cx + r, cy - 2f, cx + r + 3f, cy + 2f, fill)
                c.drawLine(cx - 3f, cy, cx + 3f, cy, stroke)
            }
            "load" -> {
                box.set(cx - r, cy - r * .62f, cx + r, cy + r * .62f)
                c.drawRoundRect(box, 2f, 2f, stroke)
                c.drawLine(cx - r - 2f, cy - 2f, cx - r - 2f, cy + 3f, stroke)
                c.drawLine(cx + r + 2f, cy - 2f, cx + r + 2f, cy + 3f, stroke)
                c.drawCircle(cx, cy, 2.5f, stroke)
            }
            "intake" -> for (i in -1..1) {
                val yy = cy + i * 4f
                c.drawLine(cx - r, yy + 1f, cx - 2f, yy - 1f, stroke)
                c.drawLine(cx - 2f, yy - 1f, cx + 2f, yy + 1f, stroke)
                c.drawLine(cx + 2f, yy + 1f, cx + r, yy - 1f, stroke)
            }
            else -> {
                for (i in -1..1) {
                    val xx = cx + i * 5f
                    c.drawLine(xx, cy - r, xx, cy + r, stroke)
                }
                c.drawLine(cx - r, cy + r, cx + r, cy + r, stroke)
                c.drawLine(cx - r, cy - r, cx + r, cy - r, stroke)
            }
        }
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface) {
        text.typeface = face
        text.textSize = size
        text.color = color
        c.drawText(s, x, y, text)
    }

    private fun rightLabel(c: Canvas, s: String, right: Float, y: Float, size: Float, color: Int, face: Typeface) {
        text.typeface = face
        text.textSize = size
        text.color = color
        c.drawText(s, right - text.measureText(s), y, text)
    }

    private fun center(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface) {
        text.typeface = face
        text.textSize = size
        text.color = color
        c.drawText(s, x - text.measureText(s) / 2f, y, text)
    }
}
