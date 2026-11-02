// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists recorded rides as one JSON file per trip under `filesDir/trips/`.
 *
 * A file per trip keeps writes atomic and cheap (no rewriting a growing master list), and makes
 * delete a single unlink. Each file holds the summary plus the full GPS track so the map can redraw
 * the route offline. Points are stored as compact `[lat, lon, timeMs, speedMs]` arrays.
 */
object TripStore {
    private fun dir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "trips").apply { if (!exists()) mkdirs() }

    // RideScreen AA: a small index of trip summaries (no GPS points) so lists, records, the widget
    // and the watch don't parse every track. Lives outside trips/ so list() never sees it.
    private fun indexFile(ctx: Context) = File(ctx.applicationContext.filesDir, "trips-index.json")
    @Volatile private var summaryCache: List<Trip>? = null

    /** All trips without their points (cheap), most recent first. Use [get] for a full track. */
    @Synchronized
    fun summaries(ctx: Context): List<Trip> {
        summaryCache?.let { return it.filterNot { trip -> trip.archivedByMerge } }
        val files = dir(ctx).listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()
        val ids = files.map { it.name.removeSuffix(".json") }.toSet()
        val fromIndex = runCatching { readIndex(indexFile(ctx)) }.getOrNull()
        val result = if (fromIndex != null && fromIndex.map { it.id }.toSet() == ids) {
            fromIndex
        } else {
            // First run (or files changed behind our back): rebuild once from the full files.
            files.mapNotNull { f -> parse(f, includeObd = false)?.copy(points = emptyList(), obdSamples = emptyList()) }.also { writeIndex(ctx, it) }
        }.sortedByDescending { it.start }
        summaryCache = result
        return result.filterNot { it.archivedByMerge }
    }

    @Synchronized
    private fun updateIndex(ctx: Context, change: (MutableList<Trip>) -> Unit) {
        val current = summaryCache ?: runCatching { readIndex(indexFile(ctx)) }.getOrNull()
        if (current == null) {
            summaryCache = null // rebuilt lazily by summaries()
            return
        }
        val list = current.toMutableList()
        change(list)
        val sorted = list.sortedByDescending { it.start }
        writeIndex(ctx, sorted)
        summaryCache = sorted
    }

    private fun readIndex(f: File): List<Trip>? {
        if (!f.exists()) return null
        val a = JSONArray(f.readText())
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Trip(
                id = o.getString("id"), start = o.optLong("start"), end = o.optLong("end"),
                distanceMeters = o.optDouble("distanceMeters"), movingTimeMs = o.optLong("movingTimeMs"),
                maxSpeedMs = o.optDouble("maxSpeedMs").toFloat(), points = emptyList(),
                archivedByMerge = o.optBoolean("archivedByMerge"),
                mergedFromIds = o.optJSONArray("mergedFromIds")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            )
        }
    }

    private fun writeIndex(ctx: Context, trips: List<Trip>) {
        val a = JSONArray()
        for (t in trips) {
            a.put(
                JSONObject().put("id", t.id).put("start", t.start).put("end", t.end)
                    .put("distanceMeters", t.distanceMeters).put("movingTimeMs", t.movingTimeMs)
                    .put("maxSpeedMs", t.maxSpeedMs.toDouble())
                    .put("archivedByMerge", t.archivedByMerge)
                    .put("mergedFromIds", JSONArray(t.mergedFromIds))
            )
        }
        runCatching { indexFile(ctx).writeText(a.toString()) }
    }

    fun save(ctx: Context, trip: Trip) {
        val obj = JSONObject()
            .put("id", trip.id)
            .put("start", trip.start)
            .put("end", trip.end)
            .put("distanceMeters", trip.distanceMeters)
            .put("movingTimeMs", trip.movingTimeMs)
            .put("maxSpeedMs", trip.maxSpeedMs.toDouble())
            .put("archivedByMerge", trip.archivedByMerge)
            .put("mergedFromIds", JSONArray(trip.mergedFromIds))
        val pts = JSONArray()
        for (p in trip.points) {
            pts.put(JSONArray().put(p.lat).put(p.lon).put(p.time).put(p.speedMs.toDouble()))
        }
        obj.put("points", pts)
        obj.put("obdSamples", encodeObd(trip.obdSamples))
        runCatching { File(dir(ctx), "${trip.id}.json").writeText(obj.toString()) }
        updateIndex(ctx) { l -> l.removeAll { it.id == trip.id }; l.add(trip.copy(points = emptyList(), obdSamples = emptyList())) }
    }

    /** All saved trips, most recent first. */
    fun list(ctx: Context): List<Trip> {
        val files = dir(ctx).listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { parse(it, includeObd = false) }.filterNot { it.archivedByMerge }.sortedByDescending { it.start }
    }

    fun get(ctx: Context, id: String): Trip? {
        val f = File(dir(ctx), "$id.json")
        return if (f.exists()) parse(f) else null
    }

    fun delete(ctx: Context, id: String) {
        val merged = get(ctx, id)
        runCatching { File(dir(ctx), "$id.json").delete() }
        updateIndex(ctx) { l -> l.removeAll { it.id == id } }
        merged?.mergedFromIds?.forEach { originalId -> get(ctx, originalId)?.let { save(ctx, it.copy(archivedByMerge = false)) } }
    }

    @Synchronized
    fun merge(ctx: Context, ids: List<String>): Trip? {
        val originals = ids.distinct().mapNotNull { get(ctx, it) }.sortedBy { it.start }
        if (originals.size < 2 || originals.any { it.archivedByMerge || it.mergedFromIds.isNotEmpty() }) return null
        val now = System.currentTimeMillis()
        val merged = Trip(
            id = "merge-${originals.first().start}-${originals.last().end}-$now",
            start = originals.minOf { it.start }, end = originals.maxOf { it.end },
            distanceMeters = originals.sumOf { it.distanceMeters },
            movingTimeMs = originals.sumOf { it.movingTimeMs },
            maxSpeedMs = originals.maxOf { it.maxSpeedMs },
            points = originals.flatMap { it.points }.sortedBy { it.time },
            obdSamples = originals.flatMap { it.obdSamples }.sortedBy { it.timeMs },
            mergedFromIds = originals.map { it.id },
        )
        originals.forEach { save(ctx, it.copy(archivedByMerge = true)) }
        save(ctx, merged)
        return merged
    }

    @Synchronized
    fun unmerge(ctx: Context, id: String): Boolean {
        val merged = get(ctx, id)?.takeIf { it.mergedFromIds.isNotEmpty() } ?: return false
        runCatching { File(dir(ctx), "$id.json").delete() }
        merged.mergedFromIds.forEach { originalId -> get(ctx, originalId)?.let { save(ctx, it.copy(archivedByMerge = false)) } }
        updateIndex(ctx) { it.removeAll { trip -> trip.id == id } }
        return true
    }

    // ── v2.3: the ride in progress, written every ~30 s by TripRecorder. If the app process dies mid-ride
    // (22 Oct 2026, Manel: a native crash while restarting the video decoder) the ride used to exist only
    // in memory and was lost. On the next start [recoverCheckpoint] saves it as a normal trip.

    private fun checkpointFile(ctx: Context) = File(ctx.applicationContext.filesDir, "trip-in-progress.json")

    fun writeCheckpoint(ctx: Context, trip: Trip) {
        val obj = JSONObject()
            .put("id", trip.id).put("start", trip.start).put("end", trip.end)
            .put("distanceMeters", trip.distanceMeters).put("movingTimeMs", trip.movingTimeMs)
            .put("maxSpeedMs", trip.maxSpeedMs.toDouble())
            .put("archivedByMerge", trip.archivedByMerge)
            .put("mergedFromIds", JSONArray(trip.mergedFromIds))
        val pts = JSONArray()
        for (p in trip.points) pts.put(JSONArray().put(p.lat).put(p.lon).put(p.time).put(p.speedMs.toDouble()))
        obj.put("points", pts)
        obj.put("obdSamples", encodeObd(trip.obdSamples))
        runCatching {
            val f = checkpointFile(ctx)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(obj.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    fun clearCheckpoint(ctx: Context) { runCatching { checkpointFile(ctx).delete() } }

    /** A ride left behind by a process that died while recording, if any (and removes the file). */
    fun takeCheckpoint(ctx: Context): Trip? {
        val f = checkpointFile(ctx)
        if (!f.isFile) return null
        val t = parse(f)
        f.delete()
        return t
    }

    private fun parse(file: File, includeObd: Boolean = true): Trip? = runCatching {
        val o = JSONObject(file.readText())
        val ptsArr = o.optJSONArray("points") ?: JSONArray()
        val points = ArrayList<TrackPoint>(ptsArr.length())
        for (i in 0 until ptsArr.length()) {
            val a = ptsArr.optJSONArray(i) ?: continue
            points.add(
                TrackPoint(
                    lat = a.optDouble(0),
                    lon = a.optDouble(1),
                    time = a.optLong(2),
                    speedMs = a.optDouble(3).toFloat(),
                )
            )
        }
        Trip(
            id = o.optString("id"),
            start = o.optLong("start"),
            end = o.optLong("end"),
            distanceMeters = o.optDouble("distanceMeters"),
            movingTimeMs = o.optLong("movingTimeMs"),
            maxSpeedMs = o.optDouble("maxSpeedMs").toFloat(),
            points = points,
            obdSamples = if (includeObd) decodeObd(o.optJSONArray("obdSamples")) else emptyList(),
            archivedByMerge = o.optBoolean("archivedByMerge"),
            mergedFromIds = o.optJSONArray("mergedFromIds")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        )
    }.getOrNull()

    private fun encodeObd(samples: List<ObdTripSample>): JSONArray = JSONArray().also { out ->
        for (s in samples) {
            out.put(JSONArray().put(s.timeMs).putNullable(s.rpm).putNullable(s.speedKmh)
                .putNullable(s.coolantC).putNullable(s.throttlePct).putNullable(s.voltage)
                .putNullable(s.engineLoadPct).putNullable(s.intakeAirC).putNullable(s.baroKpa)
                .putNullable(s.absoluteLoadPct).putNullable(s.relativeThrottlePct))
        }
    }

    private fun JSONArray.putNullable(value: Double?): JSONArray = put(value ?: JSONObject.NULL)

    private fun decodeObd(array: JSONArray?): List<ObdTripSample> {
        if (array == null) return emptyList()
        fun JSONArray.number(index: Int): Double? =
            if (isNull(index)) null else optDouble(index).takeIf { it.isFinite() }
        return (0 until array.length()).mapNotNull { i ->
            val a = array.optJSONArray(i) ?: return@mapNotNull null
            ObdTripSample(
                timeMs = a.optLong(0), rpm = a.number(1), speedKmh = a.number(2),
                coolantC = a.number(3), throttlePct = a.number(4), voltage = a.number(5),
                engineLoadPct = a.number(6), intakeAirC = a.number(7), baroKpa = a.number(8),
                absoluteLoadPct = a.number(9), relativeThrottlePct = a.number(10),
            )
        }
    }
}
