// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Find the bike's EasyConn TCP endpoint after SoftAP / P2P join.
 *
 * Many SoftAP dashes (e.g. Morini `ML*`) do not accept the classic wake probe on `:10930`
 * even while Wi‑Fi is fine — they advertise `_EasyConn._tcp.` (or listen on a nearby port).
 * Prefer NSD, then a short TCP port scan.
 *
 * Caller should already have pinned the process to the bike [Network] (multicast lock + bind)
 * so NSD sees the SoftAP LAN.
 */
object EasyConnDiscovery {

    const val SERVICE_TYPE = "_EasyConn._tcp."
    private const val NSD_TIMEOUT_MS = 12_000L
    private const val SCAN_CONNECT_MS = 350
    private const val WIDE_SCAN_CONNECT_MS = 250
    private val SCAN_PORTS = (10915..10935).toList()

    /**
     * Broader TCP ports to probe when EasyConn + Yunmo both refuse (e.g. Kove / Thinkerride SoftAP).
     * Log-only — does not start a video link.
     */
    private val THINKER_SCAN_PORTS: List<Int> = buildList {
        addAll(listOf(80, 443, 554, 8080, 8888, 9000, 9999))
        addAll(5000..5010)
        addAll(7000..7010)
        addAll(8000..8210)
        addAll(10000..10010)
    }

    data class Endpoint(
        val host: Inet4Address,
        val port: Int,
        val packageName: String?,
        val source: String,
    )

    fun discoverNsd(context: Context, log: (String) -> Unit): Endpoint? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: run {
            log("[DISC] NsdManager unavailable")
            return null
        }
        val found = AtomicReference<Endpoint?>(null)
        val done = CountDownLatch(1)

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                log("[DISC] start failed: $errorCode")
                done.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String?) {
                log("[DISC] discovering $SERVICE_TYPE…")
            }
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (found.get() != null) return
                val name = serviceInfo.serviceName ?: "?"
                log("[DISC] found service name=$name — resolving")
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            log("[DISC] resolve failed for $name: $errorCode")
                        }
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val ep = endpointFrom(resolved) ?: return
                            // Never probe ourselves (our own advert comes back through NSD): same address as one of ours.
                            if (ep.host.hostAddress in P2pClientDiscovery.ownAddresses()) {
                                log("[DISC] ignored own advert ${ep.host.hostAddress}:${ep.port}")
                                return
                            }
                            if (found.compareAndSet(null, ep)) {
                                log(
                                    "[DISC] NSD → ${ep.host.hostAddress}:${ep.port}" +
                                        (ep.packageName?.let { " pkg=$it" } ?: ""),
                                )
                                done.countDown()
                            }
                        }
                    })
                } catch (e: Exception) {
                    log("[DISC] resolveService threw: $e")
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }

        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (e: Exception) {
            log("[DISC] discoverServices failed: $e")
            return null
        }
        try {
            done.await(NSD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        try { nsd.stopServiceDiscovery(discovery) } catch (_: Exception) {}
        if (found.get() == null) log("[DISC] NSD timed out / no usable EasyConn advertisement")
        return found.get()
    }

    private fun endpointFrom(info: NsdServiceInfo): Endpoint? {
        val port = info.port
        if (port <= 0 || port > 65535) return null
        val host = pickIpv4(info) ?: return null
        if (!isUsableBikeIpv4(host)) return null
        val pkg = readTxt(info, "packagename")
            ?: readTxt(info, "packageName")
            ?: readTxt(info, "package")
        return Endpoint(host, port, pkg, "nsd")
    }

    private fun pickIpv4(info: NsdServiceInfo): Inet4Address? {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                info.hostAddresses?.firstOrNull { it is Inet4Address }?.let {
                    return it as Inet4Address
                }
            } catch (_: Exception) {
            }
        }
        @Suppress("DEPRECATION")
        return info.host as? Inet4Address
    }

    private fun readTxt(info: NsdServiceInfo, key: String): String? {
        val attrs = info.attributes ?: return null
        val raw = attrs[key] ?: attrs.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            ?: return null
        return try {
            String(raw, Charsets.UTF_8).trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun isUsableBikeIpv4(addr: Inet4Address): Boolean {
        if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isMulticastAddress) return false
        return addr.address?.size == 4
    }

    /**
     * TCP connect-scan nearby EasyConn-ish ports on [bikeIp].
     * Prefer [EasyConnProber.BIKE_PROBE_PORT] first when open.
     */
    fun scanOpenPorts(
        bikeIp: Inet4Address,
        network: Network?,
        myIp: Inet4Address,
        openSocket: (Network?, Inet4Address) -> Socket,
        log: (String) -> Unit,
    ): List<Int> {
        log("[DISC] port-scanning ${bikeIp.hostAddress} ${SCAN_PORTS.first()}–${SCAN_PORTS.last()}…")
        val open = scanPorts(bikeIp, network, myIp, openSocket, SCAN_PORTS, SCAN_CONNECT_MS)
        log(
            if (open.isEmpty()) "[DISC] port-scan: none open"
            else "[DISC] port-scan open: $open",
        )
        return open
    }

    /**
     * Log-only wider TCP scan after EasyConn/Yunmo fail — helps reverse Thinkerride / Kove SoftAP.
     * Tag: `[DISC] thinkerride-scan …`
     */
    fun scanThinkerridePorts(
        bikeIp: Inet4Address,
        network: Network?,
        myIp: Inet4Address,
        openSocket: (Network?, Inet4Address) -> Socket,
        log: (String) -> Unit,
    ): List<Int> {
        log(
            "[DISC] thinkerride-scan ${bikeIp.hostAddress} " +
                "(${THINKER_SCAN_PORTS.size} ports, log-only)…",
        )
        val open = scanPorts(bikeIp, network, myIp, openSocket, THINKER_SCAN_PORTS, WIDE_SCAN_CONNECT_MS)
        log(
            if (open.isEmpty()) "[DISC] thinkerride-scan: none open"
            else "[DISC] thinkerride-scan open: $open",
        )
        return open
    }

    private fun scanPorts(
        bikeIp: Inet4Address,
        network: Network?,
        myIp: Inet4Address,
        openSocket: (Network?, Inet4Address) -> Socket,
        ports: List<Int>,
        connectMs: Int,
    ): List<Int> {
        val open = ArrayList<Int>()
        for (port in ports) {
            try {
                val s = openSocket(network, myIp)
                try {
                    s.connect(InetSocketAddress(bikeIp, port), connectMs)
                    open.add(port)
                } finally {
                    try { s.close() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            }
        }
        return open
    }
}
