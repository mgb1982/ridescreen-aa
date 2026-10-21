package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VanProtocolTest {
    @Test fun subscriptionIsOnlyTheHandshakeCommand() {
        val frame = VanProtocol.handshakeResponse(VanProtocol.OFFICIAL_ITEMS)
        assertEquals(0x24.toByte(), frame[0])
        assertEquals(0x31.toByte(), frame[1])
        val parsed = EcBtpProtocol.parse(frame)!!
        assertEquals(0x31.toByte(), parsed.command)
        assertTrue(parsed.payload.contentEquals(VanProtocol.OFFICIAL_ITEMS))
    }

    @Test fun mileageListAddsOnlyTripAndOdometer() {
        val extra = VanProtocol.OFFICIAL_PLUS_MILEAGE.toSet() - VanProtocol.OFFICIAL_ITEMS.toSet()
        assertEquals(setOf<Byte>(13, 14), extra)
        // Never asks for speed, rpm or gear.
        assertTrue(VanProtocol.OFFICIAL_PLUS_MILEAGE.none { it.toInt() in setOf(1, 2, 8) })
    }

    @Test fun parsesRealtimeFields() {
        val payload = byteArrayOf(1, 0x34, 0x12, 10, 0x10, 0x27, 0, 0, 6, 5)
        val fields = VanProtocol.parseRealtime(payload)
        assertEquals(3, fields.size)
        assertEquals(0x1234L, fields[0].raw)
        assertEquals(10, fields[1].id)
        assertEquals(10000L, fields[1].raw)
        assertEquals(5L, fields[2].raw)
    }

    @Test fun parsesNonRealtimeOdometerAndTrip() {
        val payload = byteArrayOf(14, 0x40, 0x42, 0x0F, 0, 13, 0x0A, 0)
        val fields = VanProtocol.parseNonRealtime(payload)
        assertEquals(1_000_000L, fields[0].raw)
        assertEquals(10L, fields[1].raw)
    }

    @Test fun negativeIntegerAndUnknownIdsAreSafe() {
        assertEquals(-1L, VanProtocol.parseRealtime(byteArrayOf(11, -1, -1, -1, -1))[0].raw)
        assertTrue(VanProtocol.parseRealtime(byteArrayOf(99, 1, 2)).isEmpty())
        // Truncated field is dropped, not read past the end.
        assertTrue(VanProtocol.parseRealtime(byteArrayOf(10, 1, 2)).isEmpty())
    }

    @Test fun streamReassemblesSplitFrames() {
        val frame = EcBtpProtocol.build(VanProtocol.CMD_REALTIME, byteArrayOf(1, 0x34, 0x12, 10, 0x10, 0x27, 0, 0, 6, 5))
        val stream = VanProtocol.Stream()
        val first = stream.feed(frame.copyOfRange(0, 7))
        assertTrue(first.isEmpty())
        val rest = stream.feed(frame.copyOfRange(7, frame.size))
        assertEquals(1, rest.size)
        assertEquals(VanProtocol.CMD_REALTIME, rest[0].command)
    }

    @Test fun streamSkipsNoise() {
        val frame = EcBtpProtocol.build(VanProtocol.CMD_HANDSHAKE, byteArrayOf(1, 2, 3))
        val out = VanProtocol.Stream().feed(byteArrayOf(0x00, 0x55) + frame)
        assertEquals(1, out.size)
        assertEquals(listOf(1, 2, 3), VanProtocol.handshakeItems(out[0].payload))
    }

    @Test fun describesFieldsLikeTheOfficialApp() {
        // Tyre pressure 2.6 / 2.5 in the top two bytes; temps carry the -40 offset.
        val tyreP = VanProtocol.Field(10, (26L shl 24) or (25L shl 16), 4)
        assertEquals("2.6 / 2.5 (bar?)", VanProtocol.describe(tyreP))
        val tyreT = VanProtocol.Field(11, (60L shl 24) or (58L shl 16), 4)
        assertEquals("20 / 18 °C", VanProtocol.describe(tyreT))
        assertEquals("50 °C", VanProtocol.describe(VanProtocol.Field(9, 90, 1)))
        assertEquals("12 V", VanProtocol.describe(VanProtocol.Field(17, 12, 1)))
    }

    @Test fun fullListAddsSpeedRpmGearOnTopOfMileage() {
        val extra = VanProtocol.FULL.toSet() - VanProtocol.OFFICIAL_PLUS_MILEAGE.toSet()
        assertEquals(setOf<Byte>(1, 2, 3, 4, 8, 15, 28, 30, 31), extra)
        // Read-only subscription: only ids the protocol defines as data, nothing above 32.
        assertTrue(VanProtocol.FULL.all { it in 1..32 })
    }

    @Test fun chunksSplitAtTwentyBytesAndRejoinToTheSameFrame() {
        val full = VanProtocol.handshakeResponse(VanProtocol.FULL)
        assertEquals(24, full.size)
        val parts = VanProtocol.chunks(full)
        assertEquals(listOf(20, 4), parts.map { it.size })
        assertTrue(parts.reduce { a, b -> a + b }.contentEquals(full))
        // The official list fits in a single write; nothing to send is no write at all.
        assertEquals(1, VanProtocol.chunks(VanProtocol.handshakeResponse(VanProtocol.OFFICIAL_ITEMS)).size)
        assertTrue(VanProtocol.chunks(ByteArray(0)).isEmpty())
    }

    @Test fun everythingTheProbeCanVolunteerIsASubscriptionFrame() {
        // The probe's only writes are built with handshakeResponse: command 0x31, ids inside the data range.
        for (items in VanProtocol.INSIST_ASKS + VanProtocol.INSIST_REPLIES) {
            val frame = EcBtpProtocol.parse(VanProtocol.handshakeResponse(items))!!
            assertEquals(VanProtocol.CMD_HANDSHAKE, frame.command)
            assertTrue(frame.payload.all { it in 1..32 })
        }
    }

    @Test fun insistTriesTheUsefulListFirstThenOfficialThenEverythingThenAnEmptyNudge() {
        assertEquals(4, VanProtocol.INSIST_ASKS.size)
        assertTrue(VanProtocol.INSIST_ASKS[0].contentEquals(VanProtocol.OFFICIAL_PLUS_MILEAGE))
        assertTrue(VanProtocol.INSIST_ASKS[1].contentEquals(VanProtocol.OFFICIAL_ITEMS))
        assertTrue(VanProtocol.INSIST_ASKS[2].contentEquals(VanProtocol.FULL))
        assertEquals(0, VanProtocol.INSIST_ASKS[3].size)
        // Answers to the dash's own offer never use the empty nudge.
        assertTrue(VanProtocol.INSIST_REPLIES.none { it.isEmpty() })
    }

    @Test fun emptySubscriptionIsAValidFrame() {
        val frame = VanProtocol.handshakeResponse(ByteArray(0))
        assertEquals(5, frame.size)
        assertEquals(0, EcBtpProtocol.parse(frame)!!.payload.size)
    }

    @Test fun parsesMaxSpeedMaintenanceAndFaultByte() {
        // id 28 / 30 are words, id 31 is a single byte, in either packet type.
        val payload = byteArrayOf(28, 0x2C, 0x01, 30, 0x10, 0x27, 31, 2)
        for (fields in listOf(VanProtocol.parseRealtime(payload), VanProtocol.parseNonRealtime(payload))) {
            assertEquals(3, fields.size)
            assertEquals(300L, fields[0].raw)
            assertEquals(10000L, fields[1].raw)
            assertEquals(2L, fields[2].raw)
        }
    }
}
