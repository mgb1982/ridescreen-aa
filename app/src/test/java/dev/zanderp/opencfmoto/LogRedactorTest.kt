package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {
    @Test
    fun redactsUrlPassword() {
        val out = LogRedactor.redact("qr ssid=CFMOTO-x&pwd=supersecret&modelid=1")
        assertTrue(out.contains("pwd=«redacted»"))
        assertFalse(out.contains("supersecret"))
        assertTrue(out.contains("ssid=CFMOTO-x"))
    }

    @Test
    fun masksSerialTail() {
        val out = LogRedactor.redact("""{"HUID":"CRCP230501740"}""")
        assertTrue(out.contains("CRCP"))
        assertFalse(out.contains("CRCP230501740"))
    }

    @Test
    fun masksHardwareAddressesButKeepsNamesAndLastBytes() {
        val out = LogRedactor.redact("[BT-TRIG] PKE-4404 (F3:81:52:33:7D:F4) connected · want ZT851066 DD:0D:30:85:10:66 and aa-bb-cc-dd-ee-ff")
        assertFalse(out.contains("F3:81:52:33"))
        assertFalse(out.contains("DD:0D:30:85"))
        assertTrue(out.contains("PKE-4404 (**:**:**:**:7D:F4)"))
        assertTrue(out.contains("ZT851066 **:**:**:**:10:66"))
        assertTrue(out.contains("**:**:**:**:ee:ff"))
    }

    @Test
    fun leavesTimesAndShortHexAlone() {
        val s = "13:09:29.501  [OBD] → 0100  ← 4100BE3EB813 at 12:30:45"
        assertTrue(LogRedactor.redact(s) == s)
    }

    @Test
    fun updateCheckerVersionCompare() {
        assertTrue(UpdateChecker.isNewer("v1.0.16", "1.0.15"))
        assertFalse(UpdateChecker.isNewer("1.0.15", "1.0.16"))
        assertTrue(UpdateChecker.isNewer("0.1.2.1", "0.1.2"))
    }
}
