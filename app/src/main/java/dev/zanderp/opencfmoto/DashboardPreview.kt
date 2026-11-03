// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect

/** Photographic option previews cropped from the approved six-panel dashboard mockup. */
internal object DashboardPreview {
    private val lock = Any()
    private var source: Bitmap? = null
    private val crops = mutableMapOf<Pair<DashboardMode.Design, Boolean>, Bitmap>()

    // Rectangles are the red-framed dash in each panel; true means the daylight row.
    private val bounds = mapOf(
        (DashboardMode.Design.SPORT to false) to Rect(72, 72, 461, 391),
        (DashboardMode.Design.RACING to false) to Rect(465, 72, 875, 391),
        (DashboardMode.Design.TOURING to false) to Rect(875, 72, 1279, 391),
        (DashboardMode.Design.SPORT to true) to Rect(72, 442, 461, 762),
        (DashboardMode.Design.RACING to true) to Rect(465, 442, 875, 762),
        (DashboardMode.Design.TOURING to true) to Rect(875, 442, 1279, 762),
    )

    fun bitmap(context: Context, design: DashboardMode.Design, daylight: Boolean): Bitmap =
        synchronized(lock) {
            val key = design to daylight
            crops[key] ?: run {
                val original = source ?: (BitmapFactory.decodeResource(
                    context.applicationContext.resources,
                    R.drawable.dashboard_style_previews,
                ) ?: error("Dashboard preview image could not be decoded")).also { source = it }
                val rect = bounds.getValue(key)
                require(rect.right <= original.width && rect.bottom <= original.height) {
                    "Dashboard preview crop exceeds source image"
                }
                Bitmap.createBitmap(original, rect.left, rect.top, rect.width(), rect.height())
                    .also { crops[key] = it }
            }
        }
}
