// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible trampoline behind the "Dashboard" launcher shortcut (long-press the app icon, or drag it to the
 * home screen): flips the dash between Android Auto and the custom dashboard, then closes at once.
 */
class DashboardToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (DashboardMode.toggle(this) == null) {
            Toast.makeText(this, R.string.dash_need_stream, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
