package dev.zanderp.opencfmoto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/** One GPS sample on a ride. */
data class TrackPoint(val lat: Double, val lon: Double, val time: Long, val speedMs: Float)

/** A read-only ECU snapshot aligned to the trip and video wall clock. */
data class ObdTripSample(
    val timeMs: Long,
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val coolantC: Double? = null,
    val throttlePct: Double? = null,
    val voltage: Double? = null,
    val engineLoadPct: Double? = null,
    val intakeAirC: Double? = null,
    val baroKpa: Double? = null,
    val absoluteLoadPct: Double? = null,
    val relativeThrottlePct: Double? = null,
)

data class TripObdSummary(
    val maxRpm: Double?,
    val maxCoolantC: Double?,
    val avgThrottlePct: Double?,
    val avgVoltage: Double?,
    val sampleCount: Int,
)

/**
 * Lean, G-forces and heart rate at one instant of the ride (see [RideMotion]). Lean: positive = right.
 * gLat: positive = right. gLon: positive = accelerating. Any field may be null.
 */
data class MotionTripSample(
    val timeMs: Long,
    val leanDeg: Double? = null,
    val gLat: Double? = null,
    val gLon: Double? = null,
    val bpm: Int? = null,
)

data class TripMotionSummary(
    val maxLeanLeftDeg: Double?,
    val maxLeanRightDeg: Double?,
    val maxG: Double?,
    val avgBpm: Int?,
    val maxBpm: Int?,
)

/** JSON form of the motion samples: one array per sample, [timeMs, lean, gLat, gLon, bpm], null for unknown. */
object MotionCodec {
    fun encode(samples: List<MotionTripSample>): JSONArray = JSONArray().also { out ->
        for (s in samples) {
            fun d(v: Double?): Any = v ?: JSONObject.NULL
            out.put(JSONArray().put(s.timeMs).put(d(s.leanDeg)).put(d(s.gLat)).put(d(s.gLon)).put(s.bpm ?: JSONObject.NULL))
        }
    }

    fun decode(array: JSONArray?): List<MotionTripSample> {
        if (array == null) return emptyList()
        fun JSONArray.number(i: Int): Double? = if (isNull(i)) null else optDouble(i).takeIf { it.isFinite() }
        return (0 until array.length()).mapNotNull { i ->
            val a = array.optJSONArray(i) ?: return@mapNotNull null
            MotionTripSample(a.optLong(0), a.number(1), a.number(2), a.number(3), a.number(4)?.toInt())
        }
    }
}

fun Trip.motionAt(timeMs: Long, maxAgeMs: Long = 2_000L): MotionTripSample? {
    if (motionSamples.isEmpty()) return null
    val i = motionSamples.binarySearchBy(timeMs) { it.timeMs }
    val nearest = if (i >= 0) i else {
        val after = (-i - 1).coerceIn(0, motionSamples.lastIndex)
        val before = (after - 1).coerceAtLeast(0)
        if (abs(motionSamples[before].timeMs - timeMs) <= abs(motionSamples[after].timeMs - timeMs)) before else after
    }
    return motionSamples[nearest].takeIf { abs(it.timeMs - timeMs) <= maxAgeMs }
}

fun Trip.obdAt(timeMs: Long, maxAgeMs: Long = 5_000L): ObdTripSample? {
    if (obdSamples.isEmpty()) return null
    val i = obdSamples.binarySearchBy(timeMs) { it.timeMs }
    val nearest = if (i >= 0) i else {
        val after = (-i - 1).coerceIn(0, obdSamples.lastIndex)
        val before = (after - 1).coerceAtLeast(0)
        if (abs(obdSamples[before].timeMs - timeMs) <= abs(obdSamples[after].timeMs - timeMs)) before else after
    }
    return obdSamples[nearest].takeIf { abs(it.timeMs - timeMs) <= maxAgeMs }
}

/**
 * A recorded ride: summary stats plus the full GPS track (for the map).
 *
 * Speeds are stored in m/s (as the GPS reports them); distance in metres; times in epoch millis.
 * The [id] is the start-time millis, which keeps files sortable and unique enough for one device.
 */
data class Trip(
    val id: String,
    val start: Long,
    val end: Long,
    val distanceMeters: Double,
    val movingTimeMs: Long,
    val maxSpeedMs: Float,
    val points: List<TrackPoint>,
    val obdSamples: List<ObdTripSample> = emptyList(),
    val motionSamples: List<MotionTripSample> = emptyList(),
    val archivedByMerge: Boolean = false,
    val mergedFromIds: List<String> = emptyList(),
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
    val maxKmh: Int get() = (maxSpeedMs * 3.6f).toInt()
    val avgKmh: Int get() =
        if (movingTimeMs > 0) ((distanceMeters / (movingTimeMs / 1000.0)) * 3.6).toInt() else 0

    fun obdSummary(): TripObdSummary? {
        if (obdSamples.isEmpty()) return null
        fun avg(values: List<Double?>): Double? = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
        return TripObdSummary(
            maxRpm = obdSamples.mapNotNull { it.rpm }.maxOrNull(),
            maxCoolantC = obdSamples.mapNotNull { it.coolantC }.maxOrNull(),
            avgThrottlePct = avg(obdSamples.map { it.relativeThrottlePct }), // relative throttle (PID 0x45): more precise on the Zontes
            avgVoltage = avg(obdSamples.map { it.voltage }),
            sampleCount = obdSamples.size,
        )
    }

    /** Max lean each side (positive degrees), max combined G, average and max heart rate; null without motion data. */
    fun motionSummary(): TripMotionSummary? {
        if (motionSamples.isEmpty()) return null
        val leans = motionSamples.mapNotNull { it.leanDeg }
        val gs = motionSamples.mapNotNull { s ->
            if (s.gLat == null && s.gLon == null) null else sqrt((s.gLat ?: 0.0).let { it * it } + (s.gLon ?: 0.0).let { it * it })
        }
        val bpms = motionSamples.mapNotNull { it.bpm }
        return TripMotionSummary(
            maxLeanLeftDeg = leans.filter { it < 0 }.minOrNull()?.let { -it },
            maxLeanRightDeg = leans.filter { it > 0 }.maxOrNull(),
            maxG = gs.maxOrNull(),
            avgBpm = bpms.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
            maxBpm = bpms.maxOrNull(),
        )
    }

    fun durationText(): String {
        val sec = (movingTimeMs / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    }

    /** Log/share text: fixed English format. Screens use [distanceText] with units + locale. */
    fun distanceText(): String = String.format(Locale.US, "%.2f km", distanceKm)

    /** User-facing distance: locale decimal separator and km or mi. */
    fun distanceText(imperial: Boolean, locale: Locale = Locale.getDefault()): String =
        FmtCore.distance(distanceMeters, imperial, locale)

    fun dateText(): String = DATE_FMT.format(Date(start))

    fun timeRangeText(): String {
        val s = TIME_FMT.format(Date(start))
        val e = TIME_FMT.format(Date(end))
        return "$s – $e"
    }

    companion object {
        private val DATE_FMT = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
        private val TIME_FMT = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}
