// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Zontes T350X (QR action=8 with ssid + password, 20 Oct 2026). The dash is a Wi‑Fi Direct group
 * owner whose group ("ZT5G0082", 5805 MHz) a phone can join like a normal Wi‑Fi network — but that
 * network almost never shows up in the phone's Wi‑Fi scans. The one time the join worked (3 s) it
 * came within seconds of 40 s of Wi‑Fi Direct discovery; every failed join came ≥30 s after
 * discovery had stopped. The likely reason: discovery probes on the dash's channel and its answer
 * lands in the radio's shared scan cache, which the normal join reads (entries expire in ~30 s).
 *
 * So while the normal join is pending, keep Wi‑Fi Direct discovery running (no connect), and log
 * what is seen: the dash as a peer (with its WPS methods) and whether its network is in the cache.
 */
object P2pWarmUp {
    private const val TAG = "[WARMUP]"
    private const val EVERY_MS = 8_000L
    private const val MAX_MS = 180_000L

    private val h = Handler(Looper.getMainLooper())
    private var mgr: WifiP2pManager? = null
    private var chan: WifiP2pManager.Channel? = null
    private var rx: BroadcastReceiver? = null
    private var appCtx: Context? = null
    @Volatile private var running = false
    private var until = 0L
    private var ssid = ""
    private var mac: String? = null
    private var peerLogged = false
    private var lastCache: String? = null
    private var log: (String) -> Unit = {}

    /** Idempotent: a second call while running only extends the window. */
    @SuppressLint("MissingPermission")
    fun start(context: Context, qr: QrData, log: (String) -> Unit) {
        val ctx = context.applicationContext
        until = SystemClock.elapsedRealtime() + MAX_MS
        if (running) return
        if (!BikeWifiP2p.hasPermission(ctx)) {
            log("$TAG skipped — no Wi‑Fi Direct permission")
            return
        }
        val m = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager ?: return
        val c = try { m.initialize(ctx, Looper.getMainLooper(), null) } catch (e: Exception) { log("$TAG init failed: $e"); return } ?: return
        appCtx = ctx; mgr = m; chan = c; this.log = log
        ssid = qr.ssid; mac = qr.mac?.lowercase()
        peerLogged = false; lastCache = null
        running = true
        val r = object : BroadcastReceiver() {
            override fun onReceive(cx: Context, intent: Intent) {
                if (!running || intent.action != WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) return
                try { m.requestPeers(c) { peers -> peers?.deviceList?.forEach { logPeer(it) } } } catch (_: Exception) {}
            }
        }
        rx = r
        try {
            val f = IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            registerPeers(ctx, r, f)
        } catch (_: Exception) {}
        log("$TAG Wi‑Fi Direct discovery alongside the Wi‑Fi join of '$ssid' (helps the phone see the dash's network)")
        h.post(tick)
    }

    fun stop(why: String) {
        if (!running) return
        running = false
        h.removeCallbacks(tick)
        try { chan?.let { mgr?.stopPeerDiscovery(it, null) } } catch (_: Exception) {}
        try { rx?.let { appCtx?.unregisterReceiver(it) } } catch (_: Exception) {}
        try { if (android.os.Build.VERSION.SDK_INT >= 27) chan?.close() } catch (_: Exception) {}
        rx = null; chan = null; mgr = null
        log("$TAG stopped ($why)")
    }

    private val tick = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            if (!running) return
            if (BikeWifi.currentNetwork != null) { stop("bike Wi‑Fi joined"); return }
            if (SystemClock.elapsedRealtime() > until) { stop("time limit"); return }
            val m = mgr; val c = chan
            if (m != null && c != null) {
                try {
                    m.discoverPeers(c, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {}
                        override fun onFailure(reason: Int) { log("$TAG discoverPeers failed ($reason)") }
                    })
                } catch (_: Exception) {}
            }
            logCache()
            h.postDelayed(this, EVERY_MS)
        }
    }

    private fun logPeer(d: WifiP2pDevice) {
        if (peerLogged) return
        val addr = d.deviceAddress?.lowercase()
        if (addr != mac && !d.deviceName.equals(ssid, ignoreCase = true)) return
        peerLogged = true
        log("$TAG dash seen over Wi‑Fi Direct: '${d.deviceName}' ${d.deviceAddress} isGO=${d.isGroupOwner} wps=${wpsMethods(d)}")
    }

    private fun logCache() {
        val ctx = appCtx ?: return
        val hit = matchingScanResults(ctx, ssid, mac).firstOrNull()
        val now = hit?.let { "in the scan cache: ${it.frequency} MHz, ${it.level} dBm, ${it.capabilities}" } ?: "not in the scan cache"
        if (now != lastCache) { lastCache = now; log("$TAG '$ssid' $now") }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerPeers(ctx: Context, r: BroadcastReceiver, f: IntentFilter) {
        if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        else ctx.registerReceiver(r, f)
    }

    internal fun wpsMethods(d: WifiP2pDevice): String =
        listOfNotNull(
            "pbc".takeIf { d.wpsPbcSupported() },
            "keypad".takeIf { d.wpsKeypadSupported() },
            "display".takeIf { d.wpsDisplaySupported() },
        ).joinToString("+").ifEmpty { "none" }

    /**
     * Scan-cache entries that look like the dash's own network: same SSID, or a BSSID that matches
     * the QR's Wi‑Fi Direct address apart from the first octet (group interfaces usually differ only
     * there). Never lists other networks.
     */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun matchingScanResults(ctx: Context, ssid: String, mac: String?): List<ScanResult> {
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()
        val all = try { wm.scanResults } catch (_: Exception) { null } ?: return emptyList()
        val tail = mac?.lowercase()?.takeIf { it.length == 17 }?.substring(3)
        return all.filter { r ->
            (ssid.isNotBlank() && r.SSID.equals(ssid, ignoreCase = true)) ||
                (tail != null && r.BSSID?.lowercase()?.endsWith(tail) == true)
        }.sortedByDescending { it.level }
    }
}
