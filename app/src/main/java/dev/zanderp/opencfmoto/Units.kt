// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import java.util.Locale

/** Unit labels for numbers formatted outside string resources (functions with no Context). */
object Units {
    fun km(lang: String = Locale.getDefault().language): String = if (lang == "ru") "км" else "km"
    fun min(lang: String = Locale.getDefault().language): String = when (lang) { "ru" -> "мин"; "el" -> "λεπ."; else -> "min" }
}
