// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock

/**
 * What the phone knows, for the dashboard without OBD ([DashFallback]). The dashboard redraws 8×/s, so
 * everything slow is cached: the GPS fix is read once a second (the same fix that gives the GPS speed, no
 * extra location request), the battery every 30 s, the cached weather every minute. Trip and navigation are
 * plain field reads.
 */
internal object DashPhone {
    private const val GPS_MAX_AGE_MS = 5_000L
    private const val GPS_POLL_MS = 1_000L
    private const val BATT_POLL_MS = 30_000L
    private const val WX_POLL_MS = 60_000L
    private const val ADAPTER_POLL_MS = 1_000L
    /** Below this speed (m/s) a GPS bearing is noise. */
    private const val MIN_BEARING_SPEED = 1.5f

    /** One GPS fix: speed in km/h as received, altitude in metres, heading in degrees (null when unknown). */
    class Fix(val speedKmh: Double?, val altitudeM: Double?, val headingDeg: Double?)

    @Volatile private var fixAt = 0L
    @Volatile private var fix: Fix? = null
    private var lastHeading: Double? = null
    private var lastHeadingAt = 0L

    @Volatile private var battAt = 0L
    @Volatile private var battPct: Int? = null
    @Volatile private var wxAt = 0L
    @Volatile private var wxTemp: Int? = null
    @Volatile private var adapterAt = 0L
    @Volatile private var adapterOn = false

    /** The latest GPS fix (under 5 s old), asked at most once a second. */
    fun gps(ctx: Context): Fix? {
        val now = SystemClock.elapsedRealtime()
        if (now - fixAt >= GPS_POLL_MS) {
            fixAt = now
            fix = try {
                RideExtras.lastLocation(ctx, GPS_MAX_AGE_MS)?.let { l ->
                    val fresh = if (l.hasBearing() && l.hasSpeed() && l.speed >= MIN_BEARING_SPEED) l.bearing.toDouble() else null
                    val heading = DashPhoneMath.keepHeading(fresh, lastHeading, now - lastHeadingAt)
                    if (fresh != null) { lastHeading = fresh; lastHeadingAt = now }
                    Fix(
                        speedKmh = if (l.hasSpeed()) l.speed * 3.6 else null,
                        altitudeM = if (l.hasAltitude()) l.altitude else null,
                        headingDeg = heading,
                    )
                }
            } catch (_: Exception) { null }
        }
        return fix
    }

    @Volatile private var wantAt = 0L
    @Volatile private var wantFix = false

    /** The rider picked altitude or heading as a field: the GPS fix is needed even with OBD speed. Cached 1 s. */
    fun wantsFix(ctx: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (wantAt == 0L || now - wantAt >= ADAPTER_POLL_MS) {
            wantAt = now
            wantFix = try { DashboardMode.visibleFields(ctx).let { "alt" in it || "heading" in it } } catch (_: Exception) { false }
        }
        return wantFix
    }

    /** A live OBD adapter is set up ([AppSettings.obdLiveAdapter]). */
    fun adapterConfigured(ctx: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (adapterAt == 0L || now - adapterAt >= ADAPTER_POLL_MS) {
            adapterAt = now
            adapterOn = try { AppSettings.obdLiveAdapter(ctx.applicationContext) != null } catch (_: Exception) { false }
        }
        return adapterOn
    }

    /** Phone battery percent, or null. */
    fun battery(ctx: Context): Int? {
        val now = SystemClock.elapsedRealtime()
        if (battAt == 0L || now - battAt >= BATT_POLL_MS) {
            battAt = now
            battPct = try {
                val i = ctx.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                BatteryLog.percent(i?.getIntExtra("level", -1) ?: -1, i?.getIntExtra("scale", -1) ?: -1).takeIf { it >= 0 }
            } catch (_: Exception) { null }
        }
        return battPct
    }

    /** Outside temperature from the weather the Home cards already cached, or null. */
    fun outsideC(ctx: Context): Double? {
        val now = SystemClock.elapsedRealtime()
        if (wxAt == 0L || now - wxAt >= WX_POLL_MS) {
            wxAt = now
            wxTemp = try { DashCards.cachedTempC(ctx.applicationContext) } catch (_: Exception) { null }
        }
        return wxTemp?.toDouble()
    }

    /** Distance to the next turn in metres from Android Auto's navigation status, or null when not navigating. */
    fun nextTurnM(): Int? = try { NavFeed.current()?.meters } catch (_: Exception) { null }
}
