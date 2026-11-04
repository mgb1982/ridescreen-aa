// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import android.app.Application
import android.graphics.Bitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the six projected dashboards (Sport/Racing/Touring × night/day) with Robolectric's native
 * Canvas so they can be compared with the approved mockup without a bike. Not part of the CI test list:
 * run on demand by .github/workflows/dashboard-snapshots.yml, which uploads the PNGs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DashboardSnapshotTest {
    private val sample = DashboardMode.Data(
        rpm = 6_250.0, speedKmh = 87.0, speedFromGps = false, coolant = 94.0,
        throttle = 38.0, volt = 14.1, load = 44.0, intake = 42.0,
        baro = 100.0, absoluteLoad = 32.0, relativeThrottle = 38.0, obdLive = true,
    )
    private val fields = setOf("rpm", "speed", "coolant", "volt", "load", "intake", "baro", "absolute", "throttle")

    @Test
    fun renderAllStyles() {
        val ctx = RuntimeEnvironment.getApplication()
        val out = File(System.getProperty("user.dir"), "build/dashboard-snapshots").apply { mkdirs() }
        for (design in DashboardMode.Design.entries) for (light in listOf(false, true)) {
            for ((w, h) in listOf(800 to 416, 1024 to 464)) {
                val bmp = DashboardRenderer.render(ctx, w, h, sample, light, design, fields)
                    .copy(Bitmap.Config.ARGB_8888, false)
                File(out, "${design.id}-${if (light) "day" else "night"}-${w}x$h.png").outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
