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
        /** A fresh OBD reading exists (false: contact off, adapter away, or the radio lent to the keyless box). */
        val obdLive: Boolean,
    )

    // Provisional until checked on the bike (the 125X rev limit and the usual coolant range).
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

    fun theme(ctx: Context): Theme = Theme.byId(prefs(ctx).getString("theme", null))
    fun setTheme(ctx: Context, t: Theme) = prefs(ctx).edit().putString("theme", t.id).apply()

    /** Open straight into the dashboard when the dash connects, instead of Android Auto. */
    fun startInDashboard(ctx: Context) = prefs(ctx).getBoolean("start", false)
    fun setStartInDashboard(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("start", on).apply()

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
    }

    private fun engage(ctx: Context, vp: VideoPipeline?, on: Boolean) {
        wanted = on
        val app = ctx.applicationContext
        vp?.setLiveLayer(if (on) liveBuilder(app) else null)
        LogBus.log("[DASHBOARD] ${if (on) "on" else "off"} (theme ${theme(app).id})")
    }

    private fun liveBuilder(app: Context): (Int, Int) -> android.graphics.Bitmap = { w, h -> render(app, w, h) }

    private fun render(ctx: Context, w: Int, h: Int): android.graphics.Bitmap =
        DashboardRenderer.render(ctx, w, h, collect(ctx), isLight(ctx))

    // ── data

    internal fun collect(ctx: Context): Data {
        val snap = ObdLive.latest?.takeIf { SystemClock.elapsedRealtime() - it.atMs <= OBD_FRESH_MS }
        val obdSpeed = snap?.speed
        val gps = if (obdSpeed == null) gpsSpeed(ctx) else null
        val speed = pickSpeed(obdSpeed, gps)
        return Data(
            rpm = snap?.rpm, speedKmh = speed?.first, speedFromGps = speed?.second == true,
            coolant = snap?.coolant, throttle = snap?.throttle, volt = snap?.volt, obdLive = snap != null,
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

    // ── pure helpers (unit tested)

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
