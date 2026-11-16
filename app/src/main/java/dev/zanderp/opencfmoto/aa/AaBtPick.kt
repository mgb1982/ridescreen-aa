// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto.aa

/**
 * Pure choice of the Bluetooth device handed to Android Auto as "the car" in self mode (unit tested).
 *
 * Field log 16 Nov 2026 (Zontes 125X): with the dash's Bluetooth not yet connected the old rule picked a
 * stale bond ("Carkit"), and with the OBD adapter connected it picked the adapter; both starts failed and
 * the third one worked. The dash's Bluetooth is named "ZT" + digits on Zontes and was not recognised.
 * Order now: the saved bike (Bluetooth trigger) → a connected bike → any connected device that is not the
 * OBD adapter → a bonded bike → (audio connected) first bond → first bond; the OBD adapter never.
 */
object AaBtPick {
    data class Dev(val name: String?, val mac: String, val connected: Boolean)

    private val BIKE_WORDS = listOf(
        "CFMOTO", "CFDL", "Bike", "Moto", "VOGE", "QJ", "Zontes", "Benelli", "Morini", "Alltrhike", "Kove",
    )
    private val ZONTES_DASH = Regex("^ZT[0-9A-F]{4,}$", RegexOption.IGNORE_CASE)

    fun isBikeName(name: String?): Boolean {
        val n = name?.trim() ?: return false
        return ZONTES_DASH.matches(n) || BIKE_WORDS.any { n.contains(it, ignoreCase = true) }
    }

    fun pick(bonded: List<Dev>, savedBikeMac: String?, obdAdapterName: String?, audioConnected: Boolean): Dev? {
        val usable = bonded.filterNot { obdAdapterName != null && it.name == obdAdapterName }
        val connected = usable.filter { it.connected }
        return usable.firstOrNull { savedBikeMac != null && it.mac.equals(savedBikeMac, ignoreCase = true) }
            ?: connected.firstOrNull { isBikeName(it.name) }
            ?: connected.firstOrNull()
            ?: usable.firstOrNull { isBikeName(it.name) }
            ?: if (audioConnected) usable.firstOrNull() else null
            ?: usable.firstOrNull()
    }
}
