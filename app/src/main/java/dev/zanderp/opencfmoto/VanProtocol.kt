// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/**
 * EasyConn "VAN" vehicle-data frames, carried over the same EC-BTP serial framing as [EcBtpProtocol]
 * (`0x24 | CMD | LEN | payload | XOR | 0x0A`, multi-byte values little-endian).
 *
 * Direction (read from the EasyConn SDK shipped in the official Zontes app):
 *  - 0x30 dash → phone: model JSON (`modelid`, `sn`, `carbrand`, `carmode`, `gpsid`)
 *  - 0x31 dash → phone: handshake, payload = the field ids the dash can provide
 *    phone → dash: the same command with the field ids the phone wants pushed
 *  - 0x32 dash → phone: realtime fields   (id + value, see [parseRealtime])
 *  - 0x33 dash → phone: slower fields     (id + value, see [parseNonRealtime])
 *
 * The phone only ever sends one kind of frame, the 0x31 subscription list (its half of the handshake):
 * in answer to the dash's offer or, in the probe, volunteered when the dash stays silent. Nothing
 * that controls the bike exists here.
 */
internal object VanProtocol {
    const val CMD_MODEL: Byte = 0x30
    const val CMD_HANDSHAKE: Byte = 0x31
    const val CMD_REALTIME: Byte = 0x32
    const val CMD_NON_REALTIME: Byte = 0x33

    /** What the official Zontes app asks for (warnings, oil, water/oil temp, tyres, range, battery). */
    val OFFICIAL_ITEMS = byteArrayOf(5, 6, 7, 9, 10, 11, 12, 17)

    /** The official list plus trip (13) and odometer (14). */
    val OFFICIAL_PLUS_MILEAGE = byteArrayOf(5, 6, 7, 9, 10, 11, 12, 13, 14, 17)

    /** Everything the protocol defines for a petrol bike: adds speed, RPM, gear, lights, status, consumption, max speed, maintenance km left, fault byte. */
    val FULL = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 28, 30, 31)

    /**
     * "Insist" mode, in answer to the dash's own 0x31 (one list per offer, so a dash that keeps
     * re-offering sees the next one): the useful list, exactly the official one (in case the dash
     * validates it), then everything.
     */
    val INSIST_REPLIES: List<ByteArray> = listOf(OFFICIAL_PLUS_MILEAGE, OFFICIAL_ITEMS, FULL)

    /** "Insist" mode, volunteered when the dash never speaks first: the replies above plus an empty list as a plain nudge. */
    val INSIST_ASKS: List<ByteArray> = INSIST_REPLIES + listOf(ByteArray(0))

    /** Splits a frame into BLE writes of at most [size] bytes, like the official stack (20 bytes, no MTU request). */
    fun chunks(frame: ByteArray, size: Int = 20): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var offset = 0
        while (offset < frame.size) {
            out += frame.copyOfRange(offset, minOf(offset + size, frame.size))
            offset += size
        }
        return out
    }

    data class Field(val id: Int, val raw: Long, val bytes: Int)

    fun name(id: Int): String = when (id) {
        1 -> "speed"; 2 -> "rpm"; 3 -> "light"; 4 -> "status"; 5 -> "warning"; 6 -> "oil"
        7 -> "water_t"; 8 -> "gear"; 9 -> "oil_t"; 10 -> "tyre_p"; 11 -> "tyre_t"
        12 -> "range"; 13 -> "trip"; 14 -> "odometer"; 15 -> "consume_oil"; 16 -> "consume_elec"
        17 -> "battery_v"; 28 -> "max_speed"; 30 -> "maint_left"; 31 -> "fault"
        else -> "id$id"
    }

    fun handshakeResponse(items: ByteArray): ByteArray = EcBtpProtocol.build(CMD_HANDSHAKE, items)

    fun handshakeItems(payload: ByteArray): List<Int> = payload.map { it.toInt() and 0xFF }

    fun parseRealtime(payload: ByteArray): List<Field> = parseFields(payload) { id ->
        when (id) {
            1, 2, 3, 4, 28, 30 -> 2
            5, 10, 11 -> 4
            6, 7, 8, 9, 31 -> 1
            else -> 0
        }
    }

    fun parseNonRealtime(payload: ByteArray): List<Field> = parseFields(payload) { id ->
        when (id) {
            12, 13, 15, 16, 28, 30, 32 -> 2
            14 -> 4
            in 17..25, 31 -> 1
            else -> 0
        }
    }

    private fun parseFields(payload: ByteArray, widthOf: (Int) -> Int): List<Field> {
        val out = ArrayList<Field>()
        var i = 0
        while (i < payload.size) {
            val id = payload[i].toInt() and 0xFF
            val width = widthOf(id)
            if (width == 0 || i + 1 + width > payload.size) break
            var v = 0L
            for (k in 0 until width) v = v or ((payload[i + 1 + k].toLong() and 0xFF) shl (8 * k))
            if (width == 4) v = v.toInt().toLong()
            out += Field(id, v, width)
            i += 1 + width
        }
        return out
    }

    /**
     * Human reading of a field, following how the official app formats it (VANFragment): byte values
     * as-is for fuel %, water °C, battery V and range km; oil and tyre temperatures carry a −40
     * offset; tyre pressure is two bytes (bits 24–31 then 16–23) in 0.1 steps (labelled "pa" in the
     * app but by magnitude bar — unconfirmed), and front/rear order is not confirmed either.
     * Trip and odometer are shown raw: their unit is unknown until a live value is compared.
     */
    fun describe(f: Field): String {
        val v = f.raw and 0xFFFFFFFFL
        val a = ((v shr 24) and 0xFF).toInt()
        val b = ((v shr 16) and 0xFF).toInt()
        return when (f.id) {
            6 -> "${f.raw} %"
            7 -> "${f.raw} °C"
            9 -> "${f.raw - 40} °C"
            17 -> "${f.raw} V"
            12 -> "${f.raw} km"
            10 -> "%.1f / %.1f (bar?)".format(java.util.Locale.US, a * 0.1, b * 0.1)
            11 -> "${a - 40} / ${b - 40} °C"
            13 -> "${f.raw} (trip, unit?)"
            14 -> "${f.raw} (odometer, unit?)"
            5 -> "warning bits 0x" + java.lang.Long.toHexString(v)
            1 -> "${f.raw} (speed, unit?)"
            2 -> "${f.raw} (rpm?)"
            8 -> "gear ${f.raw}"
            3 -> "light bits 0x" + java.lang.Long.toHexString(v)
            4 -> "status bits 0x" + java.lang.Long.toHexString(v)
            15 -> "${f.raw} (consumption, unit?)"
            28 -> "${f.raw} (max speed, unit?)"
            30 -> "${f.raw} (km to maintenance?)"
            31 -> "fault byte ${f.raw}"
            else -> ""
        }
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    /** Reassembles frames from BLE notifications (the dash may split them in 20-byte chunks). */
    class Stream {
        private var buf = ByteArray(0)

        fun feed(chunk: ByteArray): List<EcBtpProtocol.Frame> {
            buf += chunk
            val frames = ArrayList<EcBtpProtocol.Frame>()
            var guard = 0
            while (buf.isNotEmpty() && guard++ < 64) {
                val start = buf.indexOf(EcBtpProtocol.START)
                if (start < 0) { buf = ByteArray(0); break }
                if (start > 0) buf = buf.copyOfRange(start, buf.size)
                if (buf.size < 3) break
                val total = (buf[2].toInt() and 0xFF) + 1
                if (total < 5) { buf = buf.copyOfRange(1, buf.size); continue }
                if (buf.size < total) break
                val candidate = buf.copyOfRange(0, total)
                val frame = EcBtpProtocol.parse(candidate)
                if (frame != null) {
                    frames += frame
                    buf = buf.copyOfRange(total, buf.size)
                } else {
                    buf = buf.copyOfRange(1, buf.size)
                }
            }
            if (buf.size > 512) buf = ByteArray(0)
            return frames
        }
    }
}
