package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun controlFrameUsesTheExpectedActionCode() {
        val frame = PkeUnlock.frame("123456789012345", PkeUnlock.Action.UNLOCK, "00112233445566778899AABBCCDDEEFF")
        assertEquals("*BT,123456789012345,10,001,2,00112233445566778899AABBCCDDEEFF#", String(frame, Charsets.US_ASCII))
    }
}
