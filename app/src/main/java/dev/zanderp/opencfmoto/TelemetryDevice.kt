// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * What the anonymous telemetry says about the phone itself, to see which phones the low-memory kills
 * concentrate on: the model name ("samsung SM-S918B") and the installed RAM. Both come from Android's
 * public build info; nothing here identifies the person. Pure, unit tested.
 */
internal object TelemetryDevice {
    private const val MAX_LABEL = 48

    /** "samsung SM-S918B": manufacturer and model, the manufacturer not repeated when the model starts with it. */
    fun label(manufacturer: String?, model: String?): String {
        val mf = clean(manufacturer)
        val md = clean(model)
        val joined = when {
            md.isEmpty() -> mf
            mf.isEmpty() -> md
            md.startsWith(mf, ignoreCase = true) -> md
            else -> "$mf $md"
        }
        return joined.take(MAX_LABEL)
    }

    /** Letters, digits, spaces and . _ - only; runs of spaces collapsed. */
    private fun clean(s: String?): String =
        (s ?: "").filter { it.isLetterOrDigit() || it in " ._-" }.trim().replace(Regex(" +"), " ")

    private val CLASSES_GB = intArrayOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32)

    /**
     * Installed RAM as the phone's marketing class in megabytes (a "4 GB" phone reports 3.6-3.9 GB of usable
     * memory and reads as 4096; an "8 GB" one reads as 8192), so phones of the same class group together.
     * Above 32 GB it rounds up to a whole gigabyte; 0 when unknown.
     */
    fun ramMb(totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        val mb = totalBytes / (1024.0 * 1024.0)
        for (gb in CLASSES_GB) if (mb <= gb * 1024.0) return gb * 1024
        return (Math.ceil(mb / 1024.0) * 1024.0).toInt()
    }
}
