// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Diagnostics for Wi‑Fi Direct-only dashes (Zontes 350D/350E, QR modelid 21313, action=8) where
 * the P2P group forms and Android Auto runs, but EasyConn never answers (Ricky, build 325:
 * :10930 refused, 10915–10935 and the 251 "thinkerride" ports closed, dash never dials back).
 *
 * Read-only towards the dash: TCP connects (no payload written), a short banner read, mDNS queries
 * and listening. The only "active" part is [startAnnounce]/[NsdExp], which advertises OUR OWN
 * `_EasyConn._tcp.` service (hypothesis H1: the dash looks for the phone instead of being probed).
 *
 * Gated by [isTarget]: modelid 21313 over Wi‑Fi Direct only, so no other bike changes behaviour.
 * Log tags: `[DIAG]`, `[DISC] fullscan …`, `[MDNS] …`, `[NSD-EXP] …`.
 */
object P2pDiag {

    const val TARGET_MODEL_ID = "21313"
    private const val FULLSCAN_THREADS = 256
    private const val FULLSCAN_CONNECT_MS = 300
    private const val FULLSCAN_DEADLINE_MS = 90_000L
    private const val BANNER_MS = 500
    private const val MDNS_PORT = 5353
    private val MDNS_GROUP: InetAddress = InetAddress.getByName("224.0.0.251")
    private const val EASYCONN_TYPE = "_EasyConn._tcp.local"

    @Volatile private var lastFullScanAt = 0L

    fun isTarget(ctx: Context, p2p: Boolean): Boolean =
        p2p && BikeMemory.lastQr(ctx)?.modelId == TARGET_MODEL_ID

    // ---------------------------------------------------------------- network dump

    fun dumpNetwork(
        myIp: Inet4Address,
        bikeIp: Inet4Address,
        servers: List<ServerSocket>,
        log: (String) -> Unit,
    ) {
        log("[DIAG] ---- network dump (diagnostic build, modelid $TARGET_MODEL_ID) ----")
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (ni in ifaces) {
                val addrs = ni.interfaceAddresses.joinToString { "${it.address.hostAddress}/${it.networkPrefixLength}" }
                if (addrs.isEmpty() && !ni.isUp) continue
                log("[DIAG] iface ${ni.name} up=${ni.isUp} mcast=${safe { ni.supportsMulticast() }} " +
                    "mtu=${safe { ni.mtu }} addrs=[$addrs]")
            }
            val mine = NetworkInterface.getByInetAddress(myIp)
            log("[DIAG] our IP ${myIp.hostAddress} lives on iface=${mine?.name ?: "?"}")
        } catch (e: Exception) {
            log("[DIAG] iface enumeration failed: $e")
        }
        for (ss in servers) {
            log("[DIAG] listener bound to ${ss.localSocketAddress} closed=${ss.isClosed}")
        }
        dumpFile("/proc/net/arp", log)
        dumpFile("/proc/net/route", log)
        dumpExec(listOf("ip", "neigh", "show"), log)
        dumpExec(listOf("ip", "-4", "addr", "show"), log)
        dumpExec(listOf("ip", "route", "show", "table", "all"), log)
        try {
            val t0 = System.currentTimeMillis()
            val ok = bikeIp.isReachable(1500)
            log("[DIAG] ${bikeIp.hostAddress} isReachable=$ok (${System.currentTimeMillis() - t0} ms)")
        } catch (e: Exception) {
            log("[DIAG] isReachable threw: $e")
        }
        log("[DIAG] ---- end network dump ----")
    }

    private fun dumpFile(path: String, log: (String) -> Unit) {
        try {
            val lines = File(path).readLines().take(20)
            log("[DIAG] $path (${lines.size} lines):")
            lines.forEach { log("[DIAG]   $it") }
        } catch (e: Exception) {
            log("[DIAG] $path unreadable: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun dumpExec(cmd: List<String>, log: (String) -> Unit) {
        try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(3, TimeUnit.SECONDS)
            val lines = out.lines().filter { it.isNotBlank() }.take(25)
            log("[DIAG] $ ${cmd.joinToString(" ")} → ${lines.size} line(s)")
            lines.forEach { log("[DIAG]   $it") }
        } catch (e: Exception) {
            log("[DIAG] $ ${cmd.joinToString(" ")} failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private inline fun <T> safe(f: () -> T): Any? = try { f() } catch (_: Exception) { "?" }

    // ---------------------------------------------------------------- full TCP scan

    /**
     * TCP connect-scan 1–65535 on [bikeIp] from [myIp]. Nothing is written to open ports; we only
     * read whatever the dash sends first (≤ [BANNER_MS]). Also counts refused vs timed-out ports:
     * all refused = host up, nothing listening; timeouts = something filtering.
     * Runs at most once every 5 minutes.
     */
    fun fullTcpScan(bikeIp: Inet4Address, myIp: Inet4Address, log: (String) -> Unit): List<Int> {
        val now = System.currentTimeMillis()
        if (now - lastFullScanAt < 5 * 60_000L) {
            log("[DISC] fullscan skipped (ran ${(now - lastFullScanAt) / 1000}s ago)")
            return emptyList()
        }
        lastFullScanAt = now
        log("[DISC] fullscan ${bikeIp.hostAddress} 1–65535 (threads=$FULLSCAN_THREADS, " +
            "timeout=${FULLSCAN_CONNECT_MS}ms, read-only)…")
        val open = ConcurrentHashMap<Int, String>()
        val refused = AtomicInteger(0)
        val timedOut = AtomicInteger(0)
        val other = AtomicInteger(0)
        val otherSample = ConcurrentHashMap<String, Int>()
        val done = AtomicInteger(0)
        val abort = AtomicBoolean(false)
        val deadline = now + FULLSCAN_DEADLINE_MS
        val pool = Executors.newFixedThreadPool(FULLSCAN_THREADS) { r ->
            Thread(r, "diag-fullscan").apply { isDaemon = true }
        }
        try {
            for (port in 1..65535) {
                pool.execute {
                    if (abort.get()) return@execute
                    if (System.currentTimeMillis() > deadline) { abort.set(true); return@execute }
                    val s = Socket()
                    try {
                        try { s.bind(InetSocketAddress(myIp, 0)) } catch (_: Exception) {}
                        s.connect(InetSocketAddress(bikeIp, port), FULLSCAN_CONNECT_MS)
                        open[port] = readBanner(s)
                    } catch (e: SocketTimeoutException) {
                        timedOut.incrementAndGet()
                    } catch (e: Exception) {
                        val m = e.message ?: ""
                        if (m.contains("ECONNREFUSED")) refused.incrementAndGet()
                        else {
                            other.incrementAndGet()
                            val key = e.javaClass.simpleName + ":" + m.substringAfterLast(':').trim().take(40)
                            otherSample.merge(key, 1, Int::plus)
                        }
                    } finally {
                        try { s.close() } catch (_: Exception) {}
                        val d = done.incrementAndGet()
                        if (d % 16384 == 0) log("[DISC] fullscan progress $d/65535 open=${open.keys.sorted()}")
                    }
                }
            }
            pool.shutdown()
            pool.awaitTermination(FULLSCAN_DEADLINE_MS + 5_000L, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            log("[DISC] fullscan error: $e")
        } finally {
            pool.shutdownNow()
        }
        val secs = (System.currentTimeMillis() - now) / 1000
        log("[DISC] fullscan done in ${secs}s: scanned=${done.get()} refused=${refused.get()} " +
            "timeout=${timedOut.get()} other=${other.get()}" +
            (if (abort.get()) " (DEADLINE hit — incomplete)" else ""))
        if (otherSample.isNotEmpty()) log("[DISC] fullscan other errors: ${otherSample.entries.take(5)}")
        val sorted = open.keys.sorted()
        if (sorted.isEmpty()) {
            log("[DISC] fullscan open: none")
        } else {
            log("[DISC] fullscan open: $sorted")
            for (p in sorted) log("[DISC] fullscan banner :$p ${open[p]}")
        }
        return sorted
    }

    private fun readBanner(s: Socket): String = try {
        s.soTimeout = BANNER_MS
        val buf = ByteArray(96)
        val n = s.getInputStream().read(buf)
        if (n <= 0) "(no banner, n=$n)" else describeBytes(buf, n)
    } catch (_: SocketTimeoutException) {
        "(silent ${BANNER_MS}ms)"
    } catch (e: Exception) {
        "(read failed: ${e.javaClass.simpleName})"
    }

    private fun describeBytes(b: ByteArray, n: Int): String {
        val hex = (0 until n).joinToString("") { "%02x".format(b[it]) }
        val ascii = (0 until n).joinToString("") {
            val c = b[it].toInt() and 0xff
            if (c in 0x20..0x7e) c.toChar().toString() else "."
        }
        return "hex=$hex ascii=\"$ascii\""
    }

    // ---------------------------------------------------------------- mDNS sniffer / announcer

    /**
     * Listens for mDNS traffic on the P2P interface (what the dash asks for or announces), sends
     * PTR queries (`_services._dns-sd._udp`, `_EasyConn._tcp`) and — when [announce] — periodically
     * announces our own `_EasyConn._tcp` service pointing at [myIp]:[announcePort].
     */
    class MdnsSniffer(
        private val myIp: Inet4Address,
        private val announce: Boolean,
        private val announcePort: Int,
        private val log: (String) -> Unit,
    ) {
        @Volatile private var running = false
        private var sock: MulticastSocket? = null
        private val seen = ConcurrentHashMap<String, Int>()
        private var logged = 0

        fun start(durationMs: Long) {
            if (running) return
            running = true
            thread(name = "diag-mdns", isDaemon = true) { runLoop(durationMs) }
        }

        fun stop() {
            running = false
            try { sock?.close() } catch (_: Exception) {}
        }

        private fun runLoop(durationMs: Long) {
            val iface = try { NetworkInterface.getByInetAddress(myIp) } catch (_: Exception) { null }
            if (iface == null) {
                log("[MDNS] no interface for ${myIp.hostAddress} — sniffer not started")
                running = false
                return
            }
            val s = openSocket(iface) ?: run { running = false; return }
            sock = s
            val until = System.currentTimeMillis() + durationMs
            var nextQuery = 0L
            var queries = 0
            var nextAnnounce = 0L
            var announces = 0
            val buf = ByteArray(9000)
            try {
                while (running && System.currentTimeMillis() < until) {
                    val t = System.currentTimeMillis()
                    if (queries < 3 && t >= nextQuery) {
                        send(s, Dns.query(listOf("_services._dns-sd._udp.local", EASYCONN_TYPE)), "query #${queries + 1}")
                        queries++
                        nextQuery = t + 2_000L * queries
                    }
                    if (announce && announces < 40 && t >= nextAnnounce) {
                        send(s, Dns.announceEasyConn(myIp, announcePort), if (announces == 0) "announce #1" else null)
                        announces++
                        if (announces == 1) {
                            log("[NSD-EXP] raw mDNS: announcing $EASYCONN_TYPE → ${myIp.hostAddress}:$announcePort " +
                                "on ${iface.name} every 2s (instance 'RideScreen')")
                        }
                        nextAnnounce = t + 2_000L
                    }
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val src = p.address
                    if (src == myIp) continue
                    val summary = try { Dns.summarize(p.data, p.length) } catch (e: Exception) {
                        "unparsed (${e.javaClass.simpleName}) " + describeBytes(p.data, minOf(p.length, 64))
                    }
                    val key = "${src.hostAddress}|$summary"
                    val count = seen.merge(key, 1, Int::plus) ?: 1
                    if (count == 1 && logged < 80) {
                        logged++
                        log("[MDNS] from ${src.hostAddress}:${p.port} len=${p.length} $summary")
                    }
                }
            } catch (e: Exception) {
                if (running) log("[MDNS] loop ended: $e")
            } finally {
                try { s.close() } catch (_: Exception) {}
                val fromDash = seen.keys.count { !it.startsWith("${myIp.hostAddress}|") }
                log("[MDNS] sniffer stopped: ${seen.size} distinct packet(s), $fromDash from others, " +
                    "queries=$queries announces=$announces")
                running = false
            }
        }

        private fun openSocket(iface: NetworkInterface): MulticastSocket? {
            // Prefer :5353 (shared) so we also hear multicast queries; fall back to an ephemeral
            // port (only unicast replies to our QU queries) if the system holds 5353 exclusively.
            for (port in listOf(MDNS_PORT, 0)) {
                try {
                    val s = MulticastSocket(null)
                    s.reuseAddress = true
                    s.bind(InetSocketAddress(port))
                    s.networkInterface = iface
                    try {
                        s.joinGroup(InetSocketAddress(MDNS_GROUP, MDNS_PORT), iface)
                    } catch (e: Exception) {
                        log("[MDNS] joinGroup on ${iface.name} failed: ${e.message}")
                    }
                    @Suppress("DEPRECATION")
                    s.loopbackMode = true // true = disable loopback
                    s.timeToLive = 255
                    s.soTimeout = 500
                    log("[MDNS] sniffer up on ${iface.name} local port ${s.localPort}")
                    return s
                } catch (e: Exception) {
                    log("[MDNS] bind :$port failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            return null
        }

        private fun send(s: MulticastSocket, data: ByteArray, label: String?) {
            try {
                s.send(DatagramPacket(data, data.size, MDNS_GROUP, MDNS_PORT))
                if (label != null) log("[MDNS] sent $label (${data.size} B)")
            } catch (e: Exception) {
                log("[MDNS] send ${label ?: "packet"} failed: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------- NsdManager experiment

    /** H1 via the platform: register `_EasyConn._tcp.` on [port]. Logged as `[NSD-EXP]`. */
    class NsdExp(private val ctx: Context, private val log: (String) -> Unit) {
        private var listener: NsdManager.RegistrationListener? = null

        fun register(listenPort: Int) {
            val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: run {
                log("[NSD-EXP] NsdManager unavailable"); return
            }
            val info = NsdServiceInfo().apply {
                serviceName = "RideScreen"
                serviceType = EasyConnDiscovery.SERVICE_TYPE
                port = listenPort
                setAttribute("packagename", EasyConnProber.SPOOFED_PACKAGE)
                setAttribute("phoneType", "Android")
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(si: NsdServiceInfo) {
                    log("[NSD-EXP] NsdManager registered '${si.serviceName}' ${EasyConnDiscovery.SERVICE_TYPE} :$listenPort")
                }
                override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                    log("[NSD-EXP] NsdManager registration failed: $errorCode")
                }
                override fun onServiceUnregistered(si: NsdServiceInfo) {
                    log("[NSD-EXP] NsdManager unregistered")
                }
                override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) {}
            }
            try {
                nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
                listener = l
            } catch (e: Exception) {
                log("[NSD-EXP] registerService threw: $e")
            }
        }

        fun unregister() {
            val l = listener ?: return
            listener = null
            try {
                (ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager)?.unregisterService(l)
            } catch (_: Exception) {}
        }
    }

    /** Short NsdManager browse of the meta-type (may be rejected by the platform; logged). */
    fun nsdBrowseAll(ctx: Context, durationMs: Long, log: (String) -> Unit) {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val latch = CountDownLatch(1)
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String?, e: Int) { log("[DIAG] NSD browse '$t' failed: $e"); latch.countDown() }
            override fun onStopDiscoveryFailed(t: String?, e: Int) {}
            override fun onDiscoveryStarted(t: String?) { log("[DIAG] NSD browsing '$t' for ${durationMs / 1000}s…") }
            override fun onDiscoveryStopped(t: String?) {}
            override fun onServiceFound(si: NsdServiceInfo) {
                log("[DIAG] NSD found name='${si.serviceName}' type='${si.serviceType}'")
            }
            override fun onServiceLost(si: NsdServiceInfo) {}
        }
        try {
            nsd.discoverServices("_services._dns-sd._udp", NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            log("[DIAG] NSD browse threw: $e"); return
        }
        try { latch.await(durationMs, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) {}
        try { nsd.stopServiceDiscovery(l) } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- tiny DNS codec

    private object Dns {
        private const val T_A = 1
        private const val T_PTR = 12
        private const val T_TXT = 16
        private const val T_SRV = 33
        private const val T_ANY = 255

        fun query(names: List<String>): ByteArray {
            val o = ByteArrayOutputStream()
            u16(o, 0); u16(o, 0); u16(o, names.size); u16(o, 0); u16(o, 0); u16(o, 0)
            for (n in names) {
                name(o, n); u16(o, T_PTR); u16(o, 0x8001) // IN + QU (ask for unicast reply too)
            }
            return o.toByteArray()
        }

        fun announceEasyConn(ip: Inet4Address, port: Int): ByteArray {
            val instance = "RideScreen.$EASYCONN_TYPE"
            val host = "ridescreen.local"
            val o = ByteArrayOutputStream()
            u16(o, 0); u16(o, 0x8400); u16(o, 0); u16(o, 4); u16(o, 0); u16(o, 0)
            // PTR
            rr(o, EASYCONN_TYPE, T_PTR, 0x0001, 120) { nameBytes(instance) }
            // SRV
            rr(o, instance, T_SRV, 0x8001, 120) {
                val b = ByteArrayOutputStream()
                u16(b, 0); u16(b, 0); u16(b, port); name(b, host); b.toByteArray()
            }
            // TXT
            rr(o, instance, T_TXT, 0x8001, 120) {
                val b = ByteArrayOutputStream()
                for (kv in listOf("packagename=${EasyConnProber.SPOOFED_PACKAGE}", "phoneType=Android",
                    "ip=${ip.hostAddress}")) {
                    val bytes = kv.toByteArray(Charsets.UTF_8)
                    b.write(bytes.size); b.write(bytes)
                }
                b.toByteArray()
            }
            // A
            rr(o, host, T_A, 0x8001, 120) { ip.address }
            return o.toByteArray()
        }

        private fun rr(o: ByteArrayOutputStream, n: String, type: Int, cls: Int, ttl: Int, data: () -> ByteArray) {
            name(o, n); u16(o, type); u16(o, cls)
            u16(o, ttl ushr 16); u16(o, ttl and 0xffff)
            val d = data()
            u16(o, d.size); o.write(d)
        }

        private fun nameBytes(n: String): ByteArray = ByteArrayOutputStream().also { name(it, n) }.toByteArray()

        private fun name(o: ByteArrayOutputStream, n: String) {
            for (label in n.trimEnd('.').split('.')) {
                val b = label.toByteArray(Charsets.UTF_8)
                o.write(b.size); o.write(b)
            }
            o.write(0)
        }

        private fun u16(o: ByteArrayOutputStream, v: Int) { o.write((v ushr 8) and 0xff); o.write(v and 0xff) }

        fun summarize(d: ByteArray, len: Int): String {
            fun r16(i: Int) = ((d[i].toInt() and 0xff) shl 8) or (d[i + 1].toInt() and 0xff)
            val flags = r16(2)
            val qd = r16(4); val an = r16(6); val ns = r16(8); val ar = r16(10)
            var pos = 12
            val sb = StringBuilder(if (flags and 0x8000 != 0) "RESP" else "QUERY")
            repeat(qd) {
                val (n, p) = readName(d, pos, len); pos = p
                val t = r16(pos); pos += 4
                sb.append(" Q[").append(n).append(' ').append(typeName(t)).append(']')
            }
            repeat(an + ns + ar) {
                if (pos + 10 > len) return sb.toString()
                val (n, p) = readName(d, pos, len); pos = p
                val t = r16(pos); val rdlen = r16(pos + 8); pos += 10
                val rd = when (t) {
                    T_PTR -> readName(d, pos, len).first
                    T_SRV -> "port=${r16(pos + 4)} target=${readName(d, pos + 6, len).first}"
                    T_A -> if (rdlen == 4) (0 until 4).joinToString(".") { (d[pos + it].toInt() and 0xff).toString() } else "?"
                    T_TXT -> txt(d, pos, rdlen)
                    else -> "${rdlen}B"
                }
                pos += rdlen
                sb.append(" RR[").append(n).append(' ').append(typeName(t)).append(" → ").append(rd).append(']')
            }
            return sb.toString()
        }

        private fun txt(d: ByteArray, start: Int, len: Int): String {
            val parts = ArrayList<String>()
            var p = start
            while (p < start + len) {
                val l = d[p].toInt() and 0xff; p++
                if (l == 0 || p + l > start + len) break
                parts.add(String(d, p, l, Charsets.UTF_8)); p += l
            }
            return parts.joinToString(",")
        }

        private fun readName(d: ByteArray, start: Int, len: Int): Pair<String, Int> {
            val labels = ArrayList<String>()
            var pos = start
            var end = -1
            var hops = 0
            while (pos < len) {
                val l = d[pos].toInt() and 0xff
                when {
                    l == 0 -> { pos++; break }
                    l and 0xc0 == 0xc0 -> {
                        if (end < 0) end = pos + 2
                        pos = ((l and 0x3f) shl 8) or (d[pos + 1].toInt() and 0xff)
                        if (++hops > 16) break
                    }
                    else -> {
                        labels.add(String(d, pos + 1, minOf(l, len - pos - 1), Charsets.UTF_8))
                        pos += 1 + l
                    }
                }
            }
            return labels.joinToString(".") to (if (end >= 0) end else pos)
        }

        private fun typeName(t: Int) = when (t) {
            T_A -> "A"; T_PTR -> "PTR"; T_TXT -> "TXT"; T_SRV -> "SRV"; 28 -> "AAAA"; 47 -> "NSEC"; T_ANY -> "ANY"
            else -> "T$t"
        }
    }
}
