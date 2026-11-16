// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * Pure decoding of ELM327 / OBD-II mode 01 and 03 replies (SAE J1979). No Android here, so it is unit
 * tested. Only standard PIDs: what any EU5 ECU must answer through a generic adapter.
 */
object ObdDecode {
    /** A mode 01 PID we know how to show: name, unit and the formula over its data bytes. */
    class Pid(val pid: Int, val name: String, val unit: String, val bytes: Int, val calc: (IntArray) -> Double)

    val PIDS: List<Pid> = listOf(
        Pid(0x04, "engine load", "%", 1) { it[0] * 100.0 / 255 },
        Pid(0x05, "coolant", "°C", 1) { it[0] - 40.0 },
        // Extras (read rotating or once per connection, never shown on the dashboard): SAE J1979.
        Pid(0x06, "short fuel trim", "%", 1) { (it[0] - 128) * 100.0 / 128 },
        Pid(0x07, "long fuel trim", "%", 1) { (it[0] - 128) * 100.0 / 128 },
        Pid(0x0B, "intake pressure", "kPa", 1) { it[0].toDouble() },
        Pid(0x0C, "rpm", "rpm", 2) { (it[0] * 256 + it[1]) / 4.0 },
        Pid(0x0D, "speed", "km/h", 1) { it[0].toDouble() },
        Pid(0x0E, "timing advance", "°", 1) { it[0] / 2.0 - 64 },
        Pid(0x0F, "intake air", "°C", 1) { it[0] - 40.0 },
        // Absolute throttle: decoded but never shown (Manel, 5 Nov 2026: the relative one is more precise).
        Pid(0x11, "absolute throttle", "%", 1) { it[0] * 100.0 / 255 },
        Pid(0x14, "O2 B1S1", "V", 2) { it[0] / 200.0 },
        Pid(0x15, "O2 B1S2", "V", 2) { it[0] / 200.0 },
        Pid(0x1F, "run time", "s", 2) { (it[0] * 256 + it[1]).toDouble() },
        Pid(0x21, "km with MIL on", "km", 2) { (it[0] * 256 + it[1]).toDouble() },
        Pid(0x2E, "purge", "%", 1) { it[0] * 100.0 / 255 },
        Pid(0x2F, "fuel level", "%", 1) { it[0] * 100.0 / 255 },
        Pid(0x30, "warm-ups since clear", "", 1) { it[0].toDouble() },
        Pid(0x31, "km since clear", "km", 2) { (it[0] * 256 + it[1]).toDouble() },
        Pid(0x33, "baro pressure", "kPa", 1) { it[0].toDouble() },
        Pid(0x42, "module voltage", "V", 2) { (it[0] * 256 + it[1]) / 1000.0 },
        Pid(0x43, "absolute load", "%", 2) { (it[0] * 256 + it[1]) * 100.0 / 255 },
        // Relative throttle, shown everywhere as "throttle".
        Pid(0x45, "throttle", "%", 1) { it[0] * 100.0 / 255 },
        Pid(0x46, "ambient air", "°C", 1) { it[0] - 40.0 },
        Pid(0x4D, "min with MIL on", "min", 2) { (it[0] * 256 + it[1]).toDouble() },
        Pid(0x5C, "oil temperature", "°C", 1) { it[0] - 40.0 },
    )

    fun pid(p: Int): Pid? = PIDS.firstOrNull { it.pid == p }

    /**
     * All hex bytes of a reply, without the echo, "SEARCHING...", spaces, line numbers ("0:") or the
     * ">" prompt. Returns null for an adapter error ("NO DATA", "UNABLE TO CONNECT", "?"…).
     */
    fun bytesOf(reply: String): IntArray? {
        val up = reply.uppercase()
        if (ERRORS.any { up.contains(it) }) return null
        val lines = up.lines().map { it.trim() }
        // A multi-frame CAN reply starts with a bare length line ("00A") before "0:", "1:" … : not data.
        val framed = lines.any { it.contains(':') }
        val hex = lines
            .filter { !framed || it.contains(':') }
            .map { it.substringAfter(':').trim() }
            .filter { it.isNotEmpty() && !it.startsWith("SEARCHING") && !it.startsWith("BUS INIT") }
            .joinToString("")
            .filter { it in '0'..'9' || it in 'A'..'F' }
        if (hex.isEmpty() || hex.length % 2 != 0) return null
        return IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
    }

    private val ERRORS = listOf("NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS ERROR", "BUS BUSY", "STOPPED", "ERROR", "?")

    /** Data bytes of a mode 01 reply for [pid]: the bytes after "41 <pid>". Null if not found. */
    fun mode01Data(reply: String, pid: Int): IntArray? {
        val b = bytesOf(reply) ?: return null
        for (i in 0 until b.size - 1) if (b[i] == 0x41 && b[i + 1] == pid) return b.copyOfRange(i + 2, b.size)
        return null
    }

    fun value(reply: String, pid: Int): Double? {
        val p = pid(pid) ?: return null
        val d = mode01Data(reply, pid) ?: return null
        if (d.size < p.bytes) return null
        return p.calc(d)
    }

    /**
     * Reply to one request that asked for several PIDs at once ("01 0C 0D 05 11"): "41 0C xx xx 0D yy …"
     * in the order asked. Null unless every requested PID is there, so a bike whose ECU answers only the
     * first one is detected and the caller falls back to one PID per request.
     */
    fun multi(reply: String, pids: List<Int>): Map<Int, Double>? {
        val b = bytesOf(reply) ?: return null
        var i = b.indexOf(0x41)
        if (i < 0) return null
        i++
        val out = LinkedHashMap<Int, Double>()
        for (pid in pids) {
            val p = pid(pid) ?: return null
            if (i + 1 + p.bytes > b.size || b[i] != pid) return null
            out[pid] = p.calc(b.copyOfRange(i + 1, i + 1 + p.bytes))
            i += 1 + p.bytes
        }
        return out
    }

    /** PIDs flagged in a "supported PIDs" reply (01 00 / 01 20 / 01 40 / 01 60): base+1 … base+32. */
    fun supported(reply: String, base: Int): List<Int> {
        val d = mode01Data(reply, base) ?: return emptyList()
        if (d.size < 4) return emptyList()
        val out = ArrayList<Int>()
        for (byte in 0 until 4) for (bit in 0 until 8) {
            if (d[byte] and (0x80 shr bit) != 0) out += base + byte * 8 + bit + 1
        }
        return out
    }

    /** 01 01: malfunction light on, and how many stored codes. */
    fun milAndCount(reply: String): Pair<Boolean, Int>? {
        val d = mode01Data(reply, 0x01) ?: return null
        if (d.isEmpty()) return null
        return Pair(d[0] and 0x80 != 0, d[0] and 0x7F)
    }

    /** Mode 03 stored trouble codes ("43 …"), as P0123 / C… / B… / U…; zero pairs are padding. */
    fun troubleCodes(reply: String): List<String> {
        val b = bytesOf(reply) ?: return emptyList()
        val start = b.indexOf(0x43).takeIf { it >= 0 } ?: return emptyList()
        var data = b.copyOfRange(start + 1, b.size)
        // CAN replies carry a count byte first: an odd number of bytes gives it away.
        if (data.size % 2 == 1) data = data.copyOfRange(1, data.size)
        val out = ArrayList<String>()
        var i = 0
        while (i + 1 < data.size) {
            val a = data[i]; val c = data[i + 1]
            if (a != 0 || c != 0) {
                val letter = "PCBU"[a shr 6]
                out += "%c%d%X%02X".format(letter, (a shr 4) and 0x3, a and 0xF, c)
            }
            i += 2
        }
        return out
    }

    /** Oxygen sensor trim of PIDs 14 / 15 (second byte, % ); null when 0xFF = "not used" or the bytes are missing. */
    fun o2Trim(d: IntArray): Double? = if (d.size < 2 || d[1] == 0xFF) null else (d[1] - 128) * 100.0 / 128

    /** Text of the once-per-connection PIDs 01 / 03 / 13 / 1C from their mode 01 reply; null if unreadable. */
    fun fixedText(pid: Int, reply: String): String? {
        val d = mode01Data(reply, pid) ?: return null
        return when (pid) {
            0x01 -> monitorText(d)
            0x03 -> fuelSystemText(d)
            0x13 -> o2PresentText(d)
            0x1C -> obdStandardText(d)
            else -> null
        }
    }

    /** PID 01: MIL, stored code count and the readiness monitors that are supported but not complete. */
    fun monitorText(d: IntArray): String? {
        if (d.size < 4) return null
        val parts = arrayListOf("MIL " + if (d[0] and 0x80 != 0) "on" else "off", "${d[0] and 0x7F} DTC")
        val diesel = d[1] and 0x08 != 0
        val cont = listOf("misfire", "fuel", "components")
        val open = ArrayList<String>()
        for (i in 0..2) if (d[1] and (1 shl i) != 0 && d[1] and (0x10 shl i) != 0) open += cont[i]
        val names = if (diesel) listOf("NMHC cat", "NOx/SCR", "", "boost", "", "exhaust sensor", "PM filter", "EGR/VVT")
            else listOf("catalyst", "heated cat", "evap", "sec. air", "A/C", "O2 sensor", "O2 heater", "EGR")
        for (i in 0..7) if (names[i].isNotEmpty() && d[2] and (1 shl i) != 0 && d[3] and (1 shl i) != 0) open += names[i]
        parts += if (open.isEmpty()) "monitors ready" else "not ready: " + open.joinToString(",")
        return parts.joinToString(", ")
    }

    /** PID 03: fuel system status (loop state of system 1, and system 2 when present). */
    fun fuelSystemText(d: IntArray): String? {
        if (d.isEmpty()) return null
        fun one(b: Int) = when (b) {
            0 -> "off"; 1 -> "open loop (cold)"; 2 -> "closed loop"; 4 -> "open loop (load)"
            8 -> "open loop (fault)"; 16 -> "closed loop (fault)"; else -> "0x%02X".format(b)
        }
        return one(d[0]) + if (d.size > 1 && d[1] != 0) " / " + one(d[1]) else ""
    }

    /** PID 13: which oxygen sensors exist (bit 0 = bank 1 sensor 1 … bit 7 = bank 2 sensor 4). */
    fun o2PresentText(d: IntArray): String? {
        if (d.isEmpty()) return null
        val l = (0..7).filter { d[0] and (1 shl it) != 0 }.map { "B${it / 4 + 1}S${it % 4 + 1}" }
        return if (l.isEmpty()) "none" else l.joinToString(" ")
    }

    /** PID 1C: the OBD standard the vehicle declares. */
    fun obdStandardText(d: IntArray): String? {
        if (d.isEmpty()) return null
        return when (val v = d[0]) {
            1 -> "OBD-II (CARB)"; 2 -> "OBD (EPA)"; 3 -> "OBD and OBD-II"; 4 -> "OBD-I"; 5 -> "not OBD"
            6 -> "EOBD"; 7 -> "EOBD and OBD-II"; 8 -> "EOBD and OBD"; 9 -> "EOBD, OBD and OBD-II"
            10 -> "JOBD"; 11 -> "JOBD and OBD-II"; 12 -> "JOBD and EOBD"; 13 -> "JOBD, EOBD and OBD-II"
            else -> "standard $v"
        }
    }

    fun format(v: Double, unit: String): String =
        (if (v % 1.0 == 0.0 || unit == "rpm") "%.0f".format(v) else "%.1f".format(v)) + " " + unit
}
