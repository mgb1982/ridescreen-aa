// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** 🚦 Your red lights + same-trip route comparison (see [TrafficLights]). */
class TrafficLightsActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var map: MapView
    private lateinit var info: TextView
    private lateinit var lights: LinearLayout
    private lateinit var routes: LinearLayout
    private lateinit var wave: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var scroll: ScrollView
    private var result: TrafficLights.Result? = null
    private var selected: TrafficLights.JunctionStats? = null
    private val lightCards = HashMap<Pair<Double, Double>, MutableList<MaterialCardView>>()
    private val waveCards = HashMap<Pair<Double, Double>, MutableList<MaterialCardView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GpxOsmdroid.configure(this)  // v2.3 · shared persistent tile cache (90 days), so maps load instantly after an update
        enableEdgeToEdge()
        scroll = ScrollView(this).apply { setBackgroundColor(color(R.color.bg)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(28)) }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            setText(R.string.tl_title)
            textSize = 24f
            setTextColor(color(R.color.text_primary))
            typeface = ResourcesCompat.getFont(context, R.font.archivo_black)
        })
        status = body(getString(R.string.tl_progress_trips))
        root.addView(status)

        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(41.3874, 2.1686))
        }
        // The map scrolls itself; don't let the page steal its drags.
        map.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        val mapCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            addView(map, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        }
        root.addView(mapCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)).apply { topMargin = dp(10) })
        info = body(getString(R.string.tl_tap_hint))
        root.addView(info)

        root.addView(header(getString(R.string.tl_worst_title)))
        lights = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(lights)
        root.addView(header(getString(R.string.gw_title)))
        root.addView(body(getString(R.string.gw_intro)))
        wave = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(wave)
        root.addView(header(getString(R.string.tl_routes_title)))
        routes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(routes)
        root.addView(body(getString(R.string.tl_footer)))

        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val s = insets.getInsets(SafeInsets.BARS)
            v.setPadding(s.left, s.top, s.right, s.bottom)
            insets
        }
        load()
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
    override fun onDestroy() { io.shutdownNow(); super.onDestroy() }

    private fun load() {
        io.execute {
            val r = try {
                TrafficLights.analyse(this) { msg -> runOnUiThread { status.text = msg } }
            } catch (e: Exception) {
                LogBus.log("[TL] analysis failed: $e")
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result = r
                render()
            }
            // Street names for the top junctions (slow: 1 request/s), then refresh the list.
            val top = r?.junctions?.take(10).orEmpty()
            var fetched = false
            for (j in top) {
                if (Thread.currentThread().isInterrupted) return@execute
                if (TrafficLights.cachedName(this, j.junction) == null) { TrafficLights.fetchName(this, j.junction); fetched = true }
            }
            if (fetched) runOnUiThread { if (!isFinishing && !isDestroyed) { renderLights(); renderWave() } }
        }
    }

    private fun render() {
        val r = result
        if (r == null) { status.setText(R.string.tl_failed); return }
        if (r.tripsUsed == 0) { status.setText(R.string.tl_no_trips); return }
        selected = null
        status.text = getString(R.string.tl_summary, r.tripsUsed, r.junctions.size) +
            if (r.offline) "\n" + getString(R.string.tl_partial) else ""
        map.overlays.clear()
        map.overlays.add(JunctionOverlay(r.junctions))
        if (r.junctions.isNotEmpty()) {
            val lats = r.junctions.map { it.junction.lat }; val lons = r.junctions.map { it.junction.lon }
            map.post {
                try {
                    map.zoomToBoundingBox(BoundingBox(lats.max() + 0.003, lons.max() + 0.003, lats.min() - 0.003, lons.min() - 0.003), false, dp(16))
                } catch (_: Exception) {}
            }
        }
        map.invalidate()
        renderLights()
        renderWave()
        renderRoutes(r)
    }

    /** v2.3 · 🌊 How well each approach's light could be predicted (observation only, nothing on the dash). */
    private fun renderWave() {
        val r = result ?: return
        wave.removeAllViews()
        waveCards.clear()
        val dirs = resources.getStringArray(R.array.tl_dirs)
        val items = r.junctions.flatMap { j -> j.approaches.mapNotNull { a -> a.wave?.let { Triple(j, a, it) } } }
            .filter { it.third.predicted > 0 || it.third.soonErrorsS.isNotEmpty() }
            .sortedWith(compareByDescending<Triple<TrafficLights.JunctionStats, TrafficLights.Approach, GreenWave.Score>> { it.third.ready }
                .thenByDescending { it.third.predicted })
        if (items.isEmpty()) {
            val learning = r.junctions.flatMap { it.approaches }.maxOfOrNull { it.stops } ?: 0
            wave.addView(body(getString(R.string.gw_empty, learning)))
            applyHighlight()
            return
        }
        for ((j, a, sc) in items.take(12)) {
            var text = if (sc.predicted > 0)
                getString(R.string.gw_hits, sc.correct, sc.predicted, (sc.accuracy * 100).roundToInt(), sc.unsure)
            else getString(R.string.gw_no_arrivals)
            sc.soonMedianS?.let { text += "\n" + getString(R.string.gw_soon, it, sc.soonErrorsS.size) }
            a.cycleS?.let { text += "\n" + getString(R.string.gw_cycle, it) }
            text += "\n" + if (sc.ready) getString(R.string.gw_ready)
                else getString(R.string.gw_learning, GreenWave.READY_PREDICTIONS, (GreenWave.READY_ACCURACY * 100).roundToInt())
            val title = "${junctionLabel(j)} · ${dirs[a.dir]}"
            val accent = when {
                sc.ready -> Color.rgb(67, 160, 71)
                sc.predicted >= 3 && sc.accuracy < 0.6f -> Color.rgb(229, 57, 53)
                else -> Color.rgb(251, 192, 45)
            }
            val c = card(title, text, accent) {
                select(j, fromMap = false)
                map.controller.animateTo(GeoPoint(j.junction.lat, j.junction.lon), 17.0, 600L)
                info.text = "$title\n$text"
            }
            wave.addView(c)
            waveCards.getOrPut(key(j)) { ArrayList() }.add(c)
        }
        applyHighlight()
    }

    private fun junctionLabel(j: TrafficLights.JunctionStats): String =
        TrafficLights.cachedName(this, j.junction) ?: String.format(java.util.Locale.ROOT, "%.4f, %.4f", j.junction.lat, j.junction.lon)

    private fun describe(j: TrafficLights.JunctionStats): String {
        val m = j.main
        val dir = resources.getStringArray(R.array.tl_dirs)[m.dir]
        var s = getString(R.string.tl_line, j.stops, j.passes, (j.stopRate * 100).roundToInt(), j.avgWaitS) +
            "\n" + getString(R.string.tl_main_dir, dir, m.stops, m.passes)
        m.cycleS?.let { s += "\n" + getString(R.string.tl_cycle, it, (m.cycleConfidence * 100).roundToInt()) }
        return s
    }

    private fun renderLights() {
        val r = result ?: return
        lights.removeAllViews()
        lightCards.clear()
        val top = r.junctions.filter { it.stops > 0 }.take(10)
        if (top.isEmpty()) { lights.addView(body(getString(R.string.tl_no_stops))); applyHighlight(); return }
        for (j in top) {
            val c = card(junctionLabel(j), describe(j), rateColor(j.stopRate)) {
                select(j, fromMap = false)
                map.controller.animateTo(GeoPoint(j.junction.lat, j.junction.lon), 17.0, 600L)
            }
            lights.addView(c)
            lightCards.getOrPut(key(j)) { ArrayList() }.add(c)
        }
        applyHighlight()
    }

    private fun key(j: TrafficLights.JunctionStats) = j.junction.lat to j.junction.lon
    private fun same(a: TrafficLights.JunctionStats?, b: TrafficLights.JunctionStats?) =
        a != null && b != null && key(a) == key(b)

    /** Single entry point for selecting a junction, from a list card or from a map tap. */
    private fun select(j: TrafficLights.JunctionStats, fromMap: Boolean) {
        selected = j
        info.text = "${junctionLabel(j)}\n${describe(j)}"
        applyHighlight()
        map.invalidate()
        if (!fromMap) return
        val card = lightCards[key(j)]?.firstOrNull() ?: waveCards[key(j)]?.firstOrNull()
        if (card != null) {
            scroll.post {
                val r = Rect(0, 0, card.width, card.height)
                scroll.offsetDescendantRectToMyCoords(card, r)
                scroll.smoothScrollTo(0, (r.top - dp(12)).coerceAtLeast(0))
            }
        }
        // The tap came from the map, so it is normally visible; only re-centre (same zoom) if it is off-screen.
        val p = Point()
        map.projection.toPixels(GeoPoint(j.junction.lat, j.junction.lon), p)
        if (p.x < 0 || p.y < 0 || p.x > map.width || p.y > map.height) {
            map.controller.animateTo(GeoPoint(j.junction.lat, j.junction.lon))
        }
    }

    private fun clearSelection() {
        if (selected == null) return
        selected = null
        applyHighlight()
        map.invalidate()
    }

    /** Thicker border + soft tint on every card of the selected junction; plain look on the rest. */
    private fun applyHighlight() {
        val sel = selected?.let { key(it) }
        for (m in listOf(lightCards, waveCards)) for ((k, cards) in m) for (c in cards) {
            val on = k == sel
            c.strokeWidth = if (on) dp(4) else dp(1)
            c.setCardBackgroundColor(if (on) ColorUtils.blendARGB(color(R.color.surface), c.strokeColor, 0.18f) else color(R.color.surface))
        }
    }

    private fun renderRoutes(r: TrafficLights.Result) {
        routes.removeAllViews()
        val compare = r.routes.filter { it.variants.size >= 2 }
        if (compare.isEmpty()) { routes.addView(body(getString(R.string.tl_no_routes))); return }
        for (g in compare.take(8)) {
            val best = g.variants.minByOrNull { it.medianMin }!!
            val text = g.variants.mapIndexed { i, v ->
                (if (v === best) "🏆 " else "") + getString(
                    R.string.tl_variant, i + 1, v.count, v.medianMin, v.medianStops, String.format("%.1f", v.medianKm),
                )
            }.joinToString("\n")
            routes.addView(card(g.name, text + "\n" + getString(R.string.tl_variant_hint), color(R.color.brand_gold)) {
                // Show each variant's latest trip, one after another: start with the best.
                TripMapActivity.start(this, best.sample.id)
            })
        }
    }

    private fun rateColor(rate: Float): Int = when {
        rate >= 0.6f -> Color.rgb(229, 57, 53)
        rate >= 0.3f -> Color.rgb(251, 192, 45)
        else -> Color.rgb(67, 160, 71)
    }

    private inner class JunctionOverlay(private val items: List<TrafficLights.JunctionStats>) : Overlay() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.WHITE }
        private val pt = Point()

        private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
        private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(200, 20, 20, 20) }
        private val bigRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.WHITE }
        private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 30, 30, 30) }
        private val labelText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        }

        private fun radius(j: TrafficLights.JunctionStats) = dp(5) + (dp(9) * (j.passes.coerceAtMost(20) / 20f))

        override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val proj = mapView.projection
            val sel = selected
            for (j in items) {
                if (sel != null && same(j, sel)) continue
                proj.toPixels(GeoPoint(j.junction.lat, j.junction.lon), pt)
                val rad = radius(j)
                fill.color = rateColor(j.stopRate)
                ring.color = Color.WHITE
                if (sel != null) { fill.alpha = 150; ring.alpha = 150 }
                c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), rad, fill)
                c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), rad, ring)
            }
            if (sel == null) return
            proj.toPixels(GeoPoint(sel.junction.lat, sel.junction.lon), pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            val rad = radius(sel) + dp(6)
            val base = rateColor(sel.stopRate)
            halo.color = Color.argb(90, Color.red(base), Color.green(base), Color.blue(base))
            c.drawCircle(x, y, rad + dp(6), halo)  // = base radius + dp(12)
            fill.color = base
            c.drawCircle(x, y, rad, fill)
            c.drawCircle(x, y, rad, bigRing)
            c.drawCircle(x, y, rad + 2.5f, edge)
            // Label above the circle, kept inside the visible map.
            val padH = dp(8).toFloat(); val padV = dp(4).toFloat(); val margin = dp(4).toFloat()
            val maxText = (mapView.width * 0.6f - 2 * padH).coerceAtLeast(dp(40).toFloat())
            val text = TextUtils.ellipsize(junctionLabel(sel), labelText, maxText, TextUtils.TruncateAt.END).toString()
            val fm = labelText.fontMetrics
            val w = labelText.measureText(text) + 2 * padH
            val h = (fm.descent - fm.ascent) + 2 * padV
            val left = (x - w / 2).coerceIn(margin, (mapView.width - w - margin).coerceAtLeast(margin))
            val top = (y - rad - dp(6) - h).coerceIn(margin, (mapView.height - h - margin).coerceAtLeast(margin))
            c.drawRoundRect(left, top, left + w, top + h, dp(8).toFloat(), dp(8).toFloat(), labelBg)
            c.drawText(text, left + padH, top + padV - fm.ascent, labelText)
        }

        override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
            val proj = mapView.projection
            val maxD = dp(28).toFloat()
            val cands = items.indices.mapNotNull { i ->
                val j = items[i]
                proj.toPixels(GeoPoint(j.junction.lat, j.junction.lon), pt)
                val d = kotlin.math.hypot(pt.x - e.x, pt.y - e.y)
                if (d <= maxD) i to d else null
            }.sortedBy { it.second }.map { it.first }
            val cur = items.indexOfFirst { same(it, selected) }.takeIf { it >= 0 }
            val pick = TlPick.next(cands, cur)
            if (pick == null) { clearSelection(); return false }
            select(items[pick], fromMap = true)
            return true
        }
    }

    private fun card(title: String, text: String, accent: Int, onClick: () -> Unit) = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        radius = dp(16).toFloat()
        setCardBackgroundColor(color(R.color.surface))
        setContentPadding(dp(14), dp(10), dp(14), dp(10))
        strokeColor = accent; strokeWidth = dp(1)
        setOnClickListener { onClick() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                this.text = title; textSize = 15f
                setTextColor(color(R.color.text_primary))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                this.text = text; textSize = 13f
                setTextColor(color(R.color.text_secondary))
            })
        })
    }

    private fun header(s: String) = TextView(this).apply {
        text = s; textSize = 16f
        setTextColor(color(R.color.text_primary))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun body(s: String) = TextView(this).apply {
        text = s; textSize = 13f
        setTextColor(color(R.color.text_secondary))
        setPadding(0, dp(6), 0, 0)
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        fun start(ctx: Context) = ctx.startActivity(Intent(ctx, TrafficLightsActivity::class.java))
    }
}
