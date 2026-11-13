package dev.zanderp.opencfmoto

import kotlin.math.abs

/**
 * Geometry of the schematic map the WINDOW design shows inside its Android Auto window in the style
 * picker preview only (the live dash leaves that area bare: the compositor puts Android Auto there).
 *
 * Everything is in coordinates normalised to the window (0..1, origin top-left). Arrays hold
 * consecutive x,y pairs: a line array is x0,y0,x1,y1 per segment, a rect array is left,top,right,bottom
 * per rect, and the route is a polyline of points. [scale] maps any of them to pixels.
 */
object WindowPreviewMap {
    /** Wide streets. */
    val MAJOR = floatArrayOf(
        0f, .22f, 1f, .22f,
        0f, .62f, 1f, .62f,
        .22f, 0f, .22f, 1f,
        .70f, 0f, .70f, 1f,
    )

    /** Narrow side streets. */
    val MINOR = floatArrayOf(
        0f, .42f, 1f, .42f,
        0f, .82f, 1f, .82f,
        .46f, 0f, .46f, 1f,
        .88f, 0f, .88f, 1f,
        .08f, 0f, .08f, 1f,
    )

    /** Diagonal avenues. */
    val DIAGONAL = floatArrayOf(
        0f, .95f, .46f, .42f,
        .46f, .42f, .70f, .22f,
        .70f, .62f, 1f, .38f,
    )

    /** Park blocks (left, top, right, bottom). */
    val PARKS = floatArrayOf(
        .24f, .44f, .44f, .60f,
        .72f, .04f, .86f, .20f,
    )

    /** Water (left, top, right, bottom). */
    val WATER = floatArrayOf(
        .72f, .68f, 1f, 1f,
        0f, 0f, .06f, .20f,
    )

    /** Built-up blocks (left, top, right, bottom). */
    val BLOCKS = floatArrayOf(
        .48f, .04f, .68f, .20f,
        .10f, .24f, .20f, .40f,
        .48f, .64f, .68f, .80f,
    )

    /** Highlighted route: up the left avenue, right turn at the junction, then up again. Points x,y. */
    val ROUTE = floatArrayOf(
        .22f, 1f,
        .22f, .62f,
        .70f, .62f,
        .70f, .22f,
        .70f, 0f,
    )

    /** The position dot, on the first leg of the route. */
    const val POS_X = .22f
    const val POS_Y = .80f

    /** Maps normalised x,y pairs into the pixel rectangle ([x], [y], [w], [h]). */
    fun scale(src: FloatArray, x: Float, y: Float, w: Float, h: Float): FloatArray {
        val out = FloatArray(src.size)
        var i = 0
        while (i + 1 < src.size) {
            out[i] = x + src[i] * w
            out[i + 1] = y + src[i + 1] * h
            i += 2
        }
        return out
    }

    /** Number of direction changes along a polyline of x,y points. */
    fun turns(route: FloatArray): Int {
        var n = 0
        var i = 4
        while (i + 1 < route.size) {
            val ax = route[i - 2] - route[i - 4]; val ay = route[i - 1] - route[i - 3]
            val bx = route[i] - route[i - 2]; val by = route[i + 1] - route[i - 1]
            if (abs(ax * by - ay * bx) > 1e-4f) n++
            i += 2
        }
        return n
    }
}
