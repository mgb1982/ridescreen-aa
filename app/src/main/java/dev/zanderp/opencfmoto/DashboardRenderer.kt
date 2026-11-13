// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The six dashboard faces from the approved reference sheet (SPORT / RACING / TOURING, night and
 * day), drawn at a fixed 800×416 design size and scaled into the whole bike canvas. The style
 * picker shows this same renderer with sample data, so the preview is what the bike shows.
 *
 * Every face has shift lights, the style's gauges and five telemetry cards. The reference's header
 * (clock, icons) and bottom speed / temperature / fuel strip are the Zontes dash's own, drawn by the
 * dash around and over the frame, so they are not drawn here (see [VIS_BOTTOM]).
 *
 * Look: gradients, metallic rims, glass highlights, glows and soft shadows. Everything that does
 * not change with the data (faces, rims, ticks, textures, card frames…) is drawn once into a cached
 * layer per style/theme/size; each frame (8×/s) only copies it and draws values, arcs and needles.
 */
internal object DashboardRenderer {
    private const val W = 800f
    private const val H = 416f

    /**
     * What the Zontes 125X actually shows of the 800×416 frame (Manel's photos, 6 Nov 2026): the dash
     * draws its own clock bar above the frame and lays its speed/fuel strip over the bottom of it,
     * from y≈330 on the left and centre and from y≈288 right of x≈680, where the strip steps up for
     * its speed readout. Everything readable stays inside; below is background only.
     */
    internal const val VIS_BOTTOM = 326f
    internal const val VIS_BOTTOM_RIGHT = 284f
    internal const val VIS_RIGHT_STEP_X = 676f

    private data class Palette(
        val bg: Int, val fg: Int, val muted: Int, val track: Int,
        val tile: Int, val edge: Int, val accent: Int, val accent2: Int, val red: Int,
    )

    private val sportNight = Palette(
        0xFF0B0C0E.toInt(), 0xFFF4F4F4.toInt(), 0xFF9A9EA6.toInt(),
        0xFF2C2F35.toInt(), 0xFF17191D.toInt(), 0xFF3A3D44.toInt(),
        0xFFFF8A1E.toInt(), 0xFFFFC266.toInt(), 0xFFE83A2E.toInt(),
    )
    private val sportDay = Palette(
        0xFFF2F2EF.toInt(), 0xFF15171A.toInt(), 0xFF60656D.toInt(),
        0xFFD5D7DA.toInt(), 0xFFFFFFFF.toInt(), 0xFFC4C7CC.toInt(),
        0xFFF0860F.toInt(), 0xFFF6B13E.toInt(), 0xFFD9372B.toInt(),
    )
    private val racingNight = Palette(
        0xFF08090B.toInt(), 0xFFF7F7F8.toInt(), 0xFFA0A4AB.toInt(),
        0xFF2A2D33.toInt(), 0xFF131519.toInt(), 0xFF5A1C22.toInt(),
        0xFFE8192C.toInt(), 0xFFFF6B76.toInt(), 0xFFFF1F33.toInt(),
    )
    private val racingDay = Palette(
        0xFFF3F3F5.toInt(), 0xFF141519.toInt(), 0xFF5F636B.toInt(),
        0xFFD4D6DB.toInt(), 0xFFFFFFFF.toInt(), 0xFFE9A9AF.toInt(),
        0xFFD8162A.toInt(), 0xFFF0707A.toInt(), 0xFFD0122A.toInt(),
    )
    private val touringNight = Palette(
        0xFF0A1513.toInt(), 0xFFF0F8F5.toInt(), 0xFF92ACA4.toInt(),
        0xFF263B36.toInt(), 0xFF12221F.toInt(), 0xFF2E5A50.toInt(),
        0xFF2FCB9E.toInt(), 0xFF7BE3C4.toInt(), 0xFFE8473C.toInt(),
    )
    private val touringDay = Palette(
        0xFFEFF4F1.toInt(), 0xFF13241F.toInt(), 0xFF5A736B.toInt(),
        0xFFC9D8D2.toInt(), 0xFFFAFDFB.toInt(), 0xFFB7CCC4.toInt(),
        0xFF13876A.toInt(), 0xFF4DB894.toInt(), 0xFFC93630.toInt(),
    )

    /** 2.5 · the rider's accent over a style palette. Racing also tints its card edges; red stays red. */
    private fun Palette.withAccent(color: Int, design: DashboardMode.Design): Palette {
        val a = DashLayout.accentFor(color, night, tile)
        return copy(accent = a.accent, accent2 = a.accent2, edge = if (design == DashboardMode.Design.RACING) a.edge else edge)
    }

    private const val GREEN = 0xFF3CC35A.toInt()
    private const val YELLOW = 0xFFF2C230.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** Two passes over the same layout: the cached background layer and the live data. */
    private enum class Pass { STATIC, LIVE }
    private var pass = Pass.STATIC
    private var night = true

    /** Full scale of the speed and RPM gauges, per bike ([DashboardMode.speedMax], [DashboardMode.rpmMax]). */
    private var speedMax = 240
    private var rpmMax = 14_000
    /** RPM scale in thousands (10, 12 or 14): one tach segment per 1,000 rpm, a label every 2,000. */
    private val rpmK get() = rpmMax / 1000

    /** v2.6 · Focus hero for the frame being drawn, and the state that keeps it from flickering. */
    private var hero = FocusHero.Hero.SPEED
    private val focusState = FocusHeroState(DashboardMode.COOLANT_AMBER)

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var layer: Bitmap? = null
    private var layerKey: String? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val matrix = Matrix()

    private var fValue: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    private var fSemi: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    private var fBold: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private var fLabel: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    private var fontsLoaded = false
    private val clock = SimpleDateFormat("HH:mm", Locale.US)

    private data class Metric(
        val id: String, val title: String, val value: Double?, val unit: String,
        val max: Double = 100.0,
    )

    private fun metrics(d: DashboardMode.Data) = listOf(
        Metric("speed", "Speed", d.speedKmh, "km/h", speedMax.toDouble()),
        Metric("rpm", "RPM", d.rpm, "rpm", rpmMax.toDouble()),
        Metric("throttle", "Throttle", d.relativeThrottle, "%", 100.0),
        Metric("coolant", "Coolant", d.coolant, "°C", 130.0),
        Metric("volt", "Voltage", d.volt, "V", 16.0),
        Metric("load", "Engine Load", d.load, "%", 100.0),
        Metric("intake", "Intake", d.intake, "°C", 80.0),
        Metric("baro", "Manifold", d.baro, "kPa", 120.0),
        Metric("absolute", "Abs Load", d.absoluteLoad, "%", 260.0),
    )


    private fun value(m: Metric?): String = m?.value?.let {
        if (m.unit == "V") String.format(Locale.getDefault(), "%.1f", it)
        else String.format(Locale.US, "%.0f", it)
    } ?: "—"

    private inline fun still(block: () -> Unit) { if (pass == Pass.STATIC) block() }
    private inline fun live(block: () -> Unit) { if (pass == Pass.LIVE) block() }

    private fun loadFonts(ctx: Context) {
        if (fontsLoaded) return
        fontsLoaded = true
        fun font(id: Int, fallback: Typeface) = try {
            ResourcesCompat.getFont(ctx, id) ?: fallback
        } catch (_: Exception) {
            fallback
        }
        fValue = font(R.font.barlow_condensed_bold_italic, fValue)
        fSemi = font(R.font.barlow_condensed_semibold_italic, fSemi)
        fBold = font(R.font.barlow_condensed_bold, fBold)
        fLabel = font(R.font.barlow_condensed_medium, fLabel)
    }

    /** Synchronized: the GL thread draws the live dash while the style picker draws its previews. */
    @Synchronized
    fun render(
        ctx: Context, w: Int, h: Int, d: DashboardMode.Data, light: Boolean,
        design: DashboardMode.Design, visible: Set<String>,
        /** True for the style picker: draw into a new bitmap. The live one belongs to the GL thread until
         *  it is uploaded; reusing (or recycling, on a dash whose canvas isn't 800×416) it from the UI
         *  thread showed the preview on the bike or crashed on a recycled bitmap. */
        standalone: Boolean = false,
        /** 2.5 · rider's card order ([DashLayout.orderedCards] fills in the rest). */
        cardOrder: List<String> = DashLayout.DEFAULT_CARDS,
        /** 2.5 · accent colour, 0 = the style's own. */
        accent: Int = 0,
    ): Bitmap {
        loadFonts(ctx)
        var target = if (standalone) null else bitmap
        val c: Canvas
        if (standalone) {
            target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            c = Canvas(target)
        } else {
            if (target == null || target.width != w || target.height != h) {
                // Not recycled: the GL thread may still be about to upload it.
                target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap = target
                canvas = Canvas(target)
            }
            c = canvas!!
        }
        night = !light
        val base = when (design) {
            // The four v2.6 designs share the Sport palettes (orange accent, or the rider's own).
            DashboardMode.Design.SPORT, DashboardMode.Design.CLEAN, DashboardMode.Design.PIT,
            DashboardMode.Design.CLASSIC, DashboardMode.Design.FOCUS, DashboardMode.Design.WINDOW -> if (light) sportDay else sportNight
            DashboardMode.Design.RACING -> if (light) racingDay else racingNight
            DashboardMode.Design.TOURING -> if (light) touringDay else touringNight
        }
        val p = if (accent == 0) base else base.withAccent(accent, design)
        speedMax = DashboardMode.speedMax(ctx)
        rpmMax = DashboardMode.rpmMax(ctx)
        // Focus: which reading is the hero. The live dash keeps the 3 degree hysteresis; a preview doesn't touch it.
        hero = if (design != DashboardMode.Design.FOCUS) FocusHero.Hero.SPEED else {
            val volt = if ("volt" in visible) d.volt else null
            if (standalone) FocusHero.pick(d.coolant, d.rpm, volt, false, DashboardMode.COOLANT_AMBER)
            else focusState.update(d.coolant, d.rpm, volt)
        }
        val all = metrics(d).filter { it.id in visible }.associateBy { it.id }
        val cards = DashLayout.orderedCards(cardOrder, visible).mapNotNull(all::get)
        // WINDOW draws in real canvas pixels (AaWindowLayout); the others in the virtual 800x416 space.
        val real = design == DashboardMode.Design.WINDOW
        val scale = if (real) 1f else minOf(w / W, h / H)
        val dx = if (real) 0f else (w - W * scale) / 2f
        val dy = if (real) 0f else (h - H * scale) / 2f

        val key = "${design.id}|$light|${w}x$h|$speedMax|$rpmMax|${visible.sorted().joinToString(",")}|" +
            "${cards.joinToString(",") { it.id }}|$accent"
        if (key != layerKey || layer == null) {
            layer?.recycle()
            val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val lc = Canvas(bg)
            lc.drawColor(p.bg)
            lc.save(); lc.translate(dx, dy); lc.scale(scale, scale)
            pass = Pass.STATIC
            drawFace(ctx, lc, d, p, design, all, cards)
            lc.restore()
            layer = bg
            layerKey = key
        }
        c.drawBitmap(layer!!, 0f, 0f, null)
        c.save(); c.translate(dx, dy); c.scale(scale, scale)
        pass = Pass.LIVE
        drawFace(ctx, c, d, p, design, all, cards)
        c.restore()
        return target!!
    }

    private fun drawFace(
        ctx: Context, c: Canvas, d: DashboardMode.Data, p: Palette,
        design: DashboardMode.Design, all: Map<String, Metric>, cards: List<Metric>,
    ) {
        if (design == DashboardMode.Design.WINDOW) { drawWindow(ctx, c, d, p, all, cards); return }
        still { drawBackground(c, p, design) }
        // No header: the dash's own clock bar sits right above the frame.
        // Pit has its own wide RPM bar instead of the shift lights.
        if (design != DashboardMode.Design.PIT) live { drawShiftLights(c, d.rpm, p, design) }
        when (design) {
            DashboardMode.Design.SPORT -> drawSport(c, d, p, all, cards)
            DashboardMode.Design.RACING -> drawRacing(c, d, p, all, cards)
            DashboardMode.Design.TOURING -> drawTouring(c, d, p, all, cards)
            DashboardMode.Design.CLEAN -> drawClean(c, d, p, all)
            DashboardMode.Design.PIT -> drawPit(c, d, p, all, cards)
            DashboardMode.Design.CLASSIC -> drawClassic(c, d, p, all)
            DashboardMode.Design.FOCUS -> drawFocus(c, d, p, all)
        }
        live {
            if (!d.obdLive) txt(c, ctx.getString(R.string.dash_no_obd), 24f, 22f, 13f, p.muted, fLabel)
        }
    }

    // ── effects ───────────────────────────────────────────────────────────────────────────

    private fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun lighten(color: Int, f: Float) = blend(color, WHITE, f)
    private fun darken(color: Int, f: Float) = blend(color, BLACK, f)

    private fun resetFx() {
        fx.reset()
        fx.isAntiAlias = true
    }

    /** Soft light behind a stroke (arcs, lines). Stronger at night, a faint bloom by day. */
    private fun glowArc(c: Canvas, oval: RectF, start: Float, sweep: Float, color: Int, width: Float, blur: Float = 9f) {
        if (sweep <= 0f) return
        resetFx()
        fx.style = Paint.Style.STROKE
        fx.strokeCap = Paint.Cap.ROUND
        fx.strokeWidth = width + blur * .8f
        fx.color = alpha(color, if (night) 0xB0 else 0x50)
        fx.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        c.drawArc(oval, start, sweep, false, fx)
    }

    private fun glowPath(c: Canvas, path: Path, color: Int, blur: Float, strength: Int) {
        resetFx()
        fx.style = Paint.Style.FILL
        fx.color = alpha(color, strength)
        fx.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(path, fx)
    }

    private fun shadowRoundRect(c: Canvas, r: RectF, radius: Float, depth: Float) {
        resetFx()
        fx.color = alpha(BLACK, if (night) 0xC0 else 0x38)
        fx.maskFilter = BlurMaskFilter(depth, BlurMaskFilter.Blur.NORMAL)
        val s = RectF(r.left, r.top + depth * .5f, r.right, r.bottom + depth * .7f)
        c.drawRoundRect(s, radius, radius, fx)
    }

    private fun shadowCircle(c: Canvas, cx: Float, cy: Float, r: Float, depth: Float) {
        resetFx()
        fx.color = alpha(BLACK, if (night) 0xD0 else 0x40)
        fx.maskFilter = BlurMaskFilter(depth, BlurMaskFilter.Blur.NORMAL)
        c.drawCircle(cx, cy + depth * .5f, r, fx)
    }

    /** Dial face: radial light from the top-left, darker towards the rim. */
    private fun face(c: Canvas, cx: Float, cy: Float, r: Float, p: Palette) {
        resetFx()
        val top = if (night) lighten(p.tile, .10f) else WHITE
        val bottom = if (night) darken(p.tile, .55f) else darken(p.tile, .07f)
        fx.shader = RadialGradient(cx - r * .25f, cy - r * .35f, r * 1.35f,
            intArrayOf(top, p.tile, bottom), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fx)
    }

    /** Brushed-metal rim: sweep of light and dark bands, with a thin dark inner line. */
    private fun rim(c: Canvas, cx: Float, cy: Float, r: Float, width: Float) {
        resetFx()
        fx.style = Paint.Style.STROKE
        fx.strokeWidth = width
        val hi = if (night) 0xFF9EA3AB.toInt() else 0xFFF7F8FA.toInt()
        val mid = if (night) 0xFF4A4E56.toInt() else 0xFFC3C7CE.toInt()
        val lo = if (night) 0xFF1C1E22.toInt() else 0xFF8E939B.toInt()
        fx.shader = SweepGradient(cx, cy, intArrayOf(hi, mid, lo, mid, hi, mid, lo, mid, hi), null)
        matrix.reset(); matrix.postRotate(-60f, cx, cy)
        fx.shader.setLocalMatrix(matrix)
        c.drawCircle(cx, cy, r - width / 2f, fx)
        resetFx()
        fx.style = Paint.Style.STROKE
        fx.strokeWidth = 1.2f
        fx.color = alpha(BLACK, if (night) 0xFF else 0x60)
        c.drawCircle(cx, cy, r - width, fx)
    }

    /** Glass highlight across the top half of a round instrument. */
    private fun glass(c: Canvas, cx: Float, cy: Float, r: Float) {
        resetFx()
        fx.shader = LinearGradient(cx, cy - r, cx, cy,
            intArrayOf(alpha(WHITE, if (night) 0x26 else 0x60), alpha(WHITE, 0)), null, Shader.TileMode.CLAMP)
        box.set(cx - r * .86f, cy - r * .94f, cx + r * .86f, cy + r * .2f)
        c.drawOval(box, fx)
    }

    private fun sweepShader(cx: Float, cy: Float, from: Int, to: Int, startDeg: Float, sweepDeg: Float): Shader {
        val end = (sweepDeg / 360f).coerceIn(.01f, 1f)
        return SweepGradient(cx, cy, intArrayOf(from, to, to), floatArrayOf(0f, end, 1f)).also {
            matrix.reset(); matrix.postRotate(startDeg, cx, cy); it.setLocalMatrix(matrix)
        }
    }

    // ── background, header, shift lights ──────────────────────────────────────────────────

    private fun drawBackground(c: Canvas, p: Palette, design: DashboardMode.Design) {
        resetFx()
        val center = if (night) lighten(p.bg, .07f) else lighten(p.bg, .5f)
        val edge = if (night) darken(p.bg, .6f) else darken(p.bg, .06f)
        fx.shader = RadialGradient(W * .42f, H * .45f, W * .75f, intArrayOf(center, p.bg, edge),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, fx)
        // Fine texture: carbon weave for Sport/Racing, a faint hex mesh for Touring.
        resetFx()
        fx.color = alpha(if (night) WHITE else BLACK, if (night) 0x07 else 0x06)
        if (design == DashboardMode.Design.TOURING) {
            fx.style = Paint.Style.STROKE
            fx.strokeWidth = 1f
            var y = 0f
            var row = 0
            while (y < H) {
                var x = if (row % 2 == 0) 0f else 9f
                while (x < W) {
                    val hex = Path().apply {
                        moveTo(x, y - 6f); lineTo(x + 6f, y - 3f); lineTo(x + 6f, y + 3f)
                        lineTo(x, y + 6f); lineTo(x - 6f, y + 3f); lineTo(x - 6f, y - 3f); close()
                    }
                    c.drawPath(hex, fx)
                    x += 18f
                }
                y += 10.5f; row++
            }
        } else {
            val cell = 6f
            var y = 0f
            var row = 0
            while (y < H) {
                var x = if (row % 2 == 0) 0f else cell
                while (x < W) {
                    c.drawRect(x, y, x + cell, y + cell * .5f, fx)
                    x += cell * 2
                }
                y += cell * .5f; row++
            }
        }
    }

    private fun drawHeader(c: Canvas, d: DashboardMode.Data, p: Palette) {
        still {
            text.typeface = fValue; text.textSize = 19f
            val logoW = text.measureText("RideScreenAA")
            textWithGlow(c, "RideScreenAA", 22f, 26f, 19f, p.fg, fValue, null)
            textWithGlow(c, "///", 22f + logoW + 6f, 26f, 19f, p.accent, fValue, p.accent)
            drawWifi(c, 690f, 24f, p.fg)
            drawPhone(c, 744f, 18f, GREEN)
            drawHeadlight(c, 772f, 18f, GREEN)
            resetFx()
            fx.shader = LinearGradient(18f, 0f, W - 18f, 0f,
                intArrayOf(alpha(p.edge, 0), p.edge, alpha(p.edge, 0)), null, Shader.TileMode.CLAMP)
            c.drawRect(18f, 36f, W - 18f, 37f, fx)
        }
        live {
            centerText(c, clock.format(Date()), W / 2f, 28f, 25f, p.fg, fBold)
            drawBluetooth(c, 718f, 18f, if (d.obdLive) p.fg else p.muted)
        }
    }

    /** Ten short pills like the reference; LED 9 and 10 light from 9,000 rpm (red zone). */
    private fun drawShiftLights(c: Canvas, rpm: Double?, p: Palette, design: DashboardMode.Design) {
        val count = 10
        val width = 26f
        val gap = 8f
        val x0 = (W - (count * width + (count - 1) * gap)) / 2f
        for (i in 0 until count) {
            val threshold = (i + 1) * rpmMax.toDouble() / count
            val active = rpm != null && rpm >= threshold
            val color = when (design) {
                DashboardMode.Design.SPORT -> when {
                    i >= 8 -> p.red
                    i >= 6 -> p.accent
                    i >= 4 -> YELLOW
                    else -> GREEN
                }
                DashboardMode.Design.RACING -> if (i >= 8) p.red else p.fg
                else -> if (i >= 8) p.red else p.accent // Touring and the v2.6 designs
            }
            box.set(x0 + i * (width + gap), 5f, x0 + i * (width + gap) + width, 10f)
            if (active) {
                resetFx()
                fx.color = alpha(color, if (night) 0xC0 else 0x60)
                fx.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
                c.drawRoundRect(box, 2.5f, 2.5f, fx)
            }
            fill.shader = null
            fill.color = if (active) color else p.track
            c.drawRoundRect(box, 2.5f, 2.5f, fill)
        }
    }

    // ── SPORT ─────────────────────────────────────────────────────────────────────────────

    private fun drawSport(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>, cards: List<Metric>) {
        drawSportSpeed(c, a["speed"], 178f, 172f, 152f, p, d.speedFromGps)
        // The dials sit centred over the two columns of cards (360–785); the cards themselves are laid
        // out by DashLayout, which keeps them clear of the dash strip's step.
        val colW = 206f; val gapX = 13f; val left = 360f
        drawSmallDial(c, a["rpm"], left + colW / 2f, 78f, 62f, p, rpm = true)
        drawSmallDial(c, a["throttle"], left + colW + gapX + colW / 2f, 78f, 62f, p, rpm = false)
        DashLayout.cards(DashLayout.SPORT, cards.size).forEachIndexed { i, b -> drawCard(c, cards[i], b, p, CardLook.SPORT) }
    }

    private fun drawSportSpeed(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette, gps: Boolean) {
        if (m == null) return
        val bandR = r - 14f
        box.set(cx - bandR, cy - bandR, cx + bandR, cy + bandR)
        val band = RectF(box)
        still {
            shadowCircle(c, cx, cy, r, 14f)
            face(c, cx, cy, r, p)
            rim(c, cx, cy, r, 7f)
            // Warm halo band all around the scale, like the lit bezel in the reference.
            glowArc(c, band, 135f, 270f, p.accent, 6f, 14f)
            stroke.shader = null
            stroke.color = alpha(p.accent, 0x50)
            stroke.strokeWidth = 7f
            c.drawArc(band, 135f, 270f, false, stroke)
            // A number every 10 km/h up to 120, every 20 above; four ticks per number.
            val step = if (speedMax <= 120) 10 else 20
            val ticks = speedMax / step * 4
            for (i in 0..ticks) {
                val angle = Math.toRadians(135.0 + 270.0 * i / ticks)
                val major = i % 4 == 0
                val inner = r - if (major) 38f else 29f
                val outer = r - 22f
                stroke.color = if (major) p.fg else p.muted
                stroke.strokeWidth = if (major) 2.4f else 1.1f
                c.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                    cx + cos(angle).toFloat() * outer, cy + sin(angle).toFloat() * outer, stroke)
                if (major) {
                    centerText(c, (step * i / 4).toString(), cx + cos(angle).toFloat() * (r - 54f),
                        cy + sin(angle).toFloat() * (r - 54f) + 6f, 17f, p.fg, fBold)
                }
            }
            glass(c, cx, cy, r - 8f)
        }
        live {
            val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
            glowArc(c, band, 135f, 270f * fraction, p.accent, 7f, 10f)
            stroke.shader = sweepShader(cx, cy, p.accent2, p.accent, 135f, 270f * fraction)
            stroke.strokeWidth = 7f
            c.drawArc(band, 135f, 270f * fraction, false, stroke)
            stroke.shader = null
            centerText(c, "km/h" + if (gps) " · GPS" else "", cx, cy - 30f, 16f, p.muted, fLabel)
            textWithGlow(c, value(m), cx, cy + 44f, 84f, p.fg, fValue, if (night) p.accent else null, center = true)
            drawNeedle(c, cx, cy, 20f, r - 40f, 135f + 270f * fraction, p.accent, 5f)
        }
    }

    /** Tapered needle with a soft shadow and a hub. */
    private fun drawNeedle(c: Canvas, cx: Float, cy: Float, from: Float, to: Float, deg: Float, color: Int, width: Float) {
        val a = Math.toRadians(deg.toDouble())
        val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        val nx = -uy; val ny = ux
        val needle = Path().apply {
            moveTo(cx + ux * from + nx * width / 2f, cy + uy * from + ny * width / 2f)
            lineTo(cx + ux * to, cy + uy * to)
            lineTo(cx + ux * from - nx * width / 2f, cy + uy * from - ny * width / 2f)
            close()
        }
        resetFx()
        fx.color = alpha(BLACK, if (night) 0xC0 else 0x50)
        fx.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        c.save(); c.translate(2f, 3f); c.drawPath(needle, fx); c.restore()
        if (night) glowPath(c, needle, color, 6f, 0xA0)
        fill.color = BLACK // opaque: a shader keeps the paint alpha
        fill.shader = LinearGradient(cx + ux * from, cy + uy * from, cx + ux * to, cy + uy * to,
            darken(color, .1f), lighten(color, .35f), Shader.TileMode.CLAMP)
        c.drawPath(needle, fill)
        fill.color = BLACK // opaque: a shader keeps the paint alpha
        fill.shader = RadialGradient(cx + ux * from - 2f, cy + uy * from - 2f, 8f,
            lighten(color, .5f), darken(color, .3f), Shader.TileMode.CLAMP)
        c.drawCircle(cx + ux * from, cy + uy * from, 6f, fill)
        fill.shader = null
    }

    private fun drawSmallDial(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette, rpm: Boolean) {
        if (m == null) return
        box.set(cx - r + 9f, cy - r + 9f, cx + r - 9f, cy + r - 9f)
        val arc = RectF(box)
        still {
            shadowCircle(c, cx, cy, r, 10f)
            face(c, cx, cy, r, p)
            rim(c, cx, cy, r, 5f)
            stroke.shader = null
            stroke.color = p.track
            stroke.strokeWidth = 5f
            c.drawArc(arc, 135f, 270f, false, stroke)
            if (rpm) {
                val redFrom = (DashboardMode.RPM_RED / m.max).toFloat()
                stroke.color = alpha(p.red, 0xB0)
                c.drawArc(arc, 135f + 270f * redFrom, 270f * (1f - redFrom), false, stroke)
            }
            val ticks = if (rpm) rpmK * 2 else 28
            for (i in 0..ticks) {
                val angle = Math.toRadians(135.0 + 270.0 * i / ticks)
                val major = i % 4 == 0
                stroke.color = if (major) p.fg else p.muted
                stroke.strokeWidth = if (major) 1.6f else .8f
                val inner = r - if (major) 22f else 17f
                c.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                    cx + cos(angle).toFloat() * (r - 13f), cy + sin(angle).toFloat() * (r - 13f), stroke)
            }
            if (rpm) {
                for (i in 0..rpmK / 2) {
                    val angle = Math.toRadians(135.0 + 270.0 * i * 2 / rpmK)
                    centerText(c, (i * 2).toString(), cx + cos(angle).toFloat() * (r - 31f),
                        cy + sin(angle).toFloat() * (r - 31f) + 4f, 10f, p.muted, fBold)
                }
                centerText(c, "rpm", cx, cy + 27f, 12f, p.muted, fLabel)
            } else {
                centerText(c, "Throttle", cx, cy + 28f, 12f, p.muted, fLabel)
            }
            glass(c, cx, cy, r - 6f)
        }
        live {
            val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
            val hot = rpm && (m.value ?: 0.0) >= DashboardMode.RPM_RED
            val color = if (hot) p.red else p.accent
            glowArc(c, arc, 135f, 270f * fraction, color, 5f, 7f)
            stroke.shader = sweepShader(cx, cy, p.accent2, color, 135f, 270f * fraction)
            stroke.strokeWidth = 5f
            c.drawArc(arc, 135f, 270f * fraction, false, stroke)
            stroke.shader = null
            if (rpm) textWithGlow(c, value(m), cx, cy + 9f, 27f, p.fg, fValue, if (night) color else null, center = true)
            else valueWithUnit(c, value(m), "%", cx, cy + 9f, 27f, p.fg, p.muted)
            drawNeedle(c, cx, cy, r - 40f, r - 14f, 135f + 270f * fraction, color, 3f)
        }
    }

    // ── RACING ────────────────────────────────────────────────────────────────────────────

    private fun drawRacing(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>, cards: List<Metric>) {
        still { textWithGlow(c, "RPM", 30f, 30f, 16f, p.fg, fValue, null) }
        drawRacingTach(c, a["rpm"], p, 30f, 34f, 742f, 44f)
        drawSpeedPlate(c, a["speed"], d.speedFromGps, p, 30f, 104f, 352f, 102f)
        drawRacingThrottle(c, a["throttle"], 528f, 204f, 98f, p)
        still { drawCheckers(c, 652f, 124f, p) }
        // Wide cards carry the racing stripes, as the second row of the reference does.
        DashLayout.cards(DashLayout.RACING, cards.size).forEachIndexed { i, b ->
            drawCard(c, cards[i], b, p, if (b.w >= 280f) CardLook.RACING_WIDE else CardLook.RACING)
        }
    }

    private fun drawRacingTach(c: Canvas, m: Metric?, p: Palette, x: Float, y: Float, w: Float, h: Float) {
        val skew = 14f
        val frame = Path().apply {
            moveTo(x + skew, y); lineTo(x + w, y); lineTo(x + w - skew, y + h); lineTo(x, y + h); close()
        }
        val count = rpmK
        val gap = 4f
        val inner = 8f
        val segW = (w - skew - inner * 2 - gap * (count - 1)) / count
        val top = y + inner
        val bottom = y + h - inner
        val max = m?.max ?: rpmMax.toDouble()
        fun seg(i: Int): Path {
            val sx = x + inner + skew * .5f + i * (segW + gap)
            return Path().apply {
                moveTo(sx + 6f, top); lineTo(sx + segW + 6f, top); lineTo(sx + segW, bottom); lineTo(sx, bottom); close()
            }
        }
        still {
            glowPath(c, frame, p.accent, 10f, if (night) 0x70 else 0x30)
            fill.color = BLACK // opaque: a shader keeps the paint alpha
            fill.shader = LinearGradient(0f, y, 0f, y + h, lighten(p.tile, if (night) .08f else 0f),
                darken(p.tile, if (night) .4f else .05f), Shader.TileMode.CLAMP)
            c.drawPath(frame, fill)
            fill.shader = null
            stroke.shader = null
            stroke.color = p.accent
            stroke.strokeWidth = 2.2f
            c.drawPath(frame, stroke)
            for (i in 0 until count) {
                fill.color = BLACK // opaque: a shader keeps the paint alpha
                fill.shader = LinearGradient(0f, top, 0f, bottom, lighten(p.track, .08f), darken(p.track, .15f), Shader.TileMode.CLAMP)
                c.drawPath(seg(i), fill)
            }
            fill.shader = null
            for (i in 0..rpmK / 2) {
                val vx = x + inner + skew * .5f + (w - skew - inner * 2) * i * 2f / rpmK
                centerText(c, (i * 2).toString(), vx, y + h + 18f, 15f, p.fg, fBold)
            }
        }
        live {
            val rpm = m?.value ?: 0.0
            val start = if (p == racingDay) 0xFFC4C7CD.toInt() else p.fg
            for (i in 0 until count) {
                val from = i * max / count
                if (rpm <= from) continue
                val color = if (from >= DashboardMode.RPM_RED) p.red
                else blend(start, p.accent2, (from / DashboardMode.RPM_RED).toFloat())
                val s = seg(i)
                if (night && from >= DashboardMode.RPM_RED - 2_000.0) glowPath(c, s, color, 6f, 0xB0)
                fill.color = BLACK // opaque: a shader keeps the paint alpha
                fill.shader = LinearGradient(0f, top, 0f, bottom, lighten(color, .25f), darken(color, .12f), Shader.TileMode.CLAMP)
                c.drawPath(s, fill)
            }
            fill.shader = null
            textWithGlow(c, value(m), x + w * .58f, y + h - 9f, 42f, if (night) p.fg else BLACK, fValue,
                if (night) BLACK else WHITE, center = true)
        }
    }

    private fun drawSpeedPlate(c: Canvas, m: Metric?, gps: Boolean, p: Palette, x: Float, y: Float, w: Float, h: Float) {
        if (m == null) return
        val cut = 22f
        val plate = Path().apply {
            moveTo(x + cut, y); lineTo(x + w, y); lineTo(x + w - cut, y + h); lineTo(x, y + h)
            lineTo(x, y + h - cut); lineTo(x + cut * .6f, y + cut * .5f); close()
        }
        still {
            glowPath(c, plate, p.accent, 12f, if (night) 0x80 else 0x30)
            fill.color = BLACK // opaque: a shader keeps the paint alpha
            fill.shader = LinearGradient(x, y, x + w * .4f, y + h, lighten(p.tile, if (night) .1f else 0f),
                darken(p.tile, if (night) .45f else .06f), Shader.TileMode.CLAMP)
            c.drawPath(plate, fill)
            fill.shader = null
            stroke.shader = null
            stroke.color = p.accent
            stroke.strokeWidth = 2.6f
            c.drawPath(plate, stroke)
            // Inner bevel line.
            stroke.color = alpha(if (night) WHITE else BLACK, 0x18)
            stroke.strokeWidth = 1f
            c.drawLine(x + cut + 4f, y + 5f, x + w - 6f, y + 5f, stroke)
            fill.color = p.accent
            for (i in 0..2) {
                val sx = x + w - 74f + i * 13f
                val chev = Path().apply {
                    moveTo(sx, y + h - 38f); lineTo(sx + 8f, y + h - 38f); lineTo(sx + 2f, y + h - 20f); lineTo(sx - 6f, y + h - 20f); close()
                }
                c.drawPath(chev, fill)
            }
        }
        live {
            textWithGlow(c, value(m), x + w * .40f, y + h * .64f, 76f, p.fg, fValue, if (night) p.accent else null, center = true)
            centerText(c, "km/h" + if (gps) " · GPS" else "", x + w * .40f, y + h - 10f, 17f, p.muted, fLabel)
        }
    }

    private fun drawRacingThrottle(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette) {
        if (m == null) return
        box.set(cx - r + 12f, cy - r + 12f, cx + r - 12f, cy + r - 12f)
        val arc = RectF(box)
        still {
            fill.shader = null
            fill.color = alpha(p.muted, 0xA0)
            for (i in 0..30) {
                val angle = Math.toRadians(180.0 + 180.0 * i / 30)
                c.drawCircle(cx + cos(angle).toFloat() * (r - 2f), cy + sin(angle).toFloat() * (r - 2f), 2.2f, fill)
            }
            stroke.shader = null
            stroke.color = p.track
            stroke.strokeWidth = 11f
            stroke.strokeCap = Paint.Cap.BUTT
            c.drawArc(arc, 180f, 180f, false, stroke)
            stroke.strokeCap = Paint.Cap.ROUND
            centerText(c, "Throttle", cx, cy - 2f, 16f, p.muted, fLabel)
        }
        live {
            val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
            glowArc(c, arc, 180f, 180f * fraction, p.accent, 11f, 9f)
            stroke.shader = sweepShader(cx, cy, p.accent2, p.accent, 180f, 180f * fraction)
            stroke.strokeWidth = 11f
            stroke.strokeCap = Paint.Cap.BUTT
            c.drawArc(arc, 180f, 180f * fraction, false, stroke)
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.shader = null
            valueWithUnit(c, value(m), "%", cx, cy - 22f, 34f, p.fg, p.muted)
        }
    }

    private fun drawCheckers(c: Canvas, x: Float, y: Float, p: Palette) {
        val size = 13f
        for (row in 0..4) for (col in 0..8) {
            fill.shader = null
            fill.color = if ((row + col) % 2 == 0) p.accent else if (night) 0xFFE9E9EC.toInt() else WHITE
            val off = sin(col * .7).toFloat() * 5f
            c.drawRect(x + col * size, y + row * size + off, x + (col + 1) * size, y + (row + 1) * size + off, fill)
        }
    }

    // ── TOURING ───────────────────────────────────────────────────────────────────────────

    private fun drawTouring(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>, cards: List<Metric>) {
        still { drawLandscape(c, p) }
        drawTouringSpeed(c, a["speed"], 205f, 190f, 168f, p, d.speedFromGps)
        drawTouringRpm(c, a["rpm"], p, 432f, 58f, 48f, 142f)
        drawTouringThrottle(c, a["throttle"], 652f, 118f, 88f, p)
        DashLayout.cards(DashLayout.TOURING, cards.size).forEachIndexed { i, b -> drawCard(c, cards[i], b, p, CardLook.TOURING) }
    }

    private fun drawTouringSpeed(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette, gps: Boolean) {
        if (m == null) return
        val segments = 44
        val start = 168.0
        val sweep = 204.0
        fun tick(i: Int) {
            val a0 = Math.toRadians(start + sweep * i / segments)
            val inner = r - 26f
            c.drawLine(cx + cos(a0).toFloat() * inner, cy + sin(a0).toFloat() * inner,
                cx + cos(a0).toFloat() * r, cy + sin(a0).toFloat() * r, stroke)
        }
        still {
            stroke.shader = null
            stroke.color = p.track
            stroke.strokeWidth = 4.5f
            for (i in 0 until segments) tick(i)
        }
        live {
            val fraction = ((m.value ?: 0.0) / m.max).coerceIn(0.0, 1.0)
            val lit = (0 until segments).filter { (it + 1.0) / segments <= fraction }
            if (lit.isNotEmpty() && night) {
                box.set(cx - r + 13f, cy - r + 13f, cx + r - 13f, cy + r - 13f)
                glowArc(c, box, start.toFloat(), (sweep * (lit.last() + 1) / segments).toFloat(), p.accent, 20f, 12f)
            }
            stroke.strokeWidth = 4.5f
            for (i in lit) {
                stroke.color = blend(p.accent, p.accent2, i.toFloat() / segments)
                tick(i)
            }
            textWithGlow(c, value(m), cx, cy - 34f, 86f, p.fg, fValue, if (night) p.accent else null, center = true)
            centerText(c, "km/h" + if (gps) " · GPS" else "", cx, cy - 8f, 19f, p.fg, fLabel)
        }
    }

    /** Hills and pines under the speed arc (baseline [G]), as in the reference. */
    private fun drawLandscape(c: Canvas, p: Palette) {
        val g = 206f
        val far = Path().apply {
            moveTo(30f, g); lineTo(90f, g - 68f); lineTo(140f, g - 42f); lineTo(210f, g - 86f)
            lineTo(262f, g - 50f); lineTo(318f, g - 76f); lineTo(392f, g); close()
        }
        resetFx()
        fx.shader = LinearGradient(0f, g - 86f, 0f, g, alpha(p.accent, if (night) 0x58 else 0x48),
            alpha(p.accent, 0x08), Shader.TileMode.CLAMP)
        c.drawPath(far, fx)
        val near = Path().apply {
            moveTo(30f, g); lineTo(70f, g - 32f); lineTo(120f, g - 16f); lineTo(176f, g - 44f)
            lineTo(250f, g - 12f); lineTo(300f, g - 30f); lineTo(392f, g); close()
        }
        fx.shader = LinearGradient(0f, g - 44f, 0f, g, alpha(p.accent, if (night) 0x70 else 0x60),
            alpha(p.accent, 0x10), Shader.TileMode.CLAMP)
        c.drawPath(near, fx)
        fill.shader = null
        fill.color = alpha(p.accent, if (night) 0x90 else 0x80)
        for ((tx, th) in listOf(340f to 34f, 358f to 46f, 376f to 30f, 394f to 40f)) {
            val tree = Path().apply {
                moveTo(tx, g - th); lineTo(tx + 7f, g - th * .55f); lineTo(tx + 4f, g - th * .55f)
                lineTo(tx + 10f, g); lineTo(tx - 10f, g); lineTo(tx - 4f, g - th * .55f)
                lineTo(tx - 7f, g - th * .55f); close()
            }
            c.drawPath(tree, fill)
        }
    }

    /** Fourteen segments on the 0–14 ×1000 scale; red from RPM_RED. */
    private fun drawTouringRpm(c: Canvas, m: Metric?, p: Palette, x: Float, y: Float, width: Float, height: Float) {
        val count = rpmK
        val gap = 2.5f
        val segH = (height - gap * (count - 1)) / count
        val max = m?.max ?: rpmMax.toDouble()
        fun seg(i: Int): RectF {
            val bottom = y + height - i * (segH + gap)
            return RectF(x, bottom - segH, x + width, bottom)
        }
        still {
            centerText(c, "RPM", x + width / 2f, y - 32f, 13f, p.muted, fLabel)
            for (i in 0 until count) {
                fill.color = BLACK // opaque: a shader keeps the paint alpha
                fill.shader = LinearGradient(x, 0f, x + width, 0f, darken(p.track, .1f), lighten(p.track, .1f), Shader.TileMode.CLAMP)
                c.drawRoundRect(seg(i), 2f, 2f, fill)
            }
            fill.shader = null
            for (i in 0..rpmK / 2) {
                val yy = y + height - height * (i * 2f / rpmK)
                txt(c, (i * 2).toString(), x + width + 8f, yy + 4f, 12f, p.fg, fBold)
            }
        }
        live {
            val rpm = m?.value ?: 0.0
            for (i in 0 until count) {
                val from = i * max / count
                if (rpm <= from) continue
                val color = if (from >= DashboardMode.RPM_RED) p.red else p.accent
                val r = seg(i)
                if (night) {
                    resetFx()
                    fx.color = alpha(color, 0x90)
                    fx.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL)
                    c.drawRoundRect(r, 2f, 2f, fx)
                }
                fill.color = BLACK // opaque: a shader keeps the paint alpha
                fill.shader = LinearGradient(x, 0f, x + width, 0f, darken(color, .1f), lighten(color, .3f), Shader.TileMode.CLAMP)
                c.drawRoundRect(r, 2f, 2f, fill)
            }
            fill.shader = null
            textWithGlow(c, value(m), x + width / 2f, y - 9f, 24f, p.fg, fValue, null, center = true)
        }
    }

    private fun drawTouringThrottle(c: Canvas, m: Metric?, cx: Float, cy: Float, r: Float, p: Palette) {
        if (m == null) return
        box.set(cx - r + 8f, cy - r + 8f, cx + r - 8f, cy + r - 8f)
        val arc = RectF(box)
        still {
            shadowCircle(c, cx, cy, r, 10f)
            face(c, cx, cy, r, p)
            stroke.shader = null
            stroke.color = p.track
            stroke.strokeWidth = 9f
            c.drawArc(arc, 0f, 360f, false, stroke)
            glass(c, cx, cy, r - 4f)
            centerText(c, "Throttle", cx, cy + 28f, 15f, p.muted, fLabel)
        }
        live {
            val fraction = ((m.value ?: 0.0) / m.max).toFloat().coerceIn(0f, 1f)
            glowArc(c, arc, -90f, 360f * fraction, p.accent, 9f, 8f)
            stroke.shader = sweepShader(cx, cy, p.accent2, p.accent, -90f, 360f * fraction)
            stroke.strokeWidth = 9f
            c.drawArc(arc, -90f, 360f * fraction, false, stroke)
            stroke.shader = null
            valueWithUnit(c, value(m), "%", cx, cy + 6f, 34f, p.fg, p.muted)
        }
    }

    // ── v2.6 designs: CLEAN / PIT / CLASSIC / FOCUS ───────────────────────────────────────

    private const val AMBER = 0xFFFFB020.toInt()

    private fun whole(v: Double?) = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"

    /** Small cards at fixed spots, stacked from the first spot for the fields that are visible. */
    private fun smallCards(c: Canvas, p: Palette, ms: List<Metric?>, x: Float, y: Float, w: Float, h: Float, gap: Float) {
        ms.filterNotNull().forEachIndexed { i, m ->
            drawCard(c, m, DashLayout.Box(x, y + i * (h + gap), w, h), p, CardLook.SPORT)
        }
    }

    // WINDOW: Android Auto goes in AaWindowLayout.window (the compositor draws it over a bare frame);
    // the data column beside it is laid out by AaColumnLayout. Real canvas pixels, no virtual scale.
    private fun drawWindow(
        ctx: Context, c: Canvas, d: DashboardMode.Data, p: Palette,
        a: Map<String, Metric>, cards: List<Metric>,
    ) {
        val w = c.width; val h = c.height
        val win = AaWindowLayout.window(w, h)
        val col = AaWindowLayout.column(w, h)
        val rows = AaColumnLayout.rows(col, cards.size.coerceAtMost(AaColumnLayout.MAX_CARDS))
        val k = h / H // text scale: the other designs are tuned for 416 px of height
        still {
            // Background is already p.bg; a thin frame marks where Android Auto goes.
            stroke.shader = null
            stroke.color = p.edge
            stroke.strokeWidth = maxOf(1.5f, 2f * k)
            box.set(win.x.toFloat(), win.y.toFloat(), (win.x + win.w).toFloat(), (win.y + win.h).toFloat())
            c.drawRoundRect(box, 8f * k, 8f * k, stroke)
        }
        val cx = rows.speed.x + rows.speed.w / 2f
        val speed = a["speed"]
        if (speed != null) {
            val size0 = rows.speed.h * .85f
            text.typeface = fValue; text.textSize = size0
            val vw = text.measureText("888")
            text.typeface = fLabel; text.textSize = size0 * .3f
            val fit = DashLayout.fitScale(rows.speed.w, vw + 6f + text.measureText("km/h"))
            val size = size0 * fit
            val base = rows.speed.bottom - rows.speed.h * .08f
            live {
                text.typeface = fValue; text.textSize = size
                val v = value(speed)
                val vwNow = text.measureText(v)
                text.typeface = fLabel; text.textSize = size * .3f
                val uw = text.measureText(speed.unit)
                val x0 = cx - (vwNow + 6f + uw) / 2f
                textWithGlow(c, v, x0, base, size, p.fg, fValue, if (night) p.accent else null)
                txt(c, speed.unit, x0 + vwNow + 6f, base, size * .3f, p.muted, fLabel)
            }
        }
        val rpm = a["rpm"]
        if (rpm != null) {
            val redFrom = DashDesignMath.redFraction(DashboardMode.RPM_RED, rpmMax.toDouble())
            val b = rows.bar
            val rad = b.h / 2f
            still {
                fill.shader = null
                fill.color = p.track
                box.set(b.x, b.y, b.right, b.bottom)
                c.drawRoundRect(box, rad, rad, fill)
                fill.color = alpha(p.red, 0x59)
                box.set(b.x + b.w * redFrom, b.y, b.right, b.bottom)
                c.drawRoundRect(box, rad, rad, fill)
            }
            live {
                val f = DashDesignMath.fraction(rpm.value, rpm.max)
                val red = (rpm.value ?: 0.0) >= DashboardMode.RPM_RED
                val label = if (rpm.value == null) "— ×1000 rpm"
                    else String.format(Locale.US, "%.1f ×1000 rpm", (rpm.value ?: 0.0) / 1000.0)
                rightText(c, label, b.right, rows.rpmLabelY, rows.rpmLabelSize, if (red) p.red else p.muted, fSemi)
                if (f > 0f) {
                    fill.shader = null
                    fill.color = p.accent
                    val end = b.x + b.w * f
                    box.set(b.x, b.y, minOf(end, b.x + b.w * redFrom).coerceAtLeast(b.x + b.h), b.bottom)
                    c.drawRoundRect(box, rad, rad, fill)
                    if (f > redFrom) {
                        fill.color = p.red
                        box.set(b.x + b.w * redFrom, b.y, end, b.bottom)
                        c.drawRoundRect(box, rad, rad, fill)
                    }
                }
            }
        }
        cards.take(rows.cards.size).forEachIndexed { i, m -> drawCard(c, m, rows.cards[i], p, CardLook.SPORT) }
        live {
            if (!d.obdLive) txt(c, ctx.getString(R.string.dash_no_obd), col.x.toFloat(), col.y + 14f * k, 13f * k, p.muted, fLabel)
        }
    }

    // CLEAN: huge speed, thin 270 degree RPM arc, three small readings.
    private fun drawClean(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        val cx = 400f; val cy = 172f; val r = 150f
        val redFrom = DashDesignMath.redFraction(DashboardMode.RPM_RED, rpmMax.toDouble())
        box.set(cx - r, cy - r, cx + r, cy + r)
        val oval = RectF(box)
        val rpm = a["rpm"]
        if (rpm != null) {
            still {
                stroke.shader = null
                stroke.strokeWidth = 16f
                stroke.color = p.track
                c.drawArc(oval, DashDesignMath.ARC_START, DashDesignMath.ARC_SWEEP, false, stroke)
                stroke.color = alpha(p.red, 0x59)
                c.drawArc(oval, DashDesignMath.ARC_START + DashDesignMath.ARC_SWEEP * redFrom,
                    DashDesignMath.sweep(redFrom, 1f), false, stroke)
            }
            live {
                val f = DashDesignMath.fill(DashDesignMath.fraction(rpm.value, rpm.max), redFrom)
                stroke.shader = null
                stroke.strokeWidth = 16f
                val accentSweep = DashDesignMath.sweep(0f, f.accentTo)
                glowArc(c, oval, DashDesignMath.ARC_START, accentSweep, p.accent, 16f, 10f)
                stroke.color = p.accent
                if (accentSweep > 0f) c.drawArc(oval, DashDesignMath.ARC_START, accentSweep, false, stroke)
                if (f.hasRed) {
                    val start = DashDesignMath.ARC_START + DashDesignMath.ARC_SWEEP * f.redFrom
                    val sweep = DashDesignMath.sweep(f.redFrom, f.redTo)
                    glowArc(c, oval, start, sweep, p.red, 16f, 10f)
                    stroke.color = p.red
                    c.drawArc(oval, start, sweep, false, stroke)
                }
                val k = (rpm.value ?: 0.0) / 1000.0
                val label = if (rpm.value == null) "— ×1000 rpm" else String.format(Locale.US, "%.1f ×1000 rpm", k)
                centerText(c, label, cx, 300f, 20f, if ((rpm.value ?: 0.0) >= DashboardMode.RPM_RED) p.red else p.accent, fSemi)
            }
        }
        val speed = a["speed"]
        if (speed != null) {
            still { centerText(c, "KM/H", cx, 262f, 24f, p.muted, fLabel) }
            live { textWithGlow(c, value(speed), cx, 222f, 170f, p.fg, fValue, if (night) p.accent else null, center = true) }
        }
        smallCards(c, p, listOf(a["coolant"], a["volt"]), 26f, 96f, 150f, 70f, 14f)
        smallCards(c, p, listOf(a["throttle"]), 624f, 120f, 150f, 70f, 14f)
    }

    // PIT: wide 24 segment RPM bar with a scale, big speed on the left, up to four cards in a grid.
    private fun drawPit(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>, cards: List<Metric>) {
        val n = 24; val gap = 4f
        val x0 = 30f; val bw = 740f; val y0 = 26f; val bh = 36f
        val sw = (bw - gap * (n - 1)) / n
        val redFrom = DashDesignMath.redFraction(DashboardMode.RPM_RED, rpmMax.toDouble())
        val firstRed = DashDesignMath.firstRedSegment(n, redFrom)
        val rpm = a["rpm"]
        if (rpm != null) {
            still {
                for (i in 0 until n) {
                    box.set(x0 + i * (sw + gap), y0, x0 + i * (sw + gap) + sw, y0 + bh)
                    fill.shader = null
                    fill.color = if (i >= firstRed) alpha(p.red, 0x40) else p.track
                    c.drawRoundRect(box, 3f, 3f, fill)
                }
                for ((f, label) in DashDesignMath.barLabels(rpmK)) centerText(c, label, x0 + bw * f, 84f, 15f, p.muted, fSemi)
                txt(c, "rpm ×1000", x0, 108f, 14f, p.muted, fLabel)
            }
            live {
                val lit = DashDesignMath.litSegments(DashDesignMath.fraction(rpm.value, rpm.max), n)
                for (i in 0 until lit) {
                    val color = if (i >= firstRed) p.red else p.accent
                    box.set(x0 + i * (sw + gap), y0, x0 + i * (sw + gap) + sw, y0 + bh)
                    if (night) {
                        resetFx()
                        fx.color = alpha(color, 0x90)
                        fx.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
                        c.drawRoundRect(box, 3f, 3f, fx)
                    }
                    fill.shader = null
                    fill.color = color
                    c.drawRoundRect(box, 3f, 3f, fill)
                }
            }
        }
        val speed = a["speed"]
        if (speed != null) {
            still { txt(c, "KM/H", 52f, 296f, 24f, p.muted, fLabel) }
            live { textWithGlow(c, value(speed), 40f, 262f, 210f, p.fg, fValue, if (night) p.accent else null) }
        }
        val shown = cards.take(4)
        DashLayout.cards(DashLayout.PIT, shown.size).forEachIndexed { i, b -> drawCard(c, shown[i], b, p, CardLook.SPORT) }
    }

    // CLASSIC: two round dials with needles, a small centre panel with coolant and voltage.
    private fun drawClassic(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        a["speed"]?.let { drawClassicDial(c, it, 200f, 165f, p, DashDesignMath.speedScale(speedMax), false, "KM/H") }
        a["rpm"]?.let { drawClassicDial(c, it, 600f, 165f, p, DashDesignMath.rpmScale(rpmK), true, "×1000 RPM") }
        smallCards(c, p, listOf(a["coolant"], a["volt"]), 340f, 96f, 120f, 66f, 10f)
    }

    private fun drawClassicDial(
        c: Canvas, m: Metric, cx: Float, cy: Float, p: Palette,
        scale: DashDesignMath.DialScale, rpm: Boolean, caption: String,
    ) {
        val r = 124f
        val redFrom = DashDesignMath.redFraction(DashboardMode.RPM_RED, m.max)
        still {
            shadowCircle(c, cx, cy, r + 10f, 12f)
            face(c, cx, cy, r + 10f, p)
            rim(c, cx, cy, r + 10f, 6f)
            for (i in 0..scale.ticks) {
                val angle = Math.toRadians(DashDesignMath.tickAngle(i, scale.ticks).toDouble())
                val major = scale.isMajor(i)
                val inner = r - if (major) 16f else 9f
                stroke.shader = null
                stroke.color = if (major) p.fg else p.muted
                stroke.strokeWidth = if (major) 3f else 1.5f
                c.drawLine(cx + cos(angle).toFloat() * inner, cy + sin(angle).toFloat() * inner,
                    cx + cos(angle).toFloat() * (r - 2f), cy + sin(angle).toFloat() * (r - 2f), stroke)
            }
            val labelSize = if (scale.labels().size > 9) 15f else 17f
            for ((i, v) in scale.labels()) {
                val angle = Math.toRadians(DashDesignMath.tickAngle(i, scale.ticks).toDouble())
                centerText(c, v.toString(), cx + cos(angle).toFloat() * (r - 34f),
                    cy + sin(angle).toFloat() * (r - 34f) + 6f, labelSize, p.muted, fSemi)
            }
            if (rpm) {
                box.set(cx - (r - 8f), cy - (r - 8f), cx + (r - 8f), cy + (r - 8f))
                stroke.shader = null
                stroke.color = p.red
                stroke.strokeWidth = 7f
                c.drawArc(box, DashDesignMath.ARC_START + DashDesignMath.ARC_SWEEP * redFrom,
                    DashDesignMath.sweep(redFrom, 1f), false, stroke)
            }
            centerText(c, caption, cx, cy + if (rpm) 70f else 86f, 14f, p.muted, fSemi)
            glass(c, cx, cy, r + 2f)
        }
        live {
            val f = DashDesignMath.fraction(m.value, m.max)
            val color = if (rpm && (m.value ?: 0.0) >= DashboardMode.RPM_RED) p.red else p.accent
            drawNeedle(c, cx, cy, 0f, r - 22f, DashDesignMath.ARC_START + DashDesignMath.ARC_SWEEP * f, color, 6f)
            if (!rpm) textWithGlow(c, value(m), cx, cy + 62f, 44f, p.fg, fValue, null, center = true)
        }
    }

    // FOCUS: one huge reading; the hero follows the situation (see FocusHero), the rest is dimmed.
    private fun drawFocus(c: Canvas, d: DashboardMode.Data, p: Palette, a: Map<String, Metric>) {
        val dim = alpha(p.muted, 0xCC)
        val redFrom = DashDesignMath.redFraction(DashboardMode.RPM_RED, rpmMax.toDouble())
        val bx = 170f; val bw = 460f; val by = 306f; val bh = 6f
        still {
            box.set(bx, by, bx + bw, by + bh)
            fill.shader = null
            fill.color = p.track
            c.drawRoundRect(box, 3f, 3f, fill)
        }
        live {
            val (label, v, unit, color) = when (hero) {
                FocusHero.Hero.COOLANT -> HeroView("COOLANT", whole(d.coolant), "°C", when (DashboardMode.coolantLevel(d.coolant ?: 0.0)) {
                    DashboardMode.Level.ALERT -> p.red
                    DashboardMode.Level.WARN -> AMBER
                    DashboardMode.Level.NORMAL -> p.fg
                })
                FocusHero.Hero.VOLT -> HeroView("VOLTAGE", d.volt?.let { String.format(Locale.US, "%.1f", it) } ?: "—", "V", p.fg)
                FocusHero.Hero.SPEED -> HeroView("SPEED", if ("speed" in a) value(a["speed"]) else "—", "km/h", p.fg)
            }
            centerText(c, label, 400f, 36f, 20f, p.muted, fSemi)
            for (i in 0..2) {
                fill.shader = null
                fill.color = if (i == hero.ordinal) p.fg else alpha(p.muted, 0x70)
                c.drawCircle(372f + 28f * i, 54f, 5f, fill)
            }
            textWithGlow(c, v, 400f, 262f, 232f, color, fValue, if (night) color else null, center = true)
            centerText(c, unit, 400f, 292f, 24f, p.muted, fLabel)
            // Dimmed corners: RPM left, coolant right (the speed instead while coolant is the hero).
            if ("rpm" in a) txt(c, whole(d.rpm) + " rpm", 40f, 60f, 22f, dim, fSemi)
            if (hero == FocusHero.Hero.COOLANT) {
                if ("speed" in a) rightText(c, whole(d.speedKmh) + " km/h", 760f, 60f, 22f, dim, fSemi)
            } else if ("coolant" in a) {
                rightText(c, whole(d.coolant) + " °C", 760f, 60f, 22f, dim, fSemi)
            }
            if ("rpm" in a) {
                val f = DashDesignMath.fill(DashDesignMath.fraction(d.rpm, rpmMax.toDouble()), redFrom)
                fun seg(from: Float, to: Float, color: Int) {
                    box.set(bx + bw * from, by, bx + bw * to, by + bh)
                    fill.color = color
                    c.drawRoundRect(box, 3f, 3f, fill)
                }
                if (f.accentTo > 0f) seg(0f, f.accentTo, p.accent)
                if (f.hasRed) seg(f.redFrom, f.redTo, p.red)
            }
        }
    }

    private data class HeroView(val label: String, val value: String, val unit: String, val color: Int)

    // ── cards ─────────────────────────────────────────────────────────────────────────────

    private enum class CardLook { SPORT, RACING, RACING_WIDE, TOURING }

    /**
     * Icon on the left, title above, value with its unit right after it (as in the reference). 2.5: the
     * card is a [DashLayout.Box] and everything inside follows its height; text that would run past the
     * right edge is shrunk to fit.
     */
    private fun drawCard(c: Canvas, m: Metric, b: DashLayout.Box, p: Palette, look: CardLook) {
        val x = b.x; val y = b.y; val w = b.w; val h = b.h
        val t = DashLayout.cardText(b)
        val radius = if (look == CardLook.TOURING) 10f else 7f
        val racing = look == CardLook.RACING || look == CardLook.RACING_WIDE
        // Wide racing cards keep their stripes clear of the text.
        val textRight = x + w - (if (look == CardLook.RACING_WIDE) 62f else 10f)
        still {
            val r = RectF(x, y, x + w, y + h)
            shadowRoundRect(c, r, radius, 7f)
            if (racing && night) {
                resetFx()
                fx.style = Paint.Style.STROKE
                fx.strokeWidth = 5f
                fx.color = alpha(p.accent, 0x60)
                fx.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
                c.drawRoundRect(r, radius, radius, fx)
            }
            fill.color = BLACK // opaque: a shader keeps the paint alpha
            fill.shader = LinearGradient(0f, y, 0f, y + h, lighten(p.tile, if (night) .09f else 0f),
                darken(p.tile, if (night) .35f else .05f), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, radius, radius, fill)
            fill.shader = null
            stroke.shader = null
            stroke.color = if (racing) p.accent else p.edge
            stroke.strokeWidth = 1.3f
            c.drawRoundRect(r, radius, radius, stroke)
            // Top light edge.
            stroke.color = alpha(WHITE, if (night) 0x1C else 0xB0)
            stroke.strokeWidth = 1f
            c.drawLine(x + radius, y + 1.5f, x + w - radius, y + 1.5f, stroke)
            if (look == CardLook.RACING_WIDE) {
                fill.color = alpha(p.accent, 0xDD)
                for (i in 0..3) {
                    val sx = x + w - 54f + i * 10f
                    val stripe = Path().apply {
                        moveTo(sx + 8f, y + 9f); lineTo(sx + 13f, y + 9f); lineTo(sx + 5f, y + h - 9f); lineTo(sx, y + h - 9f); close()
                    }
                    c.drawPath(stripe, fill)
                }
            }
            drawMetricIcon(c, m.id, t.iconX, y + h / 2f, t.iconR, p.fg)
            text.typeface = fLabel; text.textSize = t.titleSize
            val titleSize = t.titleSize * DashLayout.fitScale(textRight - t.textX, text.measureText(m.title))
            txt(c, m.title, t.textX, t.titleY, titleSize, p.muted, fLabel)
        }
        live {
            val warn = m.id == "coolant" && (m.value ?: 0.0) >= DashboardMode.COOLANT_AMBER
            if (warn) drawMetricIcon(c, m.id, t.iconX, y + h / 2f, t.iconR, p.red)
            val v = value(m)
            text.typeface = fValue; text.textSize = t.valueSize
            val vw = text.measureText(v)
            text.typeface = fLabel; text.textSize = t.valueSize * .58f
            val uw = text.measureText(m.unit)
            val k = DashLayout.fitScale(textRight - t.textX, vw + 4f + uw)
            val size = t.valueSize * k
            txt(c, v, t.textX, t.valueY, size, if (warn) p.red else p.fg, fValue)
            txt(c, m.unit, t.textX + vw * k + 4f * k, t.valueY, size * .58f, p.fg, fLabel)
        }
    }

    // ── icons ─────────────────────────────────────────────────────────────────────────────

    private fun drawMetricIcon(c: Canvas, id: String, cx: Float, cy: Float, r: Float, color: Int) {
        stroke.shader = null
        fill.shader = null
        stroke.color = color
        stroke.strokeWidth = 1.8f
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
        stroke.shader = null
        stroke.color = color
        stroke.strokeWidth = 1.8f
        for (r in listOf(4f, 8f, 12f)) {
            box.set(cx - r, cy - r, cx + r, cy + r)
            c.drawArc(box, 225f, 90f, false, stroke)
        }
        fill.shader = null
        fill.color = color
        c.drawCircle(cx, cy - 1f, 1.8f, fill)
    }

    private fun drawBluetooth(c: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.shader = null
        stroke.color = color
        stroke.strokeWidth = 1.6f
        c.drawLine(cx, cy - 8f, cx, cy + 8f, stroke)
        c.drawLine(cx, cy - 8f, cx + 5f, cy - 4f, stroke)
        c.drawLine(cx + 5f, cy - 4f, cx - 5f, cy + 4f, stroke)
        c.drawLine(cx, cy + 8f, cx + 5f, cy + 4f, stroke)
        c.drawLine(cx + 5f, cy + 4f, cx - 5f, cy - 4f, stroke)
    }

    private fun drawPhone(c: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.shader = null
        stroke.color = color
        stroke.strokeWidth = 1.6f
        box.set(cx - 5f, cy - 9f, cx + 5f, cy + 9f)
        c.drawRoundRect(box, 2f, 2f, stroke)
        fill.shader = null
        fill.color = color
        box.set(cx - 2.5f, cy - 2f, cx + 2.5f, cy + 6f)
        c.drawRect(box, fill)
    }

    private fun drawHeadlight(c: Canvas, cx: Float, cy: Float, color: Int) {
        stroke.shader = null
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

    /** Text with a soft coloured bloom behind it (night) and a slight drop shadow. */
    private fun textWithGlow(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface,
        glow: Int?, center: Boolean = false,
    ) {
        text.typeface = face
        text.textSize = size
        val left = if (center) x - text.measureText(s) / 2f else x
        if (glow != null) {
            text.color = alpha(glow, 0xB0)
            text.maskFilter = BlurMaskFilter(size * .18f, BlurMaskFilter.Blur.NORMAL)
            c.drawText(s, left, y, text)
            text.maskFilter = null
        }
        text.color = alpha(BLACK, if (night) 0xA0 else 0x30)
        text.maskFilter = BlurMaskFilter(2.5f, BlurMaskFilter.Blur.NORMAL)
        c.drawText(s, left + 1.5f, y + 2f, text)
        text.maskFilter = null
        text.color = color
        c.drawText(s, left, y, text)
    }

    private fun valueWithUnit(c: Canvas, v: String, unit: String, cx: Float, y: Float, size: Float, color: Int, unitColor: Int) {
        text.typeface = fValue; text.textSize = size
        val vw = text.measureText(v)
        text.typeface = fLabel; text.textSize = size * .5f
        val uw = text.measureText(unit)
        val x0 = cx - (vw + 3f + uw) / 2f
        textWithGlow(c, v, x0, y, size, color, fValue, null)
        txt(c, unit, x0 + vw + 3f, y, size * .5f, unitColor, fLabel)
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface) {
        text.typeface = face
        text.textSize = size
        text.color = color
        text.maskFilter = null
        c.drawText(s, x, y, text)
    }

    private fun rightText(c: Canvas, s: String, right: Float, y: Float, size: Float, color: Int, face: Typeface) {
        text.typeface = face
        text.textSize = size
        text.color = color
        text.maskFilter = null
        c.drawText(s, right - text.measureText(s), y, text)
    }

    private fun centerText(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface) {
        text.typeface = face
        text.textSize = size
        text.color = color
        text.maskFilter = null
        c.drawText(s, x - text.measureText(s) / 2f, y, text)
    }
}
