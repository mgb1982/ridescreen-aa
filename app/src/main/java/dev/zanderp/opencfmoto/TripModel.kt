package dev.zanderp.opencfmoto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
            avgThrottlePct = avg(obdSamples.map { it.throttlePct }),
            avgVoltage = avg(obdSamples.map { it.voltage }),
            sampleCount = obdSamples.size,
        )
    }

    fun durationText(): String {
        val sec = (movingTimeMs / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    }

    fun distanceText(): String = String.format(Locale.US, "%.2f km", distanceKm)

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
