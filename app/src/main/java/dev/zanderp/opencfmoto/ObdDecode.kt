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
        Pid(0x0B, "intake pressure", "kPa", 1) { it[0].toDouble() },
        Pid(0x0C, "rpm", "rpm", 2) { (it[0] * 256 + it[1]) / 4.0 },
        Pid(0x0D, "speed", "km/h", 1) { it[0].toDouble() },
        Pid(0x0E, "timing advance", "°", 1) { it[0] / 2.0 - 64 },
        Pid(0x0F, "intake air", "°C", 1) { it[0] - 40.0 },
        Pid(0x11, "throttle", "%", 1) { it[0] * 100.0 / 255 },
        Pid(0x1F, "run time", "s", 2) { (it[0] * 256 + it[1]).toDouble() },
        Pid(0x2F, "fuel level", "%", 1) { it[0] * 100.0 / 255 },
        Pid(0x33, "baro pressure", "kPa", 1) { it[0].toDouble() },
        Pid(0x42, "module voltage", "V", 2) { (it[0] * 256 + it[1]) / 1000.0 },
        Pid(0x46, "ambient air", "°C", 1) { it[0] - 40.0 },
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

    fun format(v: Double, unit: String): String =
        (if (v % 1.0 == 0.0 || unit == "rpm") "%.0f".format(v) else "%.1f".format(v)) + " " + unit
}
