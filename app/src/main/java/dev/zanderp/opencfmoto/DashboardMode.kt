// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.os.SystemClock

/**
 * v2.4 · custom dashboard: the bike's dash shows our own gauges (RPM, speed, coolant, throttle, battery
 * voltage) instead of Android Auto, drawn by [DashboardRenderer] into the same video the compositor sends.
 *
 * Read only: everything shown comes from [ObdLive] (the rider's own ELM327) and the phone's GPS. With no
 * OBD data the speed still comes from the GPS and the rest shows "—". Android Auto keeps running underneath
 * (see [AaCompositor.setLiveLayer]), so switching back is instant.
 *
 * Launchers: the Controls screen, `app.ridescreen.action.DASH` / `DASH_ON` / `DASH_OFF` for Tasker / Key
 * Mapper (see [ExternalControl]), the app-icon shortcut ([DashboardToggleActivity]) and the "start in the
 * dashboard" setting.
 */
object DashboardMode {
    enum class Theme(val id: String) {
        AUTO("auto"), LIGHT("light"), DARK("dark");

        companion object {
            fun byId(id: String?): Theme = entries.firstOrNull { it.id == id } ?: AUTO
        }
    }

    /** What the gauges show right now. Null fields draw as "—". */
    class Data(
        val rpm: Double?,
        val speedKmh: Double?,
        val speedFromGps: Boolean,
        val coolant: Double?,
        val throttle: Double?,
        val volt: Double?,
        val load: Double?, val intake: Double?, val baro: Double?, val absoluteLoad: Double?, val relativeThrottle: Double?,
        /** A fresh OBD reading exists (false: contact off, adapter away, or the radio lent to the keyless box). */
        val obdLive: Boolean,
    )

    // Red zone from 9,000 rpm (confirmed by Manel for the 125X). Every RPM gauge colours red from RPM_RED.
    // Coolant: amber from 105 °C (above the normal fan-controlled range), red from 115 °C.
    const val RPM_MAX = 10_000.0
    const val RPM_RED = 9_000.0
    const val COOLANT_AMBER = 105.0
    const val COOLANT_RED = 115.0

    private const val PREFS = "dash_dashboard"
    private const val OBD_FRESH_MS = 5_000L
    private const val GPS_MAX_AGE_MS = 5_000L
    private const val GPS_POLL_MS = 1_000L
    /** GPS speed jitters at a standstill; below this it is shown as 0. */
    private const val GPS_STILL_KMH = 2.0

    /** The rider wants the dashboard (kept across reconnects until the projection is stopped). */
    @Volatile var wanted = false
        private set

    @Volatile private var gpsAt = 0L
    @Volatile private var gpsKmh: Double? = null

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun theme(ctx: Context): Theme = Theme.byId(BikeScope.getString(prefs(ctx), ctx, "theme", null))
    fun setTheme(ctx: Context, t: Theme) = BikeScope.putString(prefs(ctx), ctx, "theme", t.id)

    enum class Design(val id: String) {
        SPORT("sport"), RACING("racing"), TOURING("touring"),
        CLEAN("clean"), PIT("pit"), CLASSIC("classic"), FOCUS("focus"),
        /** Android Auto window on the left, data column on the right (see [AaWindowLayout]). */
        WINDOW("window");
        companion object { fun byId(id: String?): Design = entries.firstOrNull { it.id == id } ?: SPORT }
    }
    /**
     * Gauge full scale, per bike (Controls ▸ dashboard). Defaults follow the bike picked in the app:
     * 140 km/h / 10,000 rpm for the 125 range, 240 km/h / 14,000 rpm otherwise.
     */
    val SPEED_MAX_OPTIONS = listOf(120, 140, 160, 200, 240)
    val RPM_MAX_OPTIONS = listOf(10_000, 12_000, 14_000)
    private fun small125(ctx: Context): Boolean = try {
        BikePicker.selected(ctx)?.dashKey == "125x"
    } catch (_: Exception) { false }
    fun speedMax(ctx: Context): Int {
        val v = prefs(ctx).getInt("speed_max" + (BikeScope.suffix(ctx)?.let { "#$it" } ?: ""), 0)
        return v.takeIf { it in SPEED_MAX_OPTIONS } ?: if (small125(ctx)) 140 else 240
    }
    fun setSpeedMax(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt("speed_max" + (BikeScope.suffix(ctx)?.let { "#$it" } ?: ""), v).apply()
    fun rpmMax(ctx: Context): Int {
        val v = prefs(ctx).getInt("rpm_max" + (BikeScope.suffix(ctx)?.let { "#$it" } ?: ""), 0)
        return v.takeIf { it in RPM_MAX_OPTIONS } ?: if (small125(ctx)) 10_000 else 14_000
    }
    fun setRpmMax(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt("rpm_max" + (BikeScope.suffix(ctx)?.let { "#$it" } ?: ""), v).apply()

    fun design(ctx: Context): Design = Design.byId(BikeScope.getString(prefs(ctx), ctx, "design", null))
    fun setDesign(ctx: Context, value: Design) {
        BikeScope.putString(prefs(ctx), ctx, "design", value.id)
        // The window design needs Android Auto's video: the saver (video focus LOSS) can't apply there.
        if (wanted) { val app = ctx.applicationContext; applySaver(app, saver(app) && !aaWindow(app)) }
    }

    /** 2.5 · the dashboard shows Android Auto in a window ([AaWindowLayout]). */
    fun aaWindow(ctx: Context): Boolean = design(ctx) == Design.WINDOW

    /** Sample snapshot for the style picker; copied because the renderer reuses its live bitmap. */
    fun preview(ctx: Context, design: Design, width: Int, height: Int): android.graphics.Bitmap {
        val sample = Data(
            rpm = 1832.0, speedKmh = 0.0, speedFromGps = false, coolant = 94.0,
            throttle = 10.0, volt = 14.1, load = 44.0, intake = 42.0,
            baro = 100.0, absoluteLoad = 32.0, relativeThrottle = 10.0, obdLive = true,
        )
        // 2.5: the rider's own fields, order and colour, so the picker shows what the bike will.
        return DashboardRenderer.render(
            ctx, width, height, sample, isLight(ctx), design, visibleFields(ctx), standalone = true,
            cardOrder = cardOrder(ctx), accent = accent(ctx),
        )
    }

    private val fieldIds = listOf("rpm", "speed", "coolant", "volt", "load", "intake", "baro", "absolute", "throttle")
    fun visibleFields(ctx: Context): Set<String> {
        val p = prefs(ctx)
        val suffix = BikeScope.suffix(ctx)
        val scopedKey = suffix?.let { "fields#$it" }
        val key = if (scopedKey != null && p.contains(scopedKey)) scopedKey else "fields"
        val saved = p.getStringSet(key, null)?.toSet() ?: return fieldIds.toSet()
        val migrationKey = "fields_throttle_migrated#$key"
        if (!p.getBoolean(migrationKey, false)) {
            // "throttle" used to mean PID 0x11 and "relative" meant PID 0x45.
            val migrated = (saved - "throttle" - "relative").toMutableSet()
            if ("relative" in saved) migrated.add("throttle")
            p.edit().putStringSet(key, migrated).putBoolean(migrationKey, true).apply()
            return migrated
        }
        return saved.intersect(fieldIds.toSet())
    }
    fun setVisibleFields(ctx: Context, fields: Set<String>) {
        val suffix = BikeScope.suffix(ctx)
        val key = suffix?.let { "fields#$it" } ?: "fields"
        prefs(ctx).edit()
            .putStringSet(key, fields.toSet())
            .putBoolean("fields_throttle_migrated#$key", true)
            .apply()
    }
    fun fieldIds(): List<String> = fieldIds

    // ── 2.5 · layout engine settings (per bike, like the fields)

    /** Fields shown as cards, in their default order. The others are dials or bars. */
    fun cardFields(): List<String> = DashLayout.DEFAULT_CARDS

    /** The rider's card order; ids it misses follow in the default order ([DashLayout.orderedCards]). */
    fun cardOrder(ctx: Context): List<String> =
        DashLayout.orderedCards(DashLayout.parseOrder(BikeScope.getString(prefs(ctx), ctx, "card_order", null)),
            DashLayout.DEFAULT_CARDS.toSet())
    fun setCardOrder(ctx: Context, order: List<String>) =
        BikeScope.putString(prefs(ctx), ctx, "card_order", DashLayout.encodeOrder(order))
    /** Moves one card a place up (-1) or down (+1). */
    fun moveCard(ctx: Context, id: String, delta: Int) = setCardOrder(ctx, DashLayout.move(cardOrder(ctx), id, delta))

    /** Accent colour (opaque ARGB), or 0 for the style's own. */
    fun accent(ctx: Context): Int = DashLayout.parseColor(BikeScope.getString(prefs(ctx), ctx, "accent", null))
    fun setAccent(ctx: Context, color: Int) = BikeScope.putString(prefs(ctx), ctx, "accent", DashLayout.encodeColor(color))

    /** A3 · battery saver: Android Auto stops sending video while the dashboard is shown. Off by default. */
    fun saver(ctx: Context) = BikeScope.getBoolean(prefs(ctx), ctx, "saver", false)
    fun setSaver(ctx: Context, on: Boolean) {
        BikeScope.putBoolean(prefs(ctx), ctx, "saver", on)
        if (wanted) applySaver(ctx.applicationContext, on && !aaWindow(ctx))   // now, if the dashboard is up (never with the AA window)
    }

    /** Open straight into the dashboard when the dash connects, instead of Android Auto. */
    fun startInDashboard(ctx: Context) = BikeScope.getBoolean(prefs(ctx), ctx, "start", false)
    fun setStartInDashboard(ctx: Context, on: Boolean) = BikeScope.putBoolean(prefs(ctx), ctx, "start", on)

    /** Light palette: forced, or (Auto) whenever the map would be in its day theme. */
    fun isLight(ctx: Context): Boolean = when (theme(ctx)) {
        Theme.LIGHT -> true
        Theme.DARK -> false
        Theme.AUTO -> !NightPrefs.isNightNow(ctx)
    }

    /** Adds the "Dashboard" shortcut to the app icon's long-press menu (dynamic: it must use this build's package id). */
    fun publishShortcut(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val intent = android.content.Intent(app, DashboardToggleActivity::class.java)
                .setAction(android.content.Intent.ACTION_VIEW)
            val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(app, "dashboard")
                .setShortLabel(app.getString(R.string.dash_shortcut))
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(app, R.drawable.ic_line_dash))
                .setIntent(intent)
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.pushDynamicShortcut(app, info)
        } catch (e: Exception) {
            LogBus.log("[DASHBOARD] shortcut not added: $e")
        }
    }

    // ── switching

    /** Flip between Android Auto and the dashboard. Returns the new state, or null if it can't be done now. */
    fun toggle(ctx: Context): Boolean? = if (set(ctx, !wanted)) wanted else null

    /** True if the request was honoured (turning it off always is). */
    fun set(ctx: Context, on: Boolean): Boolean {
        val vp = AaVideoBridge.pipeline
        if (on && (vp == null || ConnectionState.phase != Phase.STREAMING)) {
            LogBus.log("[DASHBOARD] not shown — Android Auto isn't on the dash yet")
            return false
        }
        engage(ctx, vp, on)
        return true
    }

    /** Called by [VideoPipeline] each time the bike canvas is configured (first connect and reconnects). */
    fun onDashReady(ctx: Context, vp: VideoPipeline) {
        if (wanted || startInDashboard(ctx)) engage(ctx, vp, true)
    }

    /** The projection was stopped by the rider: the next connection starts in Android Auto again. */
    fun reset() {
        wanted = false
        lossPending = false
        AaVideoBridge.videoFocusHeld = false
    }

    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    @Volatile private var saverApp: Context? = null
    /** F1 · the dashboard was engaged with the saver before this AA session was steady: LOSS is owed. */
    @Volatile private var lossPending = false

    /** F1 · the AA session reached steady video: apply a LOSS the dashboard already asked for. */
    fun onAaSteadyVideo() {
        val app = saverApp ?: return
        if (lossPending && SaverFocus.shouldApplyLoss(wanted, saver(app) && !aaWindow(app), AaVideoBridge.aaSteady)) {
            LogBus.log("[DASHBOARD] saver: Android Auto video is steady and the dashboard is up — applying LOSS")
            applySaver(app, true)
        }
    }

    /** F1 · a new (or ended) AA session holds GAIN, whatever the previous one had; the dashboard re-applies. */
    fun onAaSessionReset(why: String) {
        // The dashboard may still be on the dash (same pipeline, Android Auto restarted): owe a LOSS for
        // when the new session is steady. If the pipeline is rebuilt, onDashReady re-engages anyway.
        lossPending = wanted && saverApp?.let { saver(it) && !aaWindow(it) } == true
        if (!AaVideoBridge.videoFocusHeld) return
        AaVideoBridge.videoFocusHeld = false
        LogBus.log("[DASHBOARD] saver: $why — focus back to GAIN; LOSS again once the dashboard is up")
    }

    /** A3 · hold (true) or give back (false) the screen to Android Auto. */
    private fun applySaver(app: Context, hold: Boolean) {
        saverApp = app
        if (hold && !SaverFocus.shouldApplyLoss(wanted, true, AaVideoBridge.aaSteady)) {
            lossPending = true
            LogBus.log("[DASHBOARD] saver: waiting for steady Android Auto video before applying LOSS")
            return
        }
        lossPending = false
        if (AaVideoBridge.videoFocusHeld == hold) return
        AaVideoBridge.videoFocusHeld = hold
        val send = AaVideoBridge.sendVideoFocus
        LogBus.log("[DASHBOARD] saver: ${if (hold) "Android Auto video focus LOSS (audio and guidance keep going)" else "video focus GAIN back to Android Auto"}${if (send == null) " (no AA session yet)" else ""}")
        try { send?.invoke(!hold) } catch (e: Exception) { LogBus.log("[DASHBOARD] saver: focus change failed: $e") }
        if (!hold) {
            val askedAt = SystemClock.elapsedRealtime()
            main.postDelayed({
                if (!AaVideoBridge.videoFocusHeld && AaVideoBridge.lastAaFrameAtMs < askedAt) {
                    LogBus.log("[DASHBOARD] saver: no Android Auto picture 2 s after giving the screen back — asking for a keyframe")
                    try { AaVideoBridge.requestAaKeyframe?.invoke() } catch (_: Exception) {}
                }
            }, 2_000L)
        }
    }

    private fun engage(ctx: Context, vp: VideoPipeline?, on: Boolean) {
        wanted = on
        val app = ctx.applicationContext
        if (on && saver(app) && !aaWindow(app)) applySaver(app, true) else if (!on || aaWindow(app)) applySaver(app, false)
        vp?.setLiveLayer(
            if (on) liveBuilder(app) else null,
            if (on) { { signature(app) } } else null,
            if (on) { { aaWindow(app) } } else null,
        )
        LogBus.log("[DASHBOARD] ${if (on) "on" else "off"} (theme ${theme(app).id})")
    }

    private fun liveBuilder(app: Context): (Int, Int) -> android.graphics.Bitmap = { w, h -> render(app, w, h) }

    private fun render(ctx: Context, w: Int, h: Int): android.graphics.Bitmap =
        ChronoController.overlay(  // 0-100 timer countdown/result on top, only while it is active
            ctx,
            DashboardRenderer.render(
                ctx, w, h, collect(ctx), isLight(ctx), design(ctx), visibleFields(ctx),
                cardOrder = cardOrder(ctx), accent = accent(ctx),
            ),
        )

    // ── data

    internal fun collect(ctx: Context): Data {
        val snap = ObdLive.latest?.takeIf { SystemClock.elapsedRealtime() - it.atMs <= OBD_FRESH_MS }
        val obdSpeed = snap?.speed
        val gps = if (obdSpeed == null) gpsSpeed(ctx) else null
        val speed = pickSpeed(obdSpeed, gps)
        return Data(
            rpm = snap?.rpm, speedKmh = speed?.first, speedFromGps = speed?.second == true,
            coolant = snap?.coolant, throttle = snap?.throttle, volt = snap?.volt,
            load = snap?.load, intake = snap?.intake, baro = snap?.baro, absoluteLoad = snap?.absoluteLoad,
            relativeThrottle = snap?.relativeThrottle, obdLive = snap != null,
        )
    }

    /** GPS speed in km/h from a fix under 5 s old, asked at most once a second (the dashboard redraws 8×/s). */
    private fun gpsSpeed(ctx: Context): Double? {
        val now = SystemClock.elapsedRealtime()
        if (now - gpsAt >= GPS_POLL_MS) {
            gpsAt = now
            gpsKmh = try {
                RideExtras.lastLocation(ctx, GPS_MAX_AGE_MS)?.takeIf { it.hasSpeed() }?.let { it.speed * 3.6 }
            } catch (_: Exception) { null }
        }
        return gpsKmh
    }

    /** A4 · what the dashboard would paint now, rounded like the renderer (text) and fine enough for needles. */
    private fun signature(ctx: Context): String = signatureOf(
        collect(ctx), isLight(ctx), design(ctx).name,
        // Fields, card order and accent: changing any of them repaints at once.
        visibleFields(ctx).sorted().joinToString(",") + "|" + cardOrder(ctx).joinToString(",") + "|" + accent(ctx),
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date()) + "|" + ChronoController.signature(),
    )

    // ── pure helpers (unit tested)

    /** Speed, temperatures and percentages to the unit, RPM to 50 (0.5 % of the dial), volts to 0.1. */
    internal fun signatureOf(d: Data, light: Boolean, design: String, fields: String, clock: String): String {
        fun r(v: Double?, step: Double) = v?.let { Math.round(it / step) }?.toString() ?: "-"
        return listOf(
            r(d.rpm, 50.0), r(d.speedKmh, 1.0), d.speedFromGps, r(d.coolant, 1.0), r(d.throttle, 1.0), r(d.volt, 0.1),
            r(d.load, 1.0), r(d.intake, 1.0), r(d.baro, 1.0), r(d.absoluteLoad, 1.0), r(d.relativeThrottle, 1.0),
            d.obdLive, light, design, fields, clock,
        ).joinToString("|")
    }

    /** OBD speed wins; otherwise the GPS one (flagged), with the standstill jitter removed. Null: no speed at all. */
    internal fun pickSpeed(obdKmh: Double?, gpsKmh: Double?): Pair<Double, Boolean>? = when {
        obdKmh != null -> obdKmh to false
        gpsKmh != null -> (if (gpsKmh < GPS_STILL_KMH) 0.0 else gpsKmh) to true
        else -> null
    }

    enum class Level { NORMAL, WARN, ALERT }

    internal fun coolantLevel(c: Double): Level = when {
        c >= COOLANT_RED -> Level.ALERT
        c >= COOLANT_AMBER -> Level.WARN
        else -> Level.NORMAL
    }

    /** How many of [segments] bar segments are lit for [rpm]. */
    internal fun litSegments(rpm: Double?, segments: Int): Int =
        if (rpm == null) 0 else Math.round(rpm.coerceIn(0.0, RPM_MAX) / RPM_MAX * segments).toInt()
}

/** F1 · pure video-focus decisions for the dashboard saver (unit tested). */
internal object SaverFocus {
    /** Opening the video channel always gives the screen to Android Auto. */
    const val CHANNEL_OPEN_GAIN = true
    /** LOSS only with the dashboard wanted, the saver on, and this AA session already at steady video. */
    fun shouldApplyLoss(dashboardWanted: Boolean, saverOn: Boolean, sessionSteady: Boolean): Boolean =
        dashboardWanted && saverOn && sessionSteady
    /** Reply to an AA request for the screen: refuse it only while the saver holds a steady session. */
    fun projectedReplyGain(saverHeld: Boolean, sessionSteady: Boolean): Boolean = !(saverHeld && sessionSteady)
}
