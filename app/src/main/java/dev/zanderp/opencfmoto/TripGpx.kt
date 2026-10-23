// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Serialize a recorded [Trip] to GPX 1.1 for share / export. */
object TripGpx {
    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    fun suggestedFileName(trip: Trip): String =
        "opencfmoto_trip_${fileStamp.format(Date(trip.start))}.gpx"

    fun toXml(trip: Trip): String {
        val name = escape("OpenCfMoto ${trip.dateText()} ${trip.timeRangeText()}")
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="OpenCfMoto" """)
        sb.append("""xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sb.append("  <metadata>\n")
        sb.append("    <name>").append(name).append("</name>\n")
        sb.append("    <time>").append(isoUtc.format(Date(trip.start))).append("</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(name).append("</name>\n")
        sb.append("    <type>motorcycle</type>\n")
        sb.append("    <trkseg>\n")
        for (p in trip.points) {
            sb.append("      <trkpt lat=\"")
                .append(fmt(p.lat))
                .append("\" lon=\"")
                .append(fmt(p.lon))
                .append("\">\n")
            if (p.time > 0L) {
                sb.append("        <time>").append(isoUtc.format(Date(p.time))).append("</time>\n")
            }
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    // ── v2.3: import a ride from a GPX file (22 Oct 2026: a ride lost when the app died, rebuilt from
    // Google Maps Timeline). Track points need a <time>; <speed> (m/s, in <extensions> or GPX 1.0) and a
    // <distanceMeters> anywhere outside the points (e.g. the distance Google measured) are optional.

    class Imported(val trip: Trip, val pointCount: Int)

    fun fromXml(xml: String): Imported? {
        data class P(val lat: Double, val lon: Double, val t: Long, val speed: Float?)
        val pts = ArrayList<P>()
        var declaredDistance: Double? = null
        val parser = android.util.Xml.newPullParser()
        parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(java.io.StringReader(xml))
        var inPt = false
        var lat = 0.0; var lon = 0.0; var t = 0L; var speed: Float? = null
        var tag = ""
        var ev = parser.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (ev) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    tag = parser.name.substringAfter(':')
                    if (tag == "trkpt" || tag == "rtept") {
                        inPt = true
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: Double.NaN
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: Double.NaN
                        t = 0L; speed = null
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    val v = parser.text.trim()
                    if (v.isNotEmpty()) when {
                        inPt && tag == "time" -> t = parseTime(v)
                        inPt && tag == "speed" -> speed = v.toFloatOrNull()
                        !inPt && tag == "distanceMeters" -> declaredDistance = v.toDoubleOrNull()
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    val name = parser.name.substringAfter(':')
                    if ((name == "trkpt" || name == "rtept") && inPt) {
                        inPt = false
                        if (!lat.isNaN() && !lon.isNaN() && t > 0L) pts.add(P(lat, lon, t, speed))
                    }
                    tag = ""
                }
            }
            ev = parser.next()
        }
        pts.sortBy { it.t }
        if (pts.size < 2) return null
        var dist = 0.0
        var moving = 0L
        var max = 0f
        val out = FloatArray(1)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            android.location.Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, out)
            val dt = b.t - a.t
            dist += out[0]
            val segSpeed = if (dt > 0) out[0] / (dt / 1000f) else 0f
            if (dt in 1..300_000 && segSpeed >= 1.4f) moving += dt
            if (segSpeed < 70f && segSpeed > max) max = segSpeed
        }
        pts.mapNotNull { it.speed }.filter { it < 70f }.maxOrNull()?.let { if (it > max) max = it }
        val d = declaredDistance?.takeIf { it > dist && it < dist * 1.6 } ?: dist
        val trip = Trip(
            id = pts.first().t.toString(),
            start = pts.first().t,
            end = pts.last().t,
            distanceMeters = d,
            movingTimeMs = moving,
            maxSpeedMs = max,
            points = pts.map { TrackPoint(it.lat, it.lon, it.t, it.speed ?: 0f) },
        )
        return Imported(trip, pts.size)
    }

    private fun parseTime(v: String): Long = try {
        java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try { java.time.Instant.parse(v).toEpochMilli() } catch (_: Exception) { 0L }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.6f", v)

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
