package dev.zanderp.opencfmoto

/** The data the rider can switch on or off in a saved trip video (what each look actually draws). */
enum class VideoField { SPEED, RPM, COOLANT, THROTTLE_LOAD, VOLTAGE, LEAN, G_FORCE, HEART_RATE, MINIMAP, PEAK_BANNERS }

/** What a trip carries, reduced to flags so the logic below needs no Android classes. */
data class VideoDataFlags(
    val rpm: Boolean, val coolant: Boolean, val throttleOrLoad: Boolean, val voltage: Boolean,
    val lean: Boolean, val g: Boolean, val heartRate: Boolean,
) {
    companion object {
        fun of(trip: Trip) = VideoDataFlags(
            rpm = trip.obdSamples.any { it.rpm != null },
            coolant = trip.obdSamples.any { it.coolantC != null },
            throttleOrLoad = trip.obdSamples.any { it.throttlePct != null || it.relativeThrottlePct != null || it.engineLoadPct != null },
            voltage = trip.obdSamples.any { it.voltage != null },
            lean = trip.motionSamples.any { it.leanDeg != null },
            g = trip.motionSamples.any { it.gLat != null || it.gLon != null },
            heartRate = trip.motionSamples.any { it.bpm != null },
        )
    }
}

/** Pure rules for the "Data in the video" picker: which fields a look draws, which exist, presets, storage. */
object VideoFields {
    val ALL: Set<VideoField> = VideoField.values().toSet()

    /** The fields a look draws at all (the picker lists only these). */
    fun relevant(style: VideoStyle): List<VideoField> = when (style) {
        VideoStyle.CLASSIC, VideoStyle.SPORT, VideoStyle.MINIMAL, VideoStyle.CINEMA, VideoStyle.RETRO ->
            listOf(VideoField.SPEED, VideoField.LEAN, VideoField.G_FORCE, VideoField.HEART_RATE, VideoField.MINIMAP)
        VideoStyle.TELEMETRY -> listOf(VideoField.SPEED, VideoField.RPM, VideoField.COOLANT, VideoField.THROTTLE_LOAD,
            VideoField.VOLTAGE, VideoField.LEAN, VideoField.G_FORCE, VideoField.HEART_RATE, VideoField.MINIMAP)
        VideoStyle.GAUGES -> listOf(VideoField.SPEED, VideoField.RPM, VideoField.COOLANT, VideoField.THROTTLE_LOAD,
            VideoField.LEAN, VideoField.G_FORCE, VideoField.HEART_RATE, VideoField.MINIMAP)
        VideoStyle.HUD -> listOf(VideoField.SPEED, VideoField.RPM, VideoField.COOLANT, VideoField.THROTTLE_LOAD,
            VideoField.LEAN, VideoField.G_FORCE, VideoField.HEART_RATE, VideoField.MINIMAP, VideoField.PEAK_BANNERS)
    }

    /**
     * Fields with data in this trip for [style]. Speed (GPS) and the map always exist. Gauges and HUD also
     * derive a G figure from the speed, so G is there for them even without motion samples.
     */
    fun available(style: VideoStyle, d: VideoDataFlags): Set<VideoField> = relevant(style).filter {
        when (it) {
            VideoField.SPEED, VideoField.MINIMAP, VideoField.PEAK_BANNERS -> true
            VideoField.RPM -> d.rpm
            VideoField.COOLANT -> d.coolant
            VideoField.THROTTLE_LOAD -> d.throttleOrLoad
            VideoField.VOLTAGE -> d.voltage
            VideoField.LEAN -> d.lean
            VideoField.G_FORCE -> d.g || style == VideoStyle.GAUGES || style == VideoStyle.HUD
            VideoField.HEART_RATE -> d.heartRate
        }
    }.toSet()

    /** "Short video": speed, lean and heart rate, only those this trip has. */
    fun shortPreset(style: VideoStyle, d: VideoDataFlags): Set<VideoField> =
        available(style, d).intersect(setOf(VideoField.SPEED, VideoField.LEAN, VideoField.HEART_RATE))

    /** What the picker shows as checked: the saved choice, minus what the trip cannot show. */
    fun checked(saved: Set<VideoField>, style: VideoStyle, d: VideoDataFlags): Set<VideoField> =
        saved.intersect(available(style, d))

    /**
     * New saved set after the rider confirmed the picker: fields the look draws and the trip has follow the
     * [picked] boxes; every other field keeps its earlier value (so a trip without heart rate does not
     * switch it off for the next trip).
     */
    fun merge(saved: Set<VideoField>, picked: Set<VideoField>, style: VideoStyle, d: VideoDataFlags): Set<VideoField> {
        val editable = available(style, d)
        return (saved - editable) + picked.intersect(editable)
    }

    /** Stored as the disabled names, so an empty string means "everything on" and new fields default on. */
    fun encode(enabled: Set<VideoField>): String =
        VideoField.values().filter { it !in enabled }.joinToString(",") { it.name }

    fun decode(s: String?): Set<VideoField> {
        if (s.isNullOrBlank()) return ALL
        val off = s.split(',').mapNotNull { n -> VideoField.values().firstOrNull { it.name == n.trim() } }.toSet()
        return ALL - off
    }

    fun key(style: VideoStyle) = "fields_" + style.name

    fun load(p: android.content.SharedPreferences, style: VideoStyle): Set<VideoField> = decode(p.getString(key(style), null))

    fun save(p: android.content.SharedPreferences, style: VideoStyle, enabled: Set<VideoField>) {
        p.edit().putString(key(style), encode(enabled)).apply()
    }
}
