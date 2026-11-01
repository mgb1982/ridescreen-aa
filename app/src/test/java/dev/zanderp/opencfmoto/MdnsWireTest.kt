package dev.zanderp.opencfmoto

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MdnsWireTest {
    private fun name(vararg labels: String): ByteArray {
        val o = ByteArrayOutputStream()
        for (l in labels) { o.write(l.length); o.write(l.toByteArray()) }
        o.write(0)
        return o.toByteArray()
    }

    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun record(owner: ByteArray, type: Int, rdata: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(owner); o.write(u16(type)); o.write(u16(1)); o.write(byteArrayOf(0, 0, 0, 120)); o.write(u16(rdata.size)); o.write(rdata)
        return o.toByteArray()
    }

    /** A response the way a dash would send it: PTR → SRV + TXT + A, the last records compressed against the first. */
    private fun response(port: Int, txt: List<String>, ip: ByteArray): ByteArray {
        val inst = name("ZT-350E", "_EasyConn", "_tcp", "local")
        val host = name("dash", "local")
        val svc = name("_EasyConn", "_tcp", "local")
        val srvRdata = ByteArrayOutputStream().also { it.write(u16(0)); it.write(u16(0)); it.write(u16(port)); it.write(host) }.toByteArray()
        val txtRdata = ByteArrayOutputStream().also { o -> txt.forEach { o.write(it.length); o.write(it.toByteArray()) } }.toByteArray()
        val body = ByteArrayOutputStream()
        body.write(record(svc, 12, inst))
        body.write(record(inst, 33, srvRdata))
        body.write(record(inst, 16, txtRdata))
        body.write(record(host, 1, ip))
        val head = byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 4, 0, 0, 0, 0)
        return head + body.toByteArray()
    }

    @Test
    fun readsPortTxtAndAddressFromAnAnnouncement() {
        val pkt = response(
            10931,
            listOf("packagename=net.easyconn.carman.neutral", "channel=zontes", "flavor=eu", "huname=ZT350E"),
            byteArrayOf(192.toByte(), 168.toByte(), 2, 1),
        )
        val svcs = MdnsWire.parse(pkt, pkt.size)
        assertEquals(1, svcs.size)
        val s = svcs[0]
        assertEquals(10931, s.port)
        assertEquals("net.easyconn.carman.neutral", s.txt["packagename"])
        assertEquals("zontes", s.txt["channel"])
        assertEquals("eu", s.txt["flavor"])
        assertEquals("ZT350E", s.txt["huname"])
        assertEquals("192.168.2.1", s.addresses.single().hostAddress)
    }

    @Test
    fun followsCompressionPointers() {
                val first = name("_EasyConn", "_tcp", "local")
        val inst = name("x", "_EasyConn", "_tcp", "local")
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 2, 0, 0, 0, 0))
        o.write(record(first, 12, inst))
        val srv = ByteArrayOutputStream().also { it.write(u16(0)); it.write(u16(0)); it.write(u16(1234)); it.write(byteArrayOf(0xC0.toByte(), 22)) }.toByteArray()
        // offset 22 = the "_tcp" label of the first name (12 header + 1 length byte + 9 chars): resolves to "_tcp.local"
        o.write(record(inst, 33, srv))
        val pkt = o.toByteArray()
        val svcs = MdnsWire.parse(pkt, pkt.size)
        assertEquals(1, svcs.size)
        assertEquals(1234, svcs[0].port)
    }

    @Test
    fun malformedOrUnrelatedPacketsGiveNothing() {
        assertTrue(MdnsWire.parse(ByteArray(3), 3).isEmpty())
        val junk = ByteArray(40) { 0xFF.toByte() }
        assertTrue(MdnsWire.parse(junk, junk.size).isEmpty())
        // A pointer loop must not hang or throw.
        val loop = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0)
        assertTrue(MdnsWire.parse(loop, loop.size).isEmpty())
    }

    @Test
    fun queryAsksForPtrWithTheUnicastBit() {
        val q = MdnsWire.buildQuery(P2pClientDiscovery.SERVICE)
        assertEquals(1, q[5].toInt())            // QDCOUNT
        assertEquals(9, q[12].toInt())           // "_EasyConn"
        assertEquals(12, q[q.size - 3].toInt())  // QTYPE PTR (low byte)
        assertEquals(0x80, q[q.size - 2].toInt() and 0xFF)
        assertEquals(1, q[q.size - 1].toInt())
    }

    @Test
    fun onlyPlausiblePackageNamesAreEchoedBack() {
        assertEquals("net.easyconn.carman.neutral", MdnsWire.safePackage(" net.easyconn.carman.neutral "))
        assertNull(MdnsWire.safePackage("a\",\"x\":\"b"))
        assertNull(MdnsWire.safePackage("nodots"))
        assertNull(MdnsWire.safePackage(null))
    }

    @Test
    fun qrIpIsOnlyUsedWhenItIsAUsableIpv4() {
        assertEquals("192.168.2.1", P2pClientDiscovery.parseIpv4("192.168.2.1")?.hostAddress)
        assertNull(P2pClientDiscovery.parseIpv4("0.0.0.0"))
        assertNull(P2pClientDiscovery.parseIpv4("127.0.0.1"))
        assertNull(P2pClientDiscovery.parseIpv4("300.1.1.1"))
        assertNull(P2pClientDiscovery.parseIpv4("224.0.0.251"))
        assertNull(P2pClientDiscovery.parseIpv4("not-an-ip"))
        assertNull(P2pClientDiscovery.parseIpv4(null))
    }

    @Test
    fun qrCarriesItsIp() {
        val raw = "http://www.carbit.com.cn/downsdk/657/658/_sdk?modelid=34808&action=8&mac=02:11:22:33:44:55&name=ZT_TEST&ip=192.168.2.1"
        val qr = QrData.parse(raw)!!
        assertEquals(8, qr.action)
        assertEquals("192.168.2.1", qr.ip)
        assertNull(QrData.parse(raw.substringBefore("&ip="))!!.ip)
    }
}
