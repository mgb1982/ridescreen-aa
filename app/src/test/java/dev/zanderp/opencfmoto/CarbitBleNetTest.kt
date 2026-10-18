// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbitBleNetTest {
    @Test fun reassemblesSplitFramesAndSkipsJunk() {
        val a = EcBtpProtocol.build(0x52, """{"status":1,"phoneApFrequency":2}""".toByteArray())
        val b = EcBtpProtocol.build(0x53, """{"cnt":1}""".toByteArray())
        val stream = byteArrayOf(0x00, 0x11) + a + b
        val r = CarbitBleNet.Reassembler()
        val got = ArrayList<EcBtpProtocol.Frame>()
        stream.toList().chunked(20).forEach { got += r.feed(it.toByteArray()) }
        assertEquals(listOf(0x52.toByte(), 0x53.toByte()), got.map { it.command })
        assertEquals("""{"cnt":1}""", String(got[1].payload))
    }

    @Test fun masksPasswords() {
        val m = CarbitBleNet.masked("""{"ssid":"AndroidShare_1","pwd":"abc123xyz","auth":"WPA2"}""")
        assertFalse(m.contains("abc123xyz"))
        assertTrue(m.contains("AndroidShare_1"))
    }

    @Test fun readsDashAddressFromCarNetInfo() {
        val json = "{\n\t\"cnt\":\t1,\n\t\"data\":\t[{\n\t\t\t\"name\":\t\"wlan0\",\n\t\t\t\"ip\":\t\"10.56.162.193\",\n\t\t\t\"mask\":\t\"255.255.255.0\"\n\t\t}]\n}"
        assertEquals("10.56.162.193", CarbitBleNet.dashIp(json))
    }
}
