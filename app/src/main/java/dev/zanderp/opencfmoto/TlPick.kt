// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** Pure choice of which junction a map tap selects (no Android, unit-tested in TlPickTest). */
internal object TlPick {
    /**
     * [candidatesSortedByDistance]: item indexes within tap range, nearest first.
     * [currentIndex]: index of the currently selected item, if any.
     * Returns the index to select, or null when nothing is in range.
     * Tapping again on the selected item cycles to the next candidate (wrapping around).
     */
    fun next(candidatesSortedByDistance: List<Int>, currentIndex: Int?): Int? {
        if (candidatesSortedByDistance.isEmpty()) return null
        val pos = if (currentIndex == null) -1 else candidatesSortedByDistance.indexOf(currentIndex)
        if (pos < 0) return candidatesSortedByDistance.first()
        return candidatesSortedByDistance[(pos + 1) % candidatesSortedByDistance.size]
    }
}
