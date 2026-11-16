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
 * OBD adapter → a bonded bike → first bond; an OBD adapter (by setting or by name) never.
 */
object AaBtPick {
    data class Dev(val name: String?, val mac: String, val connected: Boolean)

    private val BIKE_WORDS = listOf(
        "CFMOTO", "CFDL", "Bike", "Moto", "VOGE", "QJ", "Zontes", "Benelli", "Morini", "Alltrhike", "Kove",
    )
    private val ZONTES_DASH = Regex("^ZT[0-9A-F]{4,}$", RegexOption.IGNORE_CASE)

    private val OBD_WORDS = listOf("OBD", "ELM", "Vlink", "V-LINK", "Vgate", "iCar", "Veepeak", "Konnwei", "Carista")

    /** OBD adapters (by name) are never "the car", even when the live reader was never switched on. */
    fun isObdName(name: String?): Boolean {
        val n = name ?: return false
        return OBD_WORDS.any { n.contains(it, ignoreCase = true) }
    }

    fun isBikeName(name: String?): Boolean {
        val n = name?.trim() ?: return false
        return ZONTES_DASH.matches(n) || BIKE_WORDS.any { n.contains(it, ignoreCase = true) }
    }

    /** Log form of a MAC: only the last two groups (house rule: no full MACs in logs). */
    fun mask(mac: String?): String? = mac?.split(':')?.let { if (it.size == 6) "**:**:**:**:${it[4]}:${it[5]}" else "**" }

    fun pick(bonded: List<Dev>, savedBikeMac: String?, obdAdapterName: String?, audioConnected: Boolean): Dev? {
        val usable = bonded.filterNot { (obdAdapterName != null && it.name == obdAdapterName) || isObdName(it.name) }
        val connected = usable.filter { it.connected }
        // The Bluetooth trigger is any device the rider chose (helmet, watch…): only trusted as the car if it
        // looks like a bike (it is adopted automatically from the dash, "ZT…", otherwise).
        val savedBike = usable.firstOrNull {
            savedBikeMac != null && it.mac.equals(savedBikeMac, ignoreCase = true) && isBikeName(it.name)
        }
        return savedBike
            ?: connected.firstOrNull { isBikeName(it.name) }
            ?: connected.firstOrNull()
            ?: usable.firstOrNull { isBikeName(it.name) }
            ?: usable.firstOrNull()
    }
}
