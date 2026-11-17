package dev.zanderp.opencfmoto

/**
 * 2.5 · the rider's own picture behind the dashboards. Pure helpers (no Android types) so they run in unit
 * tests; the file handling is in [DashBackgroundStore] and the drawing in [DashboardRenderer].
 */
object DashBackground {
    enum class Mode(val id: String) {
        DEFAULT("default"), IMAGE("image");
        companion object { fun byId(id: String?): Mode = entries.firstOrNull { it.id == id } ?: DEFAULT }
    }

    enum class Fit(val id: String) {
        /** Crop to fill the whole canvas. */
        FILL("fill"),
        /** The whole picture, edges filled with the average colour of its borders. */
        FIT("fit");
        companion object { fun byId(id: String?): Fit = entries.firstOrNull { it.id == id } ?: FILL }
    }

    const val VEIL_MIN = 0
    const val VEIL_MAX = 80
    const val VEIL_DEFAULT = 40
    /** The saved picture is at most this wide (and [MAX_STORED_H] tall). */
    const val MAX_STORED_W = 1280
    const val MAX_STORED_H = 2048
    const val DIR = "dash_bg"

    /** What the renderer needs. [path] is only used when [mode] is IMAGE. */
    data class Setting(
        val mode: Mode = Mode.DEFAULT,
        val fit: Fit = Fit.FILL,
        val veil: Int = VEIL_DEFAULT,
        val version: Long = 0L,
        val path: String? = null,
    ) {
        val isImage: Boolean get() = mode == Mode.IMAGE
        /** Part of the static layer's cache key and of the preview keys. */
        fun key(): String = cacheKey(mode, fit, veil, version)
        companion object { val DEFAULT = Setting() }
    }

    class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    fun clampVeil(v: Int): Int = v.coerceIn(VEIL_MIN, VEIL_MAX)

    /** Alpha (0..255) of the palette-colour veil laid over the picture. */
    fun veilAlpha(veilPct: Int): Int = Math.round(clampVeil(veilPct) * 255f / 100f)

    fun cacheKey(mode: Mode, fit: Fit, veil: Int, version: Long): String =
        if (mode == Mode.DEFAULT) "bg:default" else "bg:${mode.id}:${fit.id}:${clampVeil(veil)}:$version"

    /** File name per bike; [bikeKey] is [BikeScope.suffix] or null (no bike selected). Only safe characters. */
    fun fileName(bikeKey: String?, png: Boolean = false): String {
        val safe = (bikeKey ?: "").filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "global" }
        return "$safe.${if (png) "png" else "jpg"}"
    }

    /** A picture with transparent pixels (a PNG crest) is saved as PNG so it keeps them; the rest as JPEG. */
    fun savesAsPng(anyTransparent: Boolean): Boolean = anyTransparent

    fun isPngName(path: String?): Boolean = path?.endsWith(".png", ignoreCase = true) == true

    /** True when any of the first [count] ARGB pixels is not fully opaque. */
    fun anyTransparent(pixels: IntArray, count: Int = pixels.size): Boolean {
        for (i in 0 until minOf(count, pixels.size)) if ((pixels[i] ushr 24) != 0xFF) return true
        return false
    }

    /**
     * Only this bike's own picture counts: a saved IMAGE mode (which may be inherited from the global
     * setting) with no file for this bike is the original background.
     */
    fun effectiveMode(saved: Mode, fileExists: Boolean): Mode = if (fileExists) saved else Mode.DEFAULT

    /**
     * Where to draw a [srcW]x[srcH] picture on a [dstW]x[dstH] canvas. FILL: scaled to cover and centred (the
     * box overflows the canvas, which clips it). FIT: scaled to fit entirely and centred.
     */
    fun destBox(fit: Fit, srcW: Int, srcH: Int, dstW: Int, dstH: Int): Box {
        if (srcW <= 0 || srcH <= 0) return Box(0f, 0f, dstW.toFloat(), dstH.toFloat())
        val sx = dstW.toFloat() / srcW
        val sy = dstH.toFloat() / srcH
        val s = if (fit == Fit.FILL) maxOf(sx, sy) else minOf(sx, sy)
        val w = srcW * s
        val h = srcH * s
        val l = (dstW - w) / 2f
        val t = (dstH - h) / 2f
        return Box(l, t, l + w, t + h)
    }

    /** Size to store: at most [MAX_STORED_W] wide and [MAX_STORED_H] tall, never enlarged. Returns (w, h). */
    fun storedSize(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1 to 1
        val s = minOf(1f, MAX_STORED_W.toFloat() / w, MAX_STORED_H.toFloat() / h)
        return maxOf(1, Math.round(w * s)) to maxOf(1, Math.round(h * s))
    }

    /** Power-of-two inSampleSize that keeps the decoded picture at least [targetW]x[targetH]. */
    fun sampleSize(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
        if (srcW <= 0 || srcH <= 0 || targetW <= 0 || targetH <= 0) return 1
        var s = 1
        while (srcW / (s * 2) >= targetW && srcH / (s * 2) >= targetH) s *= 2
        return s
    }

    /** Flip left-right first, then rotate clockwise by [degrees]. */
    data class Orientation(val degrees: Int, val flipH: Boolean)

    /** EXIF orientation (values of android.media.ExifInterface.ORIENTATION_*, 2/4/5/7 are mirrored) to a transform. */
    fun exifTransform(orientation: Int): Orientation = when (orientation) {
        2 -> Orientation(0, true)      // FLIP_HORIZONTAL
        3 -> Orientation(180, false)   // ROTATE_180
        4 -> Orientation(180, true)    // FLIP_VERTICAL
        5 -> Orientation(270, true)    // TRANSPOSE
        6 -> Orientation(90, false)    // ROTATE_90
        7 -> Orientation(90, true)     // TRANSVERSE
        8 -> Orientation(270, false)   // ROTATE_270
        else -> Orientation(0, false)
    }

    /** Average opaque colour (0xFFRRGGBB) of [pixels] (ARGB ints); black when empty. */
    fun averageColor(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0xFF000000.toInt()
        var r = 0L; var g = 0L; var b = 0L
        for (p in pixels) { r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF }
        val n = pixels.size
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}
