package dev.zanderp.opencfmoto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** JVM-only round-trip and rejection tests for the byte-level BLE protocol helpers. */
class BleProtocolTest {
    @Test
    fun frameRoundTripsCommandAndProtobufPayload() {
        val payload = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val frame = BleProtocol.encodeFrame(BleProtocol.CMD_AUTH_PKG, payload)

        val decoded = BleProtocol.decodeFrame(frame)
        requireNotNull(decoded)
        assertEquals(BleProtocol.CMD_AUTH_PKG, decoded.first)
        assertArrayEquals(payload, BleProtocol.unwrapProtoBytes1(decoded.second))
    }

    @Test
    fun decoderRejectsShortFramesBadMagicBadChecksumAndBadTrailer() {
        val valid = BleProtocol.encodeFrame(BleProtocol.CMD_ACK, byteArrayOf(0x00, 0x01))

        assertNull(BleProtocol.decodeFrame(byteArrayOf(0xAB.toByte(), 0xCD.toByte())))
        val badMagic = valid.copyOf().also { it[0] = 0x00 }
        assertNull(BleProtocol.decodeFrame(badMagic))

        val badChecksum = valid.copyOf().also {
            val index = it.lastIndex - 1
            it[index] = (it[index].toInt() xor 0x01).toByte()
        }
        assertNull(BleProtocol.decodeFrame(badChecksum))

        val badTrailer = valid.copyOf().also { it[it.lastIndex] = 0x00 }
        assertNull(BleProtocol.decodeFrame(badTrailer))
    }

    @Test
    fun protobufUnwrapRejectsEmptyWrongTagAndTruncatedPayload() {
        assertNull(BleProtocol.unwrapProtoBytes1(byteArrayOf()))
        assertNull(BleProtocol.unwrapProtoBytes1(byteArrayOf(0x08, 0x01)))
        assertNull(BleProtocol.unwrapProtoBytes1(byteArrayOf(0x0A, 0x03, 0x41, 0x42)))
        assertArrayEquals(
            byteArrayOf(0x41, 0x42),
            BleProtocol.unwrapProtoBytes1(byteArrayOf(0x0A, 0x02, 0x41, 0x42))
        )
    }

    @Test
    fun hexConversionSupportsWhitespaceAndUnsignedBytes() {
        assertArrayEquals(
            byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte()),
            BleProtocol.hexToBytes("00 7f\n80 FF")
        )
        assertEquals("007f80ff", BleProtocol.bytesToHex(byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())))
    }

    @Test
    fun hexConversionRejectsOddLengthAndNonHexCharacters() {
        assertThrows(IllegalArgumentException::class.java) { BleProtocol.hexToBytes("ABC") }
        assertThrows(IllegalArgumentException::class.java) { BleProtocol.hexToBytes("GG") }
    }
}
