package dev.zanderp.opencfmoto

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Website images for 2.4: the style picker's own previews (DashboardMode.preview, sample data, 800×416 as in
 * ControlsActivity), day and night, at the 125X scale (140 km/h · 10,000 rpm) and at 240 km/h · 14,000 rpm.
 * Not an app test: it only writes PNGs to app/build/dashboard-snapshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class PickerSnapshotTest {
    @Test
    fun renderPickerPreviews() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val out = File("build/dashboard-snapshots").apply { mkdirs() }
        for ((speed, rpm) in listOf(140 to 10_000, 240 to 14_000)) {
            DashboardMode.setSpeedMax(ctx, speed)
            DashboardMode.setRpmMax(ctx, rpm)
            for (theme in listOf(DashboardMode.Theme.DARK, DashboardMode.Theme.LIGHT)) {
                DashboardMode.setTheme(ctx, theme)
                for (d in DashboardMode.Design.entries) {
                    val bmp = DashboardMode.preview(ctx, d, 800, 416)
                    val name = "${d.id}-${if (theme == DashboardMode.Theme.LIGHT) "day" else "night"}-${speed}-${rpm}.png"
                    File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }
}
