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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The six dashboard faces from the approved reference sheet (dashboard_style_previews.jpg:
 * SPORT / RACING / TOURING, night on top, day below), drawn at a fixed 800×416 design size
 * and scaled into the motorcycle's usable screen area.
 *
 * Every face has the reference's header (logo, clock, status icons), shift lights, the style's
 * gauges and five telemetry cards. The speed / temperature / fuel strip under the reference faces
 * is the Zontes dash's own fixed strip, not part of the app, so it is not drawn here.
 */
internal object DashboardRenderer {
    private const val W = 800f
    private const val H = 416f

    private data class Palette(
        val bg: Int, val fg: Int, val muted: Int, val track: Int,
        val tile: Int, val edge: Int, val accent: Int, val accent2: Int, val red: Int,
    )

    private val sportNight = Palette(
        0xFF0B0C0E.toInt(), 0xFFF4F4F4.toInt(), 0xFF9A9EA6.toInt(),
        0xFF2C2F35.toInt(), 0xFF16181C.toInt(), 0xFF3A3D44.toInt(),
        0xFFFF8A1E.toInt(), 0xFFFFB648.toInt(), 0xFFE83A2E.toInt(),
    )
    private val sportDay = Palette(
        0xFFF2F2EF.toInt(), 0xFF15171A.toInt(), 0xFF60656D.toInt(),
        0xFFD5D7DA.toInt(), 0xFFFFFFFF.toInt(), 0xFFC4C7CC.toInt(),
        0xFFF0860F.toInt(), 0xFFF6B13E.toInt(), 0xFFD9372B.toInt(),
    )
    private val racingNight = Palette(
        0xFF08090B.toInt(), 0xFFF7F7F8.toInt(), 0xFFA0A4AB.toInt(),
        0xFF2A2D33.toInt(), 0xFF121418.toInt(), 0xFF5A1C22.toInt(),
        0xFFE8192C.toInt(), 0xFFFF6B76.toInt(), 0xFFFF1F33.toInt(),
    )
    private val racingDay = Palette(
        0xFFF3F3F5.toInt(), 0xFF141519.toInt(), 0xFF5F636B.toInt(),
        0xFFD4D6DB.toInt(), 0xFFFFFFFF.toInt(), 0xFFE9A9AF.toInt(),
        0xFFD8162A.toInt(), 0xFFF0707A.toInt(), 0xFFD0122A.toInt(),
    )
    private val touringNight = Palette(
        0xFF0A1513.toInt(), 0xFFF0F8F5.toInt(), 0xFF92ACA4.toInt(),
        0xFF263B36.toInt(), 0xFF12211E.toInt(), 0xFF2E5A50.toInt(),
        0xFF2FCB9E.toInt(), 0xFF7BE3C4.toInt(), 0xFFE8473C.toInt(),
    )
    private val touringDay = Palette(
        0xFFEFF4F1.toInt(), 0xFF13241F.toInt(), 0xFF5A736B.toInt(),
        0xFFC9D8D2.toInt(), 0xFFFAFDFB.toInt(), 0xFFB7CCC4.toInt(),
        0xFF13876A.toInt(), 0xFF4DB894.toInt(), 0xFFC93630.toInt(),
    )

    private const val GREEN = 0xFF3CC35A.toInt()
    private const val YELLOW = 0xFFF2C230.toInt()

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
    private val boldItalic = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val plain = Typeface.create("sans-serif", Typeface.NORMAL)
    private val clock = SimpleDateFormat("HH:mm", Locale.US)

    private data class Metric(
        val id: String, val title: String, val value: Double?, val unit: String,
        val max: Double = 100.0,
    )

    private fun metrics(d: DashboardMode.Data) = listOf(
        Metric("speed", "Speed", d.speedKmh, "km/h", 240.0),
        Metric("rpm", "RPM", d.rpm, "rpm", 14_000.0),
        Metric("throttle", "Throttle", d.relativeThrottle, "%", 100.0),
        Metric("coolant", "Coolant", d.coolant, "°C", 130.0),
        Metric("volt", "Voltage", d.volt, "V", 16.0),
        Metric("load", "Engine Load", d.load, "%", 100.0),
        Metric("intake", "Intake", d.intake, "°C", 80.0),
        Metric("baro", "Manifold", d.baro, "kPa", 120.0),
        Metric("absolute", "Abs Load", d.absoluteLoad, "%", 260.0),
    )

    /** The five cards of the reference, in its order. */
    private val cardIds = listOf("coolant", "volt", "load", "intake", "baro")

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
        fill.color = p.bg
        c.drawRect(0f, 0f, W, H, fill)
        val all = metrics(d).filter { it.id in visible }.associateBy { it.id }
        drawHeader(c, d, p, design)
        drawShiftLights(c, d.rpm, p, design)
        when (design) {
            DashboardMode.Design.SPORT -> drawSport(c, d, p, all)
            DashboardMode.Design.RACING -> drawRacing(c, d, p, all)
            DashboardMode.Design.TOURING -> drawTouring(c, d, p, all)
        }
        if (!d.obdLive) rightLabel(c, ctx.getString(R.string.dash_no_obd), W - 22f, 64f, 11f, p.muted, plain)
        c.restore()
        return target
    }

    // ── shared frame: header and shift lights ─────────────────────────────────

    private fun drawHeader(c: Canvas, d: DashboardMode.Data, p: Palette, design: DashboardMode.Design) {
        label(c, "RideScreenAA", 22f, 25f, 15f, p.fg, boldItalic)
        text.typeface = boldItalic; text.textSize = 15f
        val after = 22f + text.measureText("RideScreenAA") + 6f
        label(c, "///", after, 25f, 15f, p.accent, boldItalic)
        center(c, clock.format(Date()), W / 2f, 26f, 21f, p.fg, bold)
        // Status icons: the dash link is Wi-Fi; Bluetooth lights up while the OBD reader answers.
        val iconY = 18f
        drawWifi(c, 690f, iconY + 6f, p.fg)
        drawBluetooth(c, 718f, iconY, if (d.obdLive) p.fg else p.muted)
        drawPhone(c, 744f, iconY, GREEN)
        drawHeadlight(c, 772f, iconY, GREEN)
        stroke.color = p.edge
        stroke.strokeWidth = 1f
        c.drawLine(18f, 36f, W - 18f, 36f, stroke)
    }

    /** Ten short pills like the reference; LED 9 and 10 light from 9,000 rpm (red zone). */
    private fun drawShiftLights(c: Canvas, rpm: Double?, p: Palette, design: DashboardMode.Design) {
        val count = 10
        val width = 26f
        val gap = 8f
        val x0 = (W - (count * width + (count - 1) * gap)) / 2f
        for (i in 0 until count) {
            val threshold = (i + 1) * DashboardMode.RPM_MAX / count
            val active = rpm != null && rpm >= threshold
            fill.color = if (!active) p.track else when (design) {
                DashboardMode.Design.SPORT -> when {
                    i >= 8 -> p.red
                    i >= 6 -> p.accent
                    i >= 4 -> YELLOW
                    else -> GREEN
                }
                DashboardMode.Design.RACING -> if (i >= 8) p.red else p.fg
                DashboardMode.Design.TOURING -> if (i >= 8) p.red else p.accent
            }
            box.set(x0 + i * (width + gap), 43f, x0 + i * (width + gap) + width, 48f)
            c.drawRoundRect(box, 2.5f, 2.5f, fill)
        }
    }

    // ── SPORT ─────────────────────────────────────────────────────────────────────────────

    private fun drawSport(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        drawSportSpeed(c, a["speed"], 178f, 232f, 168f, p, d.speedFromGps)
        drawSmallDial(c, a["rpm"], 446f, 122f, 64f, p, rpm = true)
        drawSmallDial(c, a["throttle"], 610f, 122f, 64f, p, rpm = false)
        val cards = cardIds.mapNotNull(a::get)
        cards.forEachIndexed { i, m ->
            val col = i % 2
            val row = i / 2
            drawCard(c, m, 366f + col * 214f, 202f + row * 68f, 206f, 60f, p, CardLook.SPORT)
        }
    }

    private fun drawSportSpeed(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette, gps: Boolean) {
        if (m == null) return
        fill.color = p.tile
        c.drawCircle(cx, cy, r, fill)
        stroke.color = p.edge
        stroke.strokeWidth = 2f
        c.drawCircle(cx, cy, r - 1f, stroke)
        box.set(cx - r + 8f, cy - r + 8f, cx + r - 8f, cy + r - 8f)
        // Glow band, then the lit part up to the current speed.
        stroke.color = (p.accent and 0x00FFFFFF) or 0x38000000
        stroke.strokeWidth = 12f
        c.drawArc(box, 135f, 270f, false, stroke)
        val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
        stroke.color = p.accent
        stroke.strokeWidth = 6f
        c.drawArc(box, 135f, 270f * fraction, false, stroke)
        for (i in 0..48) {
            val angle = Math.toRadians(135.0 + 270.0 * i / 48)
            val major = i % 4 == 0
            val inner = r - if (major) 30f else 22f
            val outer = r - 16f
            stroke.color = if (major) p.fg else p.muted
            stroke.strokeWidth = if (major) 2f else 1f
            c.drawLine(
                cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, stroke,
            )
            if (major) {
                center(c, (20 * i / 4).toString(), cx + cos(angle).toFloat() * (r - 46f),
                    cy + sin(angle).toFloat() * (r - 46f) + 5f, 13f, p.fg, medium)
            }
        }
        center(c, "km/h" + if (gps) " · GPS" else "", cx, cy - 26f, 13f, p.muted, plain)
        center(c, value(m), cx, cy + 34f, 66f, p.fg, boldItalic)
        val angle = Math.toRadians(135.0 + 270.0 * fraction)
        stroke.color = p.accent
        stroke.strokeWidth = 4f
        c.drawLine(cx + cos(angle).toFloat() * 18f, cy + sin(angle).toFloat() * 18f,
            cx + cos(angle).toFloat() * (r - 34f), cy + sin(angle).toFloat() * (r - 34f), stroke)
        fill.color = p.accent
        c.drawCircle(cx + cos(angle).toFloat() * 18f, cy + sin(angle).toFloat() * 18f, 4f, fill)
    }

    private fun drawSmallDial(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette, rpm: Boolean) {
        if (m == null) return
        fill.color = p.tile
        c.drawCircle(cx, cy, r, fill)
        stroke.color = p.edge
        stroke.strokeWidth = 2f
        c.drawCircle(cx, cy, r - 1f, stroke)
        box.set(cx - r + 7f, cy - r + 7f, cx + r - 7f, cy + r - 7f)
        stroke.color = p.track
        stroke.strokeWidth = 5f
        c.drawArc(box, 135f, 270f, false, stroke)
        if (rpm) {
            val redFrom = (DashboardMode.RPM_RED / m.max).toFloat()
            stroke.color = (p.red and 0x00FFFFFF) or 0x90000000.toInt()
            c.drawArc(box, 135f + 270f * redFrom, 270f * (1f - redFrom), false, stroke)
        }
        val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
        stroke.color = if (rpm && (m.value ?: 0.0) >= DashboardMode.RPM_RED) p.red else p.accent
        c.drawArc(box, 135f, 270f * fraction, false, stroke)
        for (i in 0..28) {
            val angle = Math.toRadians(135.0 + 270.0 * i / 28)
            val major = i % 4 == 0
            stroke.color = if (major) p.fg else p.muted
            stroke.strokeWidth = if (major) 1.5f else .8f
            val inner = r - if (major) 19f else 15f
            c.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * (r - 11f), cy + sin(angle).toFloat() * (r - 11f), stroke)
        }
        if (rpm) {
            for (i in 0..7) {
                val angle = Math.toRadians(135.0 + 270.0 * i / 7.0)
                center(c, (i * 2).toString(), cx + cos(angle).toFloat() * (r - 27f),
                    cy + sin(angle).toFloat() * (r - 27f) + 3f, 8f, p.muted, plain)
            }
            center(c, value(m), cx, cy + 7f, 22f, p.fg, boldItalic)
            center(c, "rpm", cx, cy + 22f, 10f, p.muted, plain)
        } else {
            valueWithUnit(c, value(m), "%", cx, cy + 6f, 22f, p.fg, p.muted)
            center(c, "Throttle", cx, cy + 24f, 10f, p.muted, medium)
        }
        val angle = Math.toRadians(135.0 + 270.0 * fraction)
        stroke.color = p.accent
        stroke.strokeWidth = 2f
        c.drawLine(cx + cos(angle).toFloat() * (r - 34f), cy + sin(angle).toFloat() * (r - 34f),
            cx + cos(angle).toFloat() * (r - 12f), cy + sin(angle).toFloat() * (r - 12f), stroke)
    }

    // ── RACING ────────────────────────────────────────────────────────────────────────────

    private fun drawRacing(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        label(c, "RPM", 40f, 70f, 14f, p.fg, boldItalic)
        drawRacingTach(c, a["rpm"], p, 40f, 76f, 720f, 46f)
        drawSpeedPlate(c, a["speed"], d.speedFromGps, p, 40f, 158f, 320f, 118f)
        drawRacingThrottle(c, a["throttle"], 535f, 272f, 96f, p)
        drawCheckers(c, 680f, 180f, p)
        val cards = cardIds.mapNotNull(a::get)
        cards.forEachIndexed { i, m ->
            if (i < 3) drawCard(c, m, 40f + i * 245f, 290f, 235f, 52f, p, CardLook.RACING)
            else drawCard(c, m, 40f + (i - 3) * 367f, 350f, 353f, 52f, p, CardLook.RACING_WIDE)
        }
    }

    private fun drawRacingTach(c: Canvas, m: Metric?, p: Palette, x: Float, y: Float, w: Float, h: Float) {
        val skew = 14f
        val frame = Path().apply {
            moveTo(x + skew, y); lineTo(x + w, y); lineTo(x + w - skew, y + h); lineTo(x, y + h); close()
        }
        fill.color = p.tile
        c.drawPath(frame, fill)
        stroke.color = p.accent
        stroke.strokeWidth = 2f
        c.drawPath(frame, stroke)
        val count = 14
        val gap = 4f
        val inner = 8f
        val segW = (w - skew - inner * 2 - gap * (count - 1)) / count
        val top = y + inner
        val bottom = y + h - inner
        val max = m?.max ?: 14_000.0
        val rpm = m?.value ?: 0.0
        for (i in 0 until count) {
            val sx = x + inner + skew * .5f + i * (segW + gap)
            val seg = Path().apply {
                moveTo(sx + 6f, top); lineTo(sx + segW + 6f, top); lineTo(sx + segW, bottom); lineTo(sx, bottom); close()
            }
            val from = i * max / count
            fill.color = when {
                rpm <= from -> p.track
                from >= DashboardMode.RPM_RED -> p.red
                // Night: white → pink → red; day: light grey → red (as in the reference).
                else -> blend(if (p == racingDay) 0xFFC4C7CD.toInt() else p.fg, p.accent2, (from / DashboardMode.RPM_RED).toFloat())
            }
            c.drawPath(seg, fill)
        }
        center(c, value(m), x + w * .58f, y + h - 11f, 34f, p.fg, boldItalic)
        for (i in 0..7) {
            val vx = x + inner + skew * .5f + (w - skew - inner * 2) * i / 7f
            center(c, (i * 2).toString(), vx, y + h + 16f, 12f, p.fg, medium)
        }
    }

    private fun drawSpeedPlate(c: Canvas, m: Metric?, gps: Boolean, p: Palette, x: Float, y: Float, w: Float, h: Float) {
        if (m == null) return
        val cut = 22f
        val plate = Path().apply {
            moveTo(x + cut, y); lineTo(x + w, y); lineTo(x + w - cut, y + h); lineTo(x, y + h)
            lineTo(x, y + h - cut); lineTo(x + cut * .6f, y + cut * .5f); close()
        }
        fill.color = p.tile
        c.drawPath(plate, fill)
        stroke.color = p.accent
        stroke.strokeWidth = 2.5f
        c.drawPath(plate, stroke)
        center(c, value(m), x + w * .42f, y + h * .64f, 62f, p.fg, boldItalic)
        center(c, "km/h" + if (gps) " · GPS" else "", x + w * .42f, y + h - 10f, 14f, p.muted, medium)
        // Red chevrons on the right edge of the plate.
        fill.color = p.accent
        for (i in 0..2) {
            val sx = x + w - 70f + i * 12f
            val chev = Path().apply {
                moveTo(sx, y + h - 34f); lineTo(sx + 7f, y + h - 34f); lineTo(sx + 2f, y + h - 18f); lineTo(sx - 5f, y + h - 18f); close()
            }
            c.drawPath(chev, fill)
        }
    }

    private fun drawRacingThrottle(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette) {
        if (m == null) return
        box.set(cx - r, cy - r, cx + r, cy + r)
        // Dotted grey track over the upper half, red arc for the opening.
        fill.color = p.track
        for (i in 0..30) {
            val angle = Math.toRadians(180.0 + 180.0 * i / 30)
            c.drawCircle(cx + cos(angle).toFloat() * (r - 2f), cy + sin(angle).toFloat() * (r - 2f), 2.2f, fill)
        }
        val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
        box.set(cx - r + 10f, cy - r + 10f, cx + r - 10f, cy + r - 10f)
        stroke.color = p.track
        stroke.strokeWidth = 9f
        c.drawArc(box, 180f, 180f, false, stroke)
        stroke.color = p.accent
        c.drawArc(box, 180f, 180f * fraction, false, stroke)
        valueWithUnit(c, value(m), "%", cx, cy - 22f, 28f, p.fg, p.muted, boldItalic)
        center(c, "Throttle", cx, cy - 4f, 13f, p.muted, medium)
    }

    private fun drawCheckers(c: Canvas, x: Float, y: Float, p: Palette) {
        val size = 8f
        for (row in 0..3) for (col in 0..6) {
            fill.color = if ((row + col) % 2 == 0) p.accent else p.tile
            val off = col * 1.5f
            c.drawRect(x + col * size, y + row * size - off, x + (col + 1) * size, y + (row + 1) * size - off, fill)
        }
    }

    // ── TOURING ───────────────────────────────────────────────────────────────────────────

    private fun drawTouring(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        drawLandscape(c, p)
        drawTouringSpeed(c, a["speed"], 200f, 240f, 168f, p, d.speedFromGps)
        drawTouringRpm(c, a["rpm"], p, 440f, 100f, 50f, 172f)
        drawTouringThrottle(c, a["throttle"], 636f, 168f, 76f, p)
        val cards = cardIds.mapNotNull(a::get)
        cards.forEachIndexed { i, m ->
            if (i < 3) drawCard(c, m, 22f + i * 255f, 296f, 246f, 50f, p, CardLook.TOURING)
            else drawCard(c, m, 22f + (i - 3) * 383f, 352f, 373f, 50f, p, CardLook.TOURING)
        }
    }

    private fun drawTouringSpeed(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette, gps: Boolean) {
        if (m == null) return
        val segments = 44
        val start = 168.0
        val sweep = 204.0
        val fraction = ((m.value ?: 0.0) / m.max).coerceIn(0.0, 1.0)
        for (i in 0 until segments) {
            val a0 = Math.toRadians(start + sweep * i / segments)
            val lit = (i + 1.0) / segments <= fraction
            stroke.color = if (lit) p.accent else p.track
            stroke.strokeWidth = 4f
            val inner = r - 24f
            c.drawLine(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner,
                cx + cos(a0).toFloat() * r, cy + sin(a0).toFloat() * r, stroke)
        }
        center(c, value(m), cx, cy - 18f, 70f, p.fg, bold)
        center(c, "km/h" + if (gps) " · GPS" else "", cx, cy + 8f, 16f, p.fg, medium)
    }

    private fun drawLandscape(c: Canvas, p: Palette) {
        val tint = (p.accent and 0x00FFFFFF) or 0x30000000
        val far = Path().apply {
            moveTo(30f, 282f); lineTo(90f, 214f); lineTo(140f, 240f); lineTo(210f, 196f)
            lineTo(282f, 232f); lineTo(318f, 206f); lineTo(380f, 282f); close()
        }
        fill.color = tint
        c.drawPath(far, fill)
        // A few pines on the right, like the reference.
        fill.color = (p.accent and 0x00FFFFFF) or 0x55000000
        for ((tx, th) in listOf(330f to 34f, 348f to 46f, 366f to 30f, 384f to 40f)) {
            val tree = Path().apply { moveTo(tx, 282f - th); lineTo(tx + 9f, 282f); lineTo(tx - 9f, 282f); close() }
            c.drawPath(tree, fill)
        }
    }

    /** Fourteen segments on the 0–14 ×1000 scale; red from RPM_RED. */
    private fun drawTouringRpm(c: Canvas, m: Metric?, p: Palette, x: Float, y: Float, width: Float, height: Float) {
        center(c, "RPM", x + width / 2f, y - 26f, 12f, p.muted, medium)
        center(c, value(m), x + width / 2f, y - 7f, 20f, p.fg, boldItalic)
        val count = 14
        val gap = 2.5f
        val segH = (height - gap * (count - 1)) / count
        val max = m?.max ?: 14_000.0
        val rpm = m?.value ?: 0.0
        for (i in 0 until count) {
            val bottom = y + height - i * (segH + gap)
            val from = i * max / count
            fill.color = when {
                rpm <= from -> p.track
                from >= DashboardMode.RPM_RED -> p.red
                else -> p.accent
            }
            box.set(x, bottom - segH, x + width, bottom)
            c.drawRoundRect(box, 2f, 2f, fill)
        }
        for (i in 0..7) {
            val yy = y + height - height * (i * 2 / 14f)
            label(c, (i * 2).toString(), x + width + 8f, yy + 3f, 9f, p.fg, plain)
        }
    }

    private fun drawTouringThrottle(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette) {
        if (m == null) return
        fill.color = p.tile
        c.drawCircle(cx, cy, r, fill)
        box.set(cx - r + 6f, cy - r + 6f, cx + r - 6f, cy + r - 6f)
        stroke.color = p.track
        stroke.strokeWidth = 8f
        c.drawArc(box, 0f, 360f, false, stroke)
        val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
        stroke.color = p.accent
        c.drawArc(box, -90f, 360f * fraction, false, stroke)
        valueWithUnit(c, value(m), "%", cx, cy + 4f, 28f, p.fg, p.muted, boldItalic)
        center(c, "Throttle", cx, cy + 24f, 12f, p.muted, medium)
    }

    // ── cards ─────────────────────────────────────────────────────────────────────────────

    private enum class CardLook { SPORT, RACING, RACING_WIDE, TOURING }

    /** Icon on the left, title above, value with its unit right after it (as in the reference). */
    private fun drawCard(c: Canvas, m: Metric, x: Float, y: Float, w: Float, h: Float, p: Palette, look: CardLook) {
        val radius = if (look == CardLook.TOURING) 10f else 7f
        box.set(x, y, x + w, y + h)
        fill.color = p.tile
        c.drawRoundRect(box, radius, radius, fill)
        stroke.color = when (look) {
            CardLook.RACING, CardLook.RACING_WIDE -> p.accent
            else -> p.edge
        }
        stroke.strokeWidth = 1.3f
        c.drawRoundRect(box, radius, radius, stroke)
        if (look == CardLook.RACING_WIDE) {
            fill.color = (p.accent and 0x00FFFFFF) or 0xCC000000.toInt()
            for (i in 0..3) {
                val sx = x + w - 52f + i * 10f
                val stripe = Path().apply {
                    moveTo(sx + 8f, y + 8f); lineTo(sx + 13f, y + 8f); lineTo(sx + 5f, y + h - 8f); lineTo(sx, y + h - 8f); close()
                }
                c.drawPath(stripe, fill)
            }
        }
        val warn = m.id == "coolant" && (m.value ?: 0.0) >= DashboardMode.COOLANT_AMBER
        val iconColor = if (warn) p.red else p.fg
        drawMetricIcon(c, m.id, x + 22f, y + h / 2f, 9f, iconColor)
        val tx = x + 44f
        val titleSize = if (h >= 48f) 11f else 10f
        val valueSize = if (h >= 48f) 21f else 18f
        label(c, m.title, tx, y + h * .40f, titleSize, p.muted, medium)
        text.typeface = bold; text.textSize = valueSize
        val v = value(m)
        label(c, v, tx, y + h - 6f, valueSize, if (warn) p.red else p.fg, bold)
        text.typeface = bold; text.textSize = valueSize
        val vw = text.measureText(v)
        label(c, m.unit, tx + vw + 4f, y + h - 6f, valueSize * .6f, p.fg, medium)
    }

    // ── icons ─────────────────────────────────────────────────────────────────────────────

    private fun drawMetricIcon(c: Canvas, id: String, cx: Float, cy: Float, r: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.7f
        fill.color = color
        when (id) {
            "coolant" -> {
                c.drawLine(cx, cy - r, cx, cy + 3f, stroke)
                c.drawLine(cx, cy - r + 2f, cx + 5f, cy - r + 2f, stroke)
                c.drawLine(cx, cy - r + 6f, cx + 4f, cy - r + 6f, stroke)
                c.drawCircle(cx, cy + 5f, 3f, fill)
                for (i in -1..1) {
                    val xx = cx + i * 5f
                    c.drawLine(xx - 2f, cy + r + 1f, xx + 2f, cy + r - 1f, stroke)
                }
            }
            "volt" -> {
                box.set(cx - r, cy - r * .6f, cx + r, cy + r * .7f)
                c.drawRoundRect(box, 2f, 2f, stroke)
                c.drawLine(cx - r * .55f, cy - r * .6f, cx - r * .55f, cy - r * .9f, stroke)
                c.drawLine(cx + r * .55f, cy - r * .6f, cx + r * .55f, cy - r * .9f, stroke)
                c.drawLine(cx - r * .7f, cy, cx - r * .3f, cy, stroke)
                c.drawLine(cx + r * .3f, cy, cx + r * .7f, cy, stroke)
                c.drawLine(cx + r * .5f, cy - 2f, cx + r * .5f, cy + 2f, stroke)
            }
            "load" -> {
                box.set(cx - r, cy - r * .55f, cx + r * .7f, cy + r * .6f)
                c.drawRoundRect(box, 2f, 2f, stroke)
                c.drawLine(cx - r * .3f, cy - r * .55f, cx - r * .3f, cy - r * .9f, stroke)
                c.drawLine(cx - r * .6f, cy - r * .9f, cx, cy - r * .9f, stroke)
                c.drawLine(cx + r * .7f, cy - 2f, cx + r, cy - 4f, stroke)
                c.drawLine(cx + r, cy - 4f, cx + r, cy + 4f, stroke)
            }
            "intake" -> for (i in -1..1) {
                val yy = cy + i * 4.5f
                c.drawLine(cx - r, yy, cx + r * .4f, yy, stroke)
                c.drawArc(cx + r * .2f, yy - 3f, cx + r, yy + 3f, 270f, 180f, false, stroke)
            }
            else -> {
                c.drawLine(cx - r, cy - r * .6f, cx + r, cy - r * .6f, stroke)
                for (i in -1..1) {
                    val xx = cx + i * 5f
                    c.drawLine(xx, cy - r * .6f, xx, cy + r * .6f, stroke)
                }
                c.drawLine(cx - r, cy + r * .6f, cx + r, cy + r * .6f, stroke)
            }
        }
    }

    private fun drawWifi(c: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.8f
        for (r in listOf(4f, 8f, 12f)) {
            box.set(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(box, 225f, 90f, false, stroke)
        }
        fill.color = color
        c.drawCircle(cx, cy - 1f, 1.8f, fill)
    }

    private fun drawBluetooth(c: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.6f
        c.drawLine(cx, cy - 8f, cx, cy + 8f, stroke)
        c.drawLine(cx, cy - 8f, cx + 5f, cy - 4f, stroke)
        c.drawLine(cx + 5f, cy - 4f, cx - 5f, cy + 4f, stroke)
        c.drawLine(cx, cy + 8f, cx + 5f, cy + 4f, stroke)
        c.drawLine(cx + 5f, cy + 4f, cx - 5f, cy - 4f, stroke)
    }

    private fun drawPhone(c: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.6f
        box.set(cx - 5f, cy - 9f, cx + 5f, cy + 9f)
        c.drawRoundRect(box, 2f, 2f, stroke)
        fill.color = color
        box.set(cx - 2.5f, cy - 2f, cx + 2.5f, cy + 6f)
        c.drawRect(box, fill)
    }

    private fun drawHeadlight(c: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.6f
        box.set(cx - 2f, cy - 7f, cx + 10f, cy + 7f)
        c.drawArc(box, 90f, 180f, false, stroke)
        c.drawLine(cx + 4f, cy - 7f, cx + 4f, cy + 7f, stroke)
        for (i in -1..1) c.drawLine(cx - 4f, cy + i * 4.5f, cx - 10f, cy + i * 4.5f, stroke)
    }

    // ── text helpers ──────────────────────────────────────────────────────────────────────

    private fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).toInt() and 0xFF
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun valueWithUnit(
        c: Canvas, v: String, unit: String, cx: Float, y: Float, size: Float, color: Int, unitColor: Int,
        face: Typeface = bold,
    ) {
        text.typeface = face; text.textSize = size
        val vw = text.measureText(v)
        text.typeface = medium; text.textSize = size * .5f
        val uw = text.measureText(unit)
        val x0 = cx - (vw + 3f + uw) / 2f
        label(c, v, x0, y, size, color, face)
        label(c, unit, x0 + vw + 3f, y, size * .5f, unitColor, medium)
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
