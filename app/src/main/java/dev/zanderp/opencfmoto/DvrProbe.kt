package dev.zanderp.opencfmoto

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TEST build (368G built-in cameras): READ-ONLY look at what the dash serves on its Wi-Fi.
 * Never writes, uploads, deletes, logs in or POSTs. Only: TCP connect, read any banner the
 * dash sends by itself, and for HTTP-looking ports a plain GET with headers + first bytes.
 * Everything goes to the normal log (Help → Share logs). No MACs, QR or secrets are logged.
 */
object DvrProbe {
    private fun log(s: String) = LogBus.log("[DVR] $s")

    /** 7686: HTTP JSON endpoint at 192.168.3.1 found (unused) in the official EU app.
     *  11021: Carbit firmware FTP — connect only, never log in. */
    private val PORTS = listOf(21, 23, 80, 443, 554, 1935, 7686, 8000, 8080, 8081, 8192, 8888, 9000,
        10920, 10921, 10922, 10930, 11021, 6666, 6667, 3333, 5000)
    private val HTTP_PORTS = setOf(80, 7686, 8000, 8080, 8081, 8192, 8888, 9000, 5000)
    private val NO_READ = setOf(11021, 10920, 10921, 10922, 10930) // don't disturb the projection plane

    /** Novatek-style dash-cam read-only queries (version, file list), only if port 80 answers HTTP. */
    private val NOVATEK_READ = listOf("/?custom=1&cmd=3012", "/?custom=1&cmd=3015")
    private val PATHS = listOf("/", "/DCIM/", "/sd/", "/tmp/", "/video/", "/record/")

    fun show(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("DVR probe (test)")
            .setMessage("Read-only check of what the dash serves on its Wi-Fi (no writes).\n\n" +
                "Use it while the phone is on the bike's Wi-Fi: projecting with RideScreen, or joined by the official Zontes app. " +
                "Takes about 1 minute. Then Help → Share logs.")
            .setPositiveButton("Start") { _, _ ->
                Toast.makeText(activity, "DVR probe running…", Toast.LENGTH_SHORT).show()
                Thread({ run(activity.applicationContext) }, "dvr-probe").start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    internal fun pickNetwork(ctx: Context): Network? {
        BikeWifi.currentNetwork?.let { log("using RideScreen's bike network"); return it }
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val wifi = cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        log(if (wifi != null) "using the phone's Wi-Fi network (not RideScreen's)" else "no Wi-Fi network: join the bike's Wi-Fi first")
        return wifi
    }

    private fun run(ctx: Context) {
        try {
            val net = pickNetwork(ctx) ?: return
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val lp = cm.getLinkProperties(net)
            val gw = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
            val me = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.toString()
            val hosts = linkedSetOf<String>().apply { gw?.let { add(it) }; add("192.168.3.1") }
            log("start · phone $me · gateway $gw · projecting=${BikeWifi.currentNetwork != null}")
            for (host in hosts) {
                val open = PORTS.filter { tcpOpen(net, host, it) }
                log("$host open ports: ${if (open.isEmpty()) "none" else open.joinToString()}")
                for (p in open) {
                    if (p in NO_READ) continue
                    if (p in HTTP_PORTS) {
                        for (path in PATHS) if (!httpGet(net, host, p, path)) break
                        if (p == 80) NOVATEK_READ.forEach { httpGet(net, host, p, it) }
                    } else banner(net, host, p)
                }
            }
            log("done")
        } catch (e: Exception) {
            log("probe failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun tcpOpen(net: Network, host: String, port: Int): Boolean = try {
        net.socketFactory.createSocket().use { it.connect(InetSocketAddress(host, port), 800); true }
    } catch (_: Exception) { false }

    /** Reads whatever the server sends by itself (FTP/telnet greetings). Sends nothing. */
    private fun banner(net: Network, host: String, port: Int) {
        try {
            net.socketFactory.createSocket().use { s: Socket ->
                s.connect(InetSocketAddress(host, port), 1500); s.soTimeout = 1500
                val buf = ByteArray(256)
                val n = try { s.getInputStream().read(buf) } catch (_: Exception) { -1 }
                log("$host:$port banner: ${if (n > 0) printable(buf, n) else "(silent)"}")
            }
        } catch (e: Exception) { log("$host:$port banner error: ${e.javaClass.simpleName}") }
    }

    /** Plain HTTP/1.0 GET. Returns false if the port doesn't speak HTTP (stop trying more paths). */
    private fun httpGet(net: Network, host: String, port: Int, path: String): Boolean {
        return try {
            net.socketFactory.createSocket().use { s ->
                s.connect(InetSocketAddress(host, port), 1500); s.soTimeout = 2500
                s.getOutputStream().apply {
                    write("GET $path HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray()); flush()
                }
                val buf = ByteArray(1536)
                var total = 0
                val ins = s.getInputStream()
                while (total < buf.size) {
                    val n = try { ins.read(buf, total, buf.size - total) } catch (_: Exception) { -1 }
                    if (n <= 0) break; total += n
                }
                val text = if (total > 0) printable(buf, total) else "(no answer)"
                log("$host:$port GET $path → $text")
                text.startsWith("HTTP/")
            }
        } catch (e: Exception) { log("$host:$port GET $path error: ${e.javaClass.simpleName}"); false }
    }

    private fun printable(b: ByteArray, n: Int): String =
        String(b, 0, n, Charsets.ISO_8859_1).map { if (it == '\n') '⏎' else if (it.code in 32..126) it else '·' }
            .joinToString("").replace("\r", "").take(900)
}
