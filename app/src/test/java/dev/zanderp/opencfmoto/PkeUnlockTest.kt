package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkeUnlockTest {
    @Test
    fun mcuIdMustBeExactlyOneHexEncodedAesBlock() {
        assertTrue(PkeUnlock.isValidMcuId("0123456789ABCDEF0123456789ABCDEF"))
        assertTrue(PkeUnlock.isValidMcuId("abcdef0123456789abcdef0123456789"))
        assertFalse(PkeUnlock.isValidMcuId("A1B2"))
        assertFalse(PkeUnlock.isValidMcuId("0123456789ABCDEF0123456789ABCDE"))
        assertFalse(PkeUnlock.isValidMcuId("0123456789ABCDEF0123456789ABCDEFG"))
        assertFalse(PkeUnlock.isValidMcuId("0123456789ABCDEF0123456789ABCDEG"))
    }

    @Test
    fun deriveRejectsMalformedMcuIdBeforeAes() {
        assertEquals(
            PkeUnlock.Derived.BadMcuId,
            PkeUnlock.derive("123456789012345", "A1B2", byteArrayOf(1, 2, 3, 4))
        )
    }

    @Test
    fun mcuIdExtractedFromInfoQrText() {
        val id = "A1B2C3D4E5F60718293A4B5C6D7E8F90"
        // A key named mcuid/McuID wins, as a URL param, JSON field or key:value line.
        assertEquals(id, PkeUnlock.mcuIdFromQr("https://x/y?sn=abc&McuID=$id&color=black"))
        assertEquals(id, PkeUnlock.mcuIdFromQr("{\"vin\":\"XXX\",\"mcuid\":\"$id\"}"))
        assertEquals(id, PkeUnlock.mcuIdFromQr("model:125X\nMcuId: $id\ncolor:black"))
        // No key, but a single 32-hex run in the text: take it.
        assertEquals(id, PkeUnlock.mcuIdFromQr("$id"))
        assertEquals(id, PkeUnlock.mcuIdFromQr("random preamble $id trailing"))
        // The real Zontes 125X information QR: 10 ';'-separated positional fields, no key names,
        // the McuID the only 32-hex field (shape confirmed from Manel's bike, 28 Oct 2026).
        assertEquals(id, PkeUnlock.mcuIdFromQr("1A2;B3C;0123456789ABCDE;AABBCCDD;11223344;;;$id;;99887766"))
        // Lower case is normalised up.
        assertEquals(id, PkeUnlock.mcuIdFromQr("mcuid=${id.lowercase()}"))
        // Nothing usable.
        assertNull(PkeUnlock.mcuIdFromQr("no hex here at all"))
        assertNull(PkeUnlock.mcuIdFromQr("sn=0501234567890123"))
        // Several unkeyed 32-hex runs are ambiguous, so give up rather than guess.
        assertNull(PkeUnlock.mcuIdFromQr("$id and also 00112233445566778899AABBCCDDEEFF"))
        // A 40-hex token must not yield a 32-char slice of it.
        assertNull(PkeUnlock.mcuIdFromQr("hash=A1B2C3D4E5F60718293A4B5C6D7E8F9000112233"))
    }

    @Test
    fun controlFrameUsesTheExpectedActionCode() {
        val frame = PkeUnlock.frame("123456789012345", PkeUnlock.Action.UNLOCK, "00112233445566778899AABBCCDDEEFF")
        assertEquals("*BT,123456789012345,10,001,2,00112233445566778899AABBCCDDEEFF#", String(frame, Charsets.US_ASCII))
    }
}
