// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import androidx.core.view.WindowInsetsCompat

/**
 * System bars without `systemOverlays()`. AndroidX's `Type.systemBars()` includes system overlays,
 * which it maps to the platform's `WindowInsets.Type.systemOverlays()` when SDK_INT ≥ 34. Some ROMs
 * report API 34 on an Android 13 framework that lacks that method → NoSuchMethodError on the first
 * layout pass of every screen (20 Oct 2026: one phone, 26 crashes on launch with 2.2.1).
 */
object SafeInsets {
    val BARS: Int = WindowInsetsCompat.Type.statusBars() or
        WindowInsetsCompat.Type.navigationBars() or
        WindowInsetsCompat.Type.captionBar()
}
