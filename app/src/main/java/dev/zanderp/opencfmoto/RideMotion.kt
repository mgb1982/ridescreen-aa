// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.sqrt

/** Result of [RideMotion.calibrate]. */
enum class CalibResult { OK, MOVING, NOT_STILL, NO_SENSOR }

/**
 * One reading of the bike's motion. Every field is null when it is not known right now.
 * [leanDeg]: positive = leaning right, negative = left. [gLat]: positive = pushed to the right (same
 * side as the lean). [gLon]: positive = accelerating, negative = braking.
 */
data class Motion(val atMs: Long, val leanDeg: Double?, val gLat: Double?, val gLon: Double?, val bpm: Int?)

/**
 * Lean angle, G-forces and heart rate while riding, from the phone's gyroscope and accelerometer
 * (phone fixed in a mount) plus the speed from GPS or OBD.
 *
 * ESTIMATE, not a measurement of the bike's frame. The calibration stores the "up" direction of the
 * bike in the phone's axes (mean gravity while the bike stands upright and still). The forward axis is
 * NOT known, so everything uses only the yaw rate around that up axis (gyro . up) and the speed:
 * lateral acceleration = v * yawRate, lean = atan(lateral acceleration / g). The yaw rate is taken
 * around the bike's own up axis, which tilts with the bike, so at big angles it reads a little low;
 * wheelies, stoppies and a phone that moves in the mount add error. Not validated on a bike.
 *
 * Lifecycle: [calibrate] once with the phone in the mount (off the main thread, takes about 2 s);
 * owners call [acquire] / [release]; sensors only run when calibrated and at least one owner holds it.
 */
object RideMotion {
    private const val PREF = "ride_motion"
    private const val FRESH_MS = 2_000L
    private const val SPEED_FRESH_MS = 3_000L
    private const val CALIB_MS = 2_000L
    private const val TAG = "[MOTION]"

    private val lock = Any()
    private var appCtx: Context? = null
    private val owners = HashMap<String, Int>()
    private var sm: SensorManager? = null
    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    // Calibration (null = not calibrated); published for the sensor thread.
    @Volatile private var up: DoubleArray? = null
    @Volatile private var calAt = 0L

    // Speed: GPS and OBD, the OBD one wins while it is recent.
    @Volatile private var gpsKmh = 0.0
    @Volatile private var gpsAt = 0L
    @Volatile private var obdKmh = 0.0
    @Volatile private var obdAt = 0L
    private val speedWin = RideMotionMath.SpeedWindow()

    // Published results.
    @Volatile private var lean: Double? = null
    @Volatile private var gLat: Double? = null
    @Volatile private var leanAt = 0L
    @Volatile private var gLon: Double? = null
    @Volatile private var gLonAt = 0L

    // Sensor-thread state.
    private var lastGyroAt = 0L
    private val grav = DoubleArray(3)
    private var gravAt = 0L
    private val mount = RideMotionMath.MountWatch()

    private fun now() = System.currentTimeMillis()

    // ── calibration

    fun isCalibrated(ctx: Context): Boolean = loadCalibration(ctx) != null

    /** Epoch millis of the active calibration, or null. */
    fun calibratedAt(ctx: Context): Long? = if (loadCalibration(ctx) != null) calAt else null

    private fun loadCalibration(ctx: Context): DoubleArray? {
        val cached = up
        if (cached != null) {
            if (RideMotionMath.calibrationValid(calAt, now())) return cached
            clearCalibration(ctx, "expired")
            return null
        }
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.contains("at")) return null
        val at = p.getLong("at", 0L)
        if (!RideMotionMath.calibrationValid(at, now())) { clearCalibration(ctx, "expired"); return null }
        val v = doubleArrayOf(p.getFloat("ux", 0f).toDouble(), p.getFloat("uy", 0f).toDouble(), p.getFloat("uz", 0f).toDouble())
        if (RideMotionMath.norm(v) < 0.5) return null
        calAt = at; up = v
        return v
    }

    /** Forget the calibration (trip over, or the phone left the mount). */
    fun clearCalibration(ctx: Context, why: String = "trip ended") {
        val app = ctx.applicationContext
        val had = up != null || app.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains("at")
        up = null; calAt = 0L
        app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
        lean = null; gLat = null; gLon = null
        if (had) LogBus.log("$TAG calibration cleared ($why)")
        refresh()
    }

    /**
     * Measures about 2 s with the phone still in its mount and stores the bike's "up" vector.
     * Blocks: call from a background thread. Speed below 3 km/h is required when it is known.
     */
    fun calibrate(ctx: Context): CalibResult {
        val app = ctx.applicationContext
        val mgr = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return CalibResult.NO_SENSOR
        val gyro = mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val acc = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyro == null || acc == null) return CalibResult.NO_SENSOR
        speedKmh(now())?.let { if (it >= RideMotionMath.CALIB_MAX_KMH) return CalibResult.MOVING }
        val sample = RideMotionMath.CalibAccumulator()
        val ht = HandlerThread("ride-calib").apply { start() }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                synchronized(sample) {
                    if (e.sensor.type == Sensor.TYPE_GYROSCOPE) sample.onGyro(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                    else sample.onAccel(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        try {
            val h = Handler(ht.looper)
            mgr.registerListener(l, gyro, SensorManager.SENSOR_DELAY_GAME, h)
            mgr.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME, h)
            try { Thread.sleep(CALIB_MS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        } finally {
            mgr.unregisterListener(l)
            ht.quitSafely()
        }
        val speed = speedKmh(now())
        val result = synchronized(sample) { sample.verdict(speed) }
        if (result == CalibResult.OK) {
            val v = synchronized(sample) { sample.up() } ?: return CalibResult.NOT_STILL
            app.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putFloat("ux", v[0].toFloat()).putFloat("uy", v[1].toFloat()).putFloat("uz", v[2].toFloat())
                .putLong("at", now()).apply()
            up = v; calAt = now()
            mount.reset()
            LogBus.log("$TAG calibrated")
            synchronized(lock) { if (appCtx == null) appCtx = app }
            refresh()
        } else LogBus.log("$TAG calibration refused: $result")
        return result
    }

    // ── owners

    /** Take part in keeping the sensors on. Each [acquire] needs its own [release] with the same owner. */
    fun acquire(ctx: Context, owner: String) {
        synchronized(lock) {
            appCtx = ctx.applicationContext
            owners[owner] = (owners[owner] ?: 0) + 1
        }
        refresh()
    }

    fun release(owner: String) {
        synchronized(lock) {
            val n = (owners[owner] ?: 0) - 1
            if (n <= 0) owners.remove(owner) else owners[owner] = n
        }
        refresh()
    }

    private fun refresh() {
        synchronized(lock) {
            val ctx = appCtx ?: return
            val want = owners.isNotEmpty() && loadCalibration(ctx) != null
            if (want && listener == null) startSensors(ctx)
            else if (!want && listener != null) stopSensors()
        }
    }

    private fun startSensors(ctx: Context) {
        val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val gyro = mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val acc = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (gyro == null || acc == null) return
        val ht = HandlerThread("ride-motion").apply { start() }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_GYROSCOPE) onGyro(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                else onAccel(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble(), ctx)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        val h = Handler(ht.looper)
        mgr.registerListener(l, gyro, SensorManager.SENSOR_DELAY_GAME, h)
        mgr.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME, h)
        sm = mgr; thread = ht; listener = l
        lastGyroAt = 0L; gravAt = 0L; mount.reset()
        LogBus.log("$TAG sensors on")
    }

    private fun stopSensors() {
        listener?.let { sm?.unregisterListener(it) }
        thread?.quitSafely()
        listener = null; thread = null; sm = null
        lean = null; gLat = null
        LogBus.log("$TAG sensors off")
    }

    // ── speed

    /**
     * Current speed. GPS callers use the 2-argument form; OBD callers pass [fromObd] = true, and the
     * OBD value wins while it is under 3 s old. [atMs] is epoch millis.
     */
    fun onSpeed(kmh: Double, atMs: Long, fromObd: Boolean = false) {
        if (!kmh.isFinite() || kmh < 0.0) return
        if (fromObd) { obdKmh = kmh; obdAt = atMs } else { gpsKmh = kmh; gpsAt = atMs }
        if (!fromObd && now() - obdAt <= SPEED_FRESH_MS) return   // OBD is feeding the window already
        val a = synchronized(speedWin) { speedWin.add(atMs, kmh / 3.6); speedWin.accel() } ?: return
        val g = a / RideMotionMath.G
        val prev = gLon
        val dt = if (gLonAt > 0) (atMs - gLonAt) else 0L
        gLon = RideMotionMath.ema(if (now() - gLonAt <= FRESH_MS) prev else null, g, dt, 400.0).coerceIn(-1.5, 1.5)
        gLonAt = atMs
    }

    /** Speed in km/h if known and fresh, OBD first. */
    private fun speedKmh(nowMs: Long): Double? = when {
        nowMs - obdAt in 0..SPEED_FRESH_MS && obdAt > 0 -> obdKmh
        nowMs - gpsAt in 0..SPEED_FRESH_MS && gpsAt > 0 -> gpsKmh
        else -> null
    }

    // ── sensor callbacks (sensor thread)

    private fun onGyro(x: Double, y: Double, z: Double) {
        val u = up ?: return
        val t = now()
        val v = speedKmh(t) ?: return
        val w = x * u[0] + y * u[1] + z * u[2]
        val vMs = v / 3.6
        val dt = if (lastGyroAt > 0) t - lastGyroAt else 0L
        lastGyroAt = t
        val fresh = t - leanAt <= FRESH_MS
        lean = RideMotionMath.ema(if (fresh) lean else null, RideMotionMath.leanDeg(vMs, w), dt, 300.0)
            .coerceIn(-RideMotionMath.MAX_LEAN_DEG, RideMotionMath.MAX_LEAN_DEG)
        gLat = RideMotionMath.ema(if (fresh) gLat else null, RideMotionMath.gLat(vMs, w), dt, 300.0)
        leanAt = t
    }

    private fun onAccel(x: Double, y: Double, z: Double, ctx: Context) {
        val t = now()
        val dt = if (gravAt > 0) t - gravAt else 0L
        gravAt = t
        if (dt == 0L || dt > 2_000L) { grav[0] = x; grav[1] = y; grav[2] = z } else {
            val a = dt / (500.0 + dt)
            grav[0] += (x - grav[0]) * a; grav[1] += (y - grav[1]) * a; grav[2] += (z - grav[2]) * a
        }
        val u = up ?: return
        if (!RideMotionMath.calibrationValid(calAt, t)) { clearCalibration(ctx, "expired"); return }
        if (mount.update(t, speedKmh(t), RideMotionMath.angleDeg(grav, u))) {
            LogBus.log("$TAG calibration dropped: phone moved off the mount")
            clearCalibration(ctx, "phone moved off the mount")
        }
    }

    // ── reading

    /** The freshest reading (under 2 s old per field), or null when nothing is known. */
    fun latest(): Motion? {
        val t = now()
        val sensors = listener != null
        val l = if (sensors && t - leanAt <= FRESH_MS) lean else null
        val gl = if (sensors && t - leanAt <= FRESH_MS) gLat else null
        val gn = if (sensors && t - gLonAt <= FRESH_MS) gLon else null
        val bpm = appCtx?.let { HeartRateStore.latest(it) }
        if (l == null && gl == null && gn == null && bpm == null) return null
        return Motion(t, l, gl, gn, bpm)
    }
}

/** The maths behind [RideMotion], with no Android types so it runs in JVM tests. */
object RideMotionMath {
    const val G = 9.81
    const val MIN_LEAN_KMH = 10.0
    const val MAX_LEAN_DEG = 65.0
    const val CALIB_MAX_KMH = 3.0
    const val CALIB_MAX_GYRO = 0.05
    const val CALIB_ACCEL_MIN = 9.3
    const val CALIB_ACCEL_MAX = 10.3
    const val CALIB_MAX_AGE_MS = 12L * 3_600_000L
    const val MOUNT_MAX_KMH = 5.0
    const val MOUNT_MAX_DEG = 25.0
    const val MOUNT_MS = 5_000L

    fun norm(v: DoubleArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    fun normalize(v: DoubleArray): DoubleArray? {
        val n = norm(v)
        return if (n < 1e-6) null else doubleArrayOf(v[0] / n, v[1] / n, v[2] / n)
    }

    fun angleDeg(a: DoubleArray, b: DoubleArray): Double {
        val na = norm(a); val nb = norm(b)
        if (na < 1e-6 || nb < 1e-6) return 0.0
        return Math.toDegrees(acos((dot(a, b) / (na * nb)).coerceIn(-1.0, 1.0)))
    }

    fun calibrationValid(atMs: Long, nowMs: Long) = atMs > 0 && nowMs - atMs in 0..CALIB_MAX_AGE_MS

    /**
     * Lateral acceleration in g from speed [vMs] and yaw rate [omegaUp] (rad/s, gyro . up). Positive =
     * towards the right: a left turn has omegaUp > 0 with Android axes (counter-clockwise seen from
     * above) and pushes the rider to the left, hence the minus sign.
     */
    fun gLat(vMs: Double, omegaUp: Double): Double = -vMs * omegaUp / G

    /**
     * Lean in degrees, positive = right, 0.0 below 10 km/h, limited to +-65. Uses tan(lean) = a/g, the
     * balance of a bike in a steady turn (the brief said asin; that over-reads at big angles).
     */
    fun leanDeg(vMs: Double, omegaUp: Double): Double {
        if (vMs * 3.6 < MIN_LEAN_KMH) return 0.0
        return Math.toDegrees(atan(gLat(vMs, omegaUp))).coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    }

    /** One step of an exponential moving average; [prev] null starts from [x]. */
    fun ema(prev: Double?, x: Double, dtMs: Long, tauMs: Double): Double {
        if (prev == null || dtMs <= 0) return prev?.let { it + (x - it) * 0.2 } ?: x
        val a = 1.0 - exp(-dtMs / tauMs)
        return prev + (x - prev) * a
    }

    /** Speed samples over about the last 1.5 s, to get the longitudinal acceleration. */
    class SpeedWindow(private val windowMs: Long = 1_500L) {
        private val t = ArrayList<Long>()
        private val v = ArrayList<Double>()
        fun add(atMs: Long, vMs: Double) {
            if (t.isNotEmpty() && atMs <= t.last()) return
            t.add(atMs); v.add(vMs)
            while (t.size > 2 && atMs - t.first() > windowMs) { t.removeAt(0); v.removeAt(0) }
        }
        /** m/s2 between oldest and newest sample, null with under 0.4 s between them. */
        fun accel(): Double? {
            if (t.size < 2) return null
            val dt = t.last() - t.first()
            if (dt < 400L) return null
            return (v.last() - v.first()) / (dt / 1000.0)
        }
    }

    /** Collects the still-phone samples of a calibration. */
    class CalibAccumulator {
        private var gyroN = 0; private var gyroBad = 0
        private var accN = 0; private var accBad = 0
        private val sum = DoubleArray(3)
        fun onGyro(x: Double, y: Double, z: Double) {
            gyroN++
            if (sqrt(x * x + y * y + z * z) >= CALIB_MAX_GYRO) gyroBad++
        }
        fun onAccel(x: Double, y: Double, z: Double) {
            accN++
            val m = sqrt(x * x + y * y + z * z)
            if (m < CALIB_ACCEL_MIN || m > CALIB_ACCEL_MAX) accBad++
            sum[0] += x; sum[1] += y; sum[2] += z
        }
        /** Mean gravity as a unit vector in phone axes. */
        fun up(): DoubleArray? = if (accN == 0) null else normalize(sum)
        fun verdict(speedKmh: Double?): CalibResult {
            if (gyroN < 10 || accN < 10) return CalibResult.NO_SENSOR
            if (speedKmh != null && speedKmh >= CALIB_MAX_KMH) return CalibResult.MOVING
            // A few outliers are noise; more than 3 % means the phone or the bike was moving.
            if (gyroBad > gyroN * 0.03 || accBad > accN * 0.03) return CalibResult.NOT_STILL
            return CalibResult.OK
        }
    }

    /** Detects the phone leaving the mount: gravity > 25 deg off the calibration for > 5 s at under 5 km/h. */
    class MountWatch {
        private var since = 0L
        fun reset() { since = 0L }
        /** True once the calibration should be dropped. Unknown speed counts as "not parked". */
        fun update(nowMs: Long, speedKmh: Double?, angleDeg: Double): Boolean {
            if (speedKmh == null || speedKmh >= MOUNT_MAX_KMH || angleDeg <= MOUNT_MAX_DEG) { since = 0L; return false }
            if (since == 0L) since = nowMs
            return nowMs - since > MOUNT_MS
        }
    }

}
