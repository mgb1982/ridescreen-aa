// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * v2.4 test · Zontes 350E (QR `action=8`, Wi‑Fi Direct, this phone as a client of the dash's group):
 * find the dash's EasyConn endpoint the way the official app does, from the moment the group forms.
 *
 * What the official app does (rea-lab `20261101-350e-p2p-como-la-oficial`, static analysis only):
 *  - mDNS over IPv4 on the group's own interface (JmDNS bound to it), asking for `_EasyConn._tcp.local.`;
 *    it then connects to the PORT the dash announces and reads `packagename` from the TXT record;
 *  - in parallel, :10930 on the group owner at 400 ms and then every second, up to 10 times;
 *  - the QR's `ip=` (when present) is probed too.
 * The system `NsdManager` never heard the 350E on Wi‑Fi Direct (every log says "NSD timed out").
 *
 * This object only DISCOVERS. When something answers it hands the endpoint to [EasyConnProber.offerEndpoint],
 * which sends MDNS_RESPOND as soon as the phone's listening ports are up. If nothing shows up in 60 s it
 * TCP-scans 1–65535 on the dash so the log shows where the dash really listens.
 * Nothing here touches other bikes: it is started only for `action=8` after a group where we are a client.
 */
object P2pClientDiscovery {
    private const val TAG = "[DISC]"
    private const val MDNS_GROUP = "224.0.0.251"
    private const val MDNS_PORT = 5353
    const val SERVICE = "_EasyConn._tcp.local"
    private const val MDNS_WINDOW_MS = 60_000L
    private const val QUERY_EVERY_MS = 1_500L
    private const val PROBE_PORT = EasyConnProber.BIKE_PROBE_PORT
    private const val MAX_ANSWER_LOGS = 40

    class Endpoint(val host: Inet4Address, val port: Int, val packageName: String?, val source: String)

    private class Session(val id: Int) { @Volatile var stopped = false }

    private val ids = AtomicInteger(0)
    @Volatile private var current: Session? = null
    /** An advert or an open :10930 was found in the current session (stops the full scan). */
    @Volatile private var foundSomething = false

    /** True while a session is running for the current Wi‑Fi Direct group. */
    val isActive: Boolean get() = current?.stopped == false

    fun stop(why: String) {
        val s = current ?: return
        if (!s.stopped) {
            s.stopped = true
            LogBus.log("$TAG early discovery stopped ($why)")
        }
    }

    /**
     * @param bindIp the phone's address in the group, @param goIp the dash (group owner),
     * @param qrIp the `ip=` of the QR or null, @param onEndpoint where to send what was found.
     */
    fun start(
        context: Context,
        bindIp: Inet4Address,
        goIp: Inet4Address,
        qrIp: String?,
        onEndpoint: (Endpoint) -> Unit,
    ) {
        stop("new group")
        val s = Session(ids.incrementAndGet())
        current = s
        foundSomething = false
        val app = context.applicationContext
        val log = { m: String -> LogBus.log(m) }

        val qrAddr = parseIpv4(qrIp)
        if (qrIp != null) {
            if (qrAddr != null) log("$TAG target address comes from the QR (ip=${qrAddr.hostAddress}); the group owner is ${goIp.hostAddress}")
            else log("$TAG QR carries an ip= that is not a usable IPv4 — ignored")
        }
        val target = qrAddr ?: goIp
        val own = ownAddresses()
        val offered = HashSet<String>()
        val offer = { ep: Endpoint ->
            val key = "${ep.host.hostAddress}:${ep.port}:${ep.packageName}"
            val first = synchronized(offered) { offered.add(key) }
            if (first && !s.stopped) {
                foundSomething = true
                try { onEndpoint(ep) } catch (e: Exception) { log("$TAG offer failed: $e") }
            }
        }

        log("$TAG early discovery on the Wi‑Fi Direct group: phone=${bindIp.hostAddress} dash=${goIp.hostAddress} " +
            "(mDNS IPv4 for ${MDNS_WINDOW_MS / 1000}s, :$PROBE_PORT ×10) — not waiting for Android Auto")

        thread(name = "p2p-mdns4", isDaemon = true) {
            try { mdnsLoop(app, s, bindIp, own, offer, log) } catch (e: Exception) { log("[MDNS4] stopped: $e") }
        }
        thread(name = "p2p-10930", isDaemon = true) {
            try { portLoop(s, bindIp, goIp, target, offer, log) } catch (e: Exception) { log("$TAG :$PROBE_PORT loop stopped: $e") }
        }
        thread(name = "p2p-fullscan", isDaemon = true) {
            try {
                var waited = 0L
                while (waited < MDNS_WINDOW_MS && !s.stopped && !foundSomething) { Thread.sleep(500); waited += 500 }
                if (s.stopped || foundSomething) return@thread
                log("$TAG nothing announced and :$PROBE_PORT closed after ${MDNS_WINDOW_MS / 1000}s — scanning every TCP port on ${target.hostAddress}")
                fullScan(s, bindIp, target, log)
            } catch (e: Exception) { log("$TAG fullscan stopped: $e") }
        }
    }

    // ── mDNS over IPv4 on the group's interface

    private fun mdnsLoop(
        app: Context, s: Session, bindIp: Inet4Address, own: Set<String>,
        offer: (Endpoint) -> Unit, log: (String) -> Unit,
    ) {
        val wm = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = try { wm?.createMulticastLock("ridescreen-p2p-mdns")?.apply { setReferenceCounted(false); acquire() } } catch (_: Exception) { null }
        var sock: MulticastSocket? = null
        try {
            val nif = NetworkInterface.getByInetAddress(bindIp)
            sock = try {
                MulticastSocket(null as java.net.SocketAddress?).apply { reuseAddress = true; bind(InetSocketAddress(MDNS_PORT)) }
            } catch (e: Exception) {
                log("[MDNS4] could not bind :$MDNS_PORT (${e.message}) — using a free port; answers come back unicast")
                MulticastSocket(0)
            }
            sock.timeToLive = 255
            sock.soTimeout = 400
            if (nif != null) {
                try { sock.networkInterface = nif } catch (e: Exception) { log("[MDNS4] setNetworkInterface: ${e.message}") }
                try { sock.joinGroup(InetSocketAddress(InetAddress.getByName(MDNS_GROUP), MDNS_PORT), nif) }
                catch (e: Exception) { log("[MDNS4] joinGroup: ${e.message}") }
            } else {
                log("[MDNS4] no network interface for ${bindIp.hostAddress} — sending without one")
            }
            log("[MDNS4] asking $SERVICE on ${nif?.name ?: "?"} (${bindIp.hostAddress}) every ${QUERY_EVERY_MS} ms, local port ${sock.localPort}")
            val query = MdnsWire.buildQuery(SERVICE)
            val group = InetAddress.getByName(MDNS_GROUP)
            val end = System.currentTimeMillis() + MDNS_WINDOW_MS
            var nextQuery = 0L
            var sent = 0; var packets = 0; var others = 0; var logged = 0
            val buf = ByteArray(2048)
            while (!s.stopped && System.currentTimeMillis() < end) {
                val now = System.currentTimeMillis()
                if (now >= nextQuery) {
                    try { sock.send(DatagramPacket(query, query.size, group, MDNS_PORT)); sent++ }
                    catch (e: Exception) { if (sent == 0) log("[MDNS4] send failed: ${e.message}") }
                    nextQuery = now + QUERY_EVERY_MS
                }
                val pkt = DatagramPacket(buf, buf.size)
                try { sock.receive(pkt) } catch (_: java.net.SocketTimeoutException) { continue }
                packets++
                val src = (pkt.address as? Inet4Address) ?: continue
                if (src.hostAddress in own) continue
                others++
                for (svc in MdnsWire.parse(pkt.data, pkt.length)) {
                    val host = svc.addresses.firstOrNull { it.hostAddress !in own } ?: src
                    if (host.hostAddress in own) {
                        log("$TAG ignored own advert ${host.hostAddress}:${svc.port}")
                        continue
                    }
                    if (logged < MAX_ANSWER_LOGS) {
                        logged++
                        log("[MDNS4] answer from ${src.hostAddress} port=${svc.port} packagename=${svc.txt["packagename"] ?: "-"} " +
                            "channel=${svc.txt["channel"] ?: "-"} flavor=${svc.txt["flavor"] ?: "-"} huname=${svc.txt["huname"] ?: "-"}" +
                            (if (host != src) " host=${host.hostAddress}" else ""))
                    }
                    if (svc.port in 1..65535) {
                        offer(Endpoint(host, svc.port, MdnsWire.safePackage(svc.txt["packagename"]), "mdns4"))
                    }
                }
            }
            log("[MDNS4] stopped: sent $sent queries, $packets packets, $others from others")
        } finally {
            try { sock?.close() } catch (_: Exception) {}
            try { lock?.release() } catch (_: Exception) {}
        }
    }

    // ── :10930 on the dash, like the official app (400 ms, then every second, up to 10 times)

    private fun portLoop(
        s: Session, bindIp: Inet4Address, goIp: Inet4Address, target: Inet4Address,
        offer: (Endpoint) -> Unit, log: (String) -> Unit,
    ) {
        Thread.sleep(400)
        for (n in 1..10) {
            if (s.stopped) return
            // The QR address first; when it differs from the group owner, alternate so a wrong ip= cannot hide the dash.
            val host = if (target == goIp || n % 2 == 1) target else goIp
            val r = tryConnect(bindIp, host, PROBE_PORT, 800)
            log("$TAG :$PROBE_PORT on ${host.hostAddress} #$n/10 → ${r.first}")
            if (r.first == "open") {
                offer(Endpoint(host, PROBE_PORT, null, "tcp$PROBE_PORT"))
                return
            }
            Thread.sleep(1000)
        }
    }

    /** ("open"|"refused"|"timeout"|"error: …", banner) — bound to the phone's group address. */
    private fun tryConnect(bindIp: Inet4Address, host: Inet4Address, port: Int, timeoutMs: Int, banner: Boolean = false): Pair<String, String> {
        val sock = Socket()
        try {
            try { sock.bind(InetSocketAddress(bindIp, 0)) } catch (_: Exception) {}
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            var text = ""
            if (banner) {
                try {
                    sock.soTimeout = 250
                    val b = ByteArray(48)
                    val n = sock.getInputStream().read(b)
                    if (n > 0) text = MdnsWire.printable(b, n)
                } catch (_: Exception) {}
            }
            return "open" to text
        } catch (e: java.net.SocketTimeoutException) {
            return "timeout" to ""
        } catch (e: java.net.ConnectException) {
            val m = (e.message ?: "").lowercase()
            return (if (m.contains("refused")) "refused" else if (m.contains("timed out")) "timeout" else "error: ${e.message}") to ""
        } catch (e: Exception) {
            return "error: ${e.javaClass.simpleName}" to ""
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    // ── full TCP scan 1–65535

    private fun fullScan(s: Session, bindIp: Inet4Address, host: Inet4Address, log: (String) -> Unit) {
        val t0 = System.currentTimeMillis()
        val open = java.util.Collections.synchronizedList(ArrayList<String>())
        val refused = AtomicInteger(0)
        val timeout = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(96) { r -> Thread(r, "p2p-scan").apply { isDaemon = true } }
        for (port in 1..65535) {
            pool.execute {
                if (s.stopped) return@execute
                val (res, banner) = tryConnect(bindIp, host, port, 400, banner = true)
                when {
                    res == "open" -> {
                        val entry = "$port(${banner.ifEmpty { "-" }})"
                        open.add(entry)
                        log("$TAG fullscan open :$port banner=${banner.ifEmpty { "-" }}")
                    }
                    res == "refused" -> refused.incrementAndGet()
                    else -> timeout.incrementAndGet()
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(240, TimeUnit.SECONDS)
        pool.shutdownNow()
        val secs = (System.currentTimeMillis() - t0) / 1000
        log("$TAG fullscan done in ${secs}s ${host.hostAddress} open=[${synchronized(open) { open.sorted().joinToString(", ") }}] " +
            "refused=${refused.get()} timeout=${timeout.get()}${if (s.stopped) " (stopped early)" else ""}")
    }

    // ── helpers

    /** Every address of this phone (any interface), without scope ids. */
    fun ownAddresses(): Set<String> {
        val out = HashSet<String>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                for (a in nif.inetAddresses) out.add(a.hostAddress.substringBefore('%'))
            }
        } catch (_: Exception) {}
        return out
    }

    internal fun parseIpv4(s: String?): Inet4Address? {
        if (s.isNullOrBlank()) return null
        val m = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(s.trim()) ?: return null
        val b = m.groupValues.drop(1).map { it.toInt() }
        if (b.any { it > 255 } || b[0] == 0 || b[0] == 127 || b[0] >= 224) return null
        return try { InetAddress.getByAddress(ByteArray(4) { b[it].toByte() }) as? Inet4Address } catch (_: Exception) { null }
    }
}

/** Just enough DNS (RFC 1035 / mDNS) to ask for a service and read PTR / SRV / TXT / A answers. */
internal object MdnsWire {
    class Service(val instance: String, val port: Int, val txt: Map<String, String>, val addresses: List<Inet4Address>)

    /** A PTR query for [name] with the unicast-response bit set, so the dash may answer straight to us. */
    fun buildQuery(name: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0)) // id 0, flags 0, QDCOUNT 1
        for (label in name.trim('.').split('.')) {
            val b = label.toByteArray(Charsets.UTF_8)
            out.write(b.size); out.write(b)
        }
        out.write(0)
        out.write(byteArrayOf(0, 12, 0x80.toByte(), 1)) // PTR, IN | unicast-response
        return out.toByteArray()
    }

    /** Services of type `_easyconn._tcp.local` found in a DNS message; empty on anything malformed. */
    fun parse(buf: ByteArray, len: Int): List<Service> = try { parseOrThrow(buf, len) } catch (_: Exception) { emptyList() }

    private fun u16(b: ByteArray, p: Int) = ((b[p].toInt() and 0xFF) shl 8) or (b[p + 1].toInt() and 0xFF)

    /** Reads a (possibly compressed) name at [start]; returns it and the offset just past it in the original position. */
    private fun readName(b: ByteArray, len: Int, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var p = start
        var end = -1
        var jumps = 0
        while (true) {
            if (p >= len) throw IllegalArgumentException("name past end")
            val l = b[p].toInt() and 0xFF
            when {
                l == 0 -> { if (end < 0) end = p + 1; break }
                (l and 0xC0) == 0xC0 -> {
                    if (p + 1 >= len) throw IllegalArgumentException("bad pointer")
                    if (end < 0) end = p + 2
                    if (++jumps > 20) throw IllegalArgumentException("pointer loop")
                    p = ((l and 0x3F) shl 8) or (b[p + 1].toInt() and 0xFF)
                }
                l > 63 -> throw IllegalArgumentException("bad label")
                else -> {
                    if (p + 1 + l > len) throw IllegalArgumentException("label past end")
                    if (sb.isNotEmpty()) sb.append('.')
                    sb.append(String(b, p + 1, l, Charsets.UTF_8))
                    p += 1 + l
                }
            }
        }
        return sb.toString() to end
    }

    private fun parseOrThrow(b: ByteArray, len: Int): List<Service> {
        if (len < 12) return emptyList()
        val qd = u16(b, 4)
        val total = u16(b, 6) + u16(b, 8) + u16(b, 10)
        var p = 12
        repeat(qd) { p = readName(b, len, p).second + 4 }
        val ptrTargets = LinkedHashSet<String>()
        val srv = HashMap<String, Pair<Int, String>>()
        val txt = HashMap<String, Map<String, String>>()
        val addrs = HashMap<String, MutableList<Inet4Address>>()
        repeat(total) {
            val (name, after) = readName(b, len, p)
            if (after + 10 > len) throw IllegalArgumentException("record past end")
            val type = u16(b, after)
            val rdLen = u16(b, after + 8)
            val rd = after + 10
            if (rd + rdLen > len) throw IllegalArgumentException("rdata past end")
            val key = name.lowercase()
            when (type) {
                12 -> ptrTargets.add(readName(b, len, rd).first.lowercase())
                33 -> if (rdLen >= 7) srv[key] = u16(b, rd + 4) to readName(b, len, rd + 6).first.lowercase()
                16 -> txt[key] = readTxt(b, rd, rdLen)
                1 -> if (rdLen == 4) {
                    (InetAddress.getByAddress(b.copyOfRange(rd, rd + 4)) as? Inet4Address)?.let { addrs.getOrPut(key) { ArrayList() }.add(it) }
                }
            }
            p = rd + rdLen
        }
        val suffix = ("._easyconn._tcp.local")
        val instances = LinkedHashSet<String>()
        instances.addAll(ptrTargets.filter { it.lowercase().endsWith(suffix) })
        instances.addAll(srv.keys.filter { it.endsWith(suffix) })
        return instances.mapNotNull { inst ->
            val s = srv[inst.lowercase()] ?: return@mapNotNull null
            val ips = addrs[s.second] ?: addrs.values.flatten().toMutableList()
            Service(inst, s.first, txt[inst.lowercase()] ?: emptyMap(), ips)
        }
    }

    private fun readTxt(b: ByteArray, start: Int, rdLen: Int): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var p = start
        val end = start + rdLen
        while (p < end) {
            val l = b[p].toInt() and 0xFF
            if (p + 1 + l > end) break
            val kv = String(b, p + 1, l, Charsets.UTF_8)
            val eq = kv.indexOf('=')
            if (eq > 0) out[kv.substring(0, eq).lowercase()] = kv.substring(eq + 1)
            p += 1 + l
        }
        return out
    }

    /** The advertised package, only if it looks like a package name (it goes into the greeting's JSON). */
    fun safePackage(raw: String?): String? =
        raw?.trim()?.takeIf { Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$").matches(it) && it.length <= 120 }

    fun printable(b: ByteArray, n: Int): String {
        val sb = StringBuilder()
        for (i in 0 until n) {
            val c = b[i].toInt() and 0xFF
            sb.append(if (c in 32..126) c.toChar() else '.')
        }
        return sb.toString().trim()
    }
}
