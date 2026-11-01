package dev.zanderp.opencfmoto

// SPDX-License-Identifier: AGPL-3.0-or-later
// Adapted from eugen0309/open-cfmoto.

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Joins the bike over **Wi-Fi Direct (Wi-Fi P2P)** instead of an infrastructure AP.
 *
 * Some CFMoto dashboards (e.g. CL-C450) advertise **P2P** in the QR `action` bitmask (bit3)
 * and run the EasyConn head unit as a Wi-Fi Direct **Group Owner (GO)** rather than a normal
 * WPA2 access point. [BikeWifi] (which uses `WifiNetworkSpecifier`) cannot associate to a GO,
 * so those bikes need this path.
 *
 * Topology once the group forms (standard Android Wi-Fi Direct):
 *   - The bike is the **GO** at the fixed address **192.168.49.1** (this is our gateway/bike IP).
 *   - The phone is a **client** at **192.168.49.x** on a `p2p-*` interface.
 *   - We do NOT `bindProcessToNetwork` (there is no `ConnectivityManager.Network` for a P2P
 *     group the way there is for a specifier join). Instead the caller binds its sockets to the
 *     phone's P2P interface IP, which we hand back via [onConnected]. The 192.168.49.0/24 subnet
 *     is on-link, so binding the source IP is enough to route over the P2P interface. A bonus is
 *     that Android Auto / GMS keep using cellular for map tiles — no VPN, no route capture.
 *
 * âš ï¸ This helper logs verbosely (raw peers, group info, addresses) so a single
 * bike session reveals what actually happens and what — if anything — needs correcting. Follow
 * the project's "implement â†’ one logged bike session â†’ adjust" loop (see docs/00 and docs/01 Â§7).
 */
object BikeWifiP2p {

    private const val TAG = "[P2P]"
    /** Default window for non-DIRECT-* MAC joins (Zontes / crowded peer lists). Callers can pass a
     *  shorter [connect] timeout when a SoftAP fallback exists (see [MainActivity]). */
    const val CONNECT_TIMEOUT_MS = 40_000L
    private const val IP_POLL_TIMEOUT_MS = 10_000L
    private const val IP_POLL_INTERVAL_MS = 500L

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private const val RETRY_EVERY_MS = 4_000L
    /** How long an accepted connect() may sit without a group before we invite again. */
    private const val ACCEPTED_WAIT_MS = 12_000L
    @Volatile private var active = false
    @Volatile private var connected = false
    @Volatile private var connectIssued = false
    @Volatile private var timeoutThread: Thread? = null

    // 350E / T350X field logs (Alberto, Inky, 22 Oct 2026): every connect() came back ERROR, 78/78 and
    // 51/51, on a OnePlus 8T (Android 14) and a Samsung A56 (Android 16). Each one was issued right after
    // stopPeerDiscovery(), with the phone's peer list empty ("peers: (none yet)"). Android refuses to
    // connect to a peer it does not currently list, and answers with that bare ERROR. So: connect only
    // while the dash IS in the latest peer list, keep discovery running, and only every third failure
    // fall back to the old stop-discovery-first order (Pixel needed it once).
    /** Normalised MACs in the latest requestPeers() answer. */
    @Volatile private var listedPeers: Set<String> = emptySet()
    @Volatile private var startedAt = 0L
    @Volatile private var peersSeenAt = 0L
    @Volatile private var lastConnectAt = 0L
    @Volatile private var macFailures = 0
    @Volatile private var waitingLogged = false
    @Volatile private var acceptedAt = 0L
    private const val ACCEPT_GRACE_MS = 25_000L
    private const val MIN_CONNECT_GAP_MS = 2_500L
    /** No peer list at all after this long (discovery broken?): allow one blind attempt. */
    private const val BLIND_AFTER_MS = 8_000L

    /** The listed address that matches [mac] (exact, or the ±1 neighbour some dashes use), else null. */
    private fun listedAs(mac: String): String? =
        listedPeers.firstOrNull { it.equals(mac, ignoreCase = true) } ?: listedPeers.firstOrNull { macMatches(mac, it) }

    /** True when the formed group has the dash as owner and this phone as a plain client (set before onConnected). */
    @Volatile var clientOfGroup = false
        private set

    /** True only while Android reports an active Wi-Fi Direct group. */
    val isConnected: Boolean get() = active && connected

    /**
     * @param onConnected called with (phoneBindIp, bikeGatewayIp) once the group is formed and
     *   both addresses are known. Pass these straight to [EasyConnProber.start].
     */
    @SuppressLint("MissingPermission")
    fun connect(
        context: Context,
        qr: QrData,
        onConnected: (bindIp: Inet4Address, gatewayIp: Inet4Address) -> Unit,
        onFailed: (reason: String) -> Unit,
        log: (String) -> Unit,
        // How long to wait for a P2P group before failing over. When the QR also advertises a SoftAP
        // (a proven fallback), the caller passes a short value so a phone that can't form a P2P group
        // fails over in seconds instead of burning the full window. Default keeps prior behaviour.
        timeoutMs: Long = CONNECT_TIMEOUT_MS,
    ) {
        stop(log) // clean any prior attempt
        active = true
        connected = false
        connectIssued = false
        acceptedRetries = 0
        listedPeers = emptySet(); peersSeenAt = 0L; acceptedAt = 0L; lastConnectAt = 0L; macFailures = 0; waitingLogged = false
        startedAt = android.os.SystemClock.elapsedRealtime()
        val ctx = context.applicationContext
        appContext = ctx

        val mgr = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (mgr == null) { onFailed("device has no Wi-Fi P2P service"); return }
        manager = mgr
        val chan = mgr.initialize(ctx, Looper.getMainLooper(), null)
        channel = chan

        log("$TAG starting Wi-Fi Direct connect")
        log("$TAG   qr ssid='${qr.ssid}' mac=${qr.mac} action=${qr.action} (p2p=${qr.supportsP2p} ap=${qr.supportsAp})")
        log("$TAG   expecting bike GO at 192.168.49.1; phone will be a P2P client")

        registerReceiver(ctx, mgr, chan, qr, onConnected, onFailed, log)

        // Peer discovery is required on many phones before connect(), and surfaces the bike so we
        // can join by MAC when the QR SSID is not a DIRECT-* group name (Zontes / Carbit action=8).
        mgr.discoverPeers(chan, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { log("$TAG discoverPeers: started") }
            override fun onFailure(reason: Int) {
                log("$TAG discoverPeers: failed (${reasonStr(reason)}) — continuing with MAC/credential join")
                // Still try MAC immediately; some stacks accept connect() without a prior discovery.
                attemptMacJoin(mgr, chan, qr, log)
            }
        })

        // Prefer credential join when the SSID is a real P2P group name; otherwise go straight to MAC.
        if (qr.ssid.startsWith("DIRECT-", ignoreCase = true)) {
            attemptCredentialJoin(mgr, chan, qr, log)
        } else {
            log("$TAG SSID '${qr.ssid}' is not DIRECT-* — joining by QR MAC / discovered peer")
            attemptMacJoin(mgr, chan, qr, log)
        }

        startTimeout(onFailed, log, timeoutMs)
        startRetries(ctx, mgr, chan, qr, log)
    }

    /**
     * Some phones (MediaTek / Xiaomi, Zontes 350D field log 20 Oct 2026) reject the first
     * discoverPeers() and connect() with a bare ERROR and never surface the dash afterwards. Keep
     * re-trying every few seconds until the group forms or the timeout fails the join, and say once
     * what the phone's Location and Wi‑Fi switches are (both are usual causes of that ERROR).
     */
    @SuppressLint("MissingPermission")
    private fun startRetries(
        ctx: Context,
        mgr: WifiP2pManager,
        chan: WifiP2pManager.Channel,
        qr: QrData,
        log: (String) -> Unit,
    ) {
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val loc = lm?.let { androidx.core.location.LocationManagerCompat.isLocationEnabled(it) }
            log("$TAG phone state: location=${loc ?: "?"} wifi=${wm?.isWifiEnabled ?: "?"} permission=${if (hasPermission(ctx)) "ok" else "MISSING ${permissionName()}"}")
        } catch (_: Exception) {}
        thread(name = "p2p-retry", isDaemon = true) {
            var n = 0
            while (active && !connected && n < 8) {
                try { Thread.sleep(RETRY_EVERY_MS) } catch (_: InterruptedException) { return@thread }
                if (!active || connected || connectIssued) continue
                n++
                android.os.Handler(Looper.getMainLooper()).post {
                    if (!active || connected || connectIssued) return@post
                    mgr.discoverPeers(chan, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() { log("$TAG retry $n: discoverPeers started") }
                        override fun onFailure(reason: Int) {
                            log("$TAG retry $n: discoverPeers failed (${reasonStr(reason)})")
                        }
                    })
                    attemptMacJoin(mgr, chan, qr, log)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun attemptCredentialJoin(
        mgr: WifiP2pManager,
        chan: WifiP2pManager.Channel,
        qr: QrData,
        log: (String) -> Unit,
    ) {
        if (qr.pwd.isBlank()) {
            log("$TAG credential-join skipped — empty passphrase")
            attemptMacJoin(mgr, chan, qr, log)
            return
        }
        val config = try {
            WifiP2pConfig.Builder()
                .setNetworkName(qr.ssid)
                .setPassphrase(qr.pwd)
                .enablePersistentMode(false)
                .build()
        } catch (e: RuntimeException) {
            log("$TAG credential-join not usable: ${e.message}")
            log("$TAG   falling back to MAC / peer discovery for '${qr.ssid}'")
            attemptMacJoin(mgr, chan, qr, log)
            return
        }

        log("$TAG connect(): joining group name='${qr.ssid}' as legacy client …")
        connectIssued = true
        mgr.connect(chan, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                log("$TAG connect(): credential request accepted — waiting for group to form")
            }
            override fun onFailure(reason: Int) {
                log("$TAG connect(): credential failed (${reasonStr(reason)}) — trying MAC")
                connectIssued = false
                attemptMacJoin(mgr, chan, qr, log)
            }
        })
    }

    /**
     * OEM Carbit Ride joins many P2P-only SoftAP QRs by **device address** from the QR `mac=`
     * field when the SSID is not a `DIRECT-*` group name (seen on Zontes 350 T2: `ZT5Gcf3b`).
     */
    @SuppressLint("MissingPermission")
    private fun attemptMacJoin(
        mgr: WifiP2pManager,
        chan: WifiP2pManager.Channel,
        qr: QrData,
        log: (String) -> Unit,
    ) {
        if (!active || connected) return
        val qrMac = normalizeMac(qr.mac)
        if (qrMac == null) {
            log("$TAG MAC-join skipped — QR has no usable mac=")
            return
        }
        if (connectIssued) return
        val listedMac = listedAs(qrMac)
        val mac = listedMac ?: qrMac
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastConnectAt < MIN_CONNECT_GAP_MS) return
        val listed = listedMac != null
        val blind = peersSeenAt == 0L && now - startedAt >= BLIND_AFTER_MS
        if (!listed && !blind) {
            if (!waitingLogged) {
                log("$TAG waiting for the dash ($mac) to show up in the peer list before connecting")
                waitingLogged = true
            }
            return
        }
        waitingLogged = false
        lastConnectAt = now
        connectIssued = true
        // Every third failure, try the old order once: stop discovery first (some Pixels needed it).
        val stopFirst = macFailures > 0 && macFailures % 3 == 2
        if (!stopFirst) {
            issueMacConnect(mgr, chan, mac, listed, log)
            return
        }
        log("$TAG fallback: stopping discovery before this connect (failures so far: $macFailures)")
        try {
            mgr.stopPeerDiscovery(chan, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { issueMacConnect(mgr, chan, mac, listed, log) }
                override fun onFailure(reason: Int) { issueMacConnect(mgr, chan, mac, listed, log) }
            })
        } catch (e: Exception) {
            issueMacConnect(mgr, chan, mac, listed, log)
        }
    }

    @SuppressLint("MissingPermission")
    private fun issueMacConnect(
        mgr: WifiP2pManager,
        chan: WifiP2pManager.Channel,
        mac: String,
        listed: Boolean,
        log: (String) -> Unit,
    ) {
        if (!active || connected) { connectIssued = false; return }
        val config = WifiP2pConfig().apply {
            deviceAddress = mac
            wps.setup = WpsInfo.PBC
            // We join the dash's group: never volunteer to be the owner.
            groupOwnerIntent = 0
        }
        // 350D (Ricky, 20 Oct 2026): the request was accepted but the dash stayed INVITED and no group
        // formed in 40 s. INVITED = Android re-invoking a remembered (persistent) group, likely stale
        // from the official app. Ask for a fresh, temporary group instead (hidden field; best effort).
        // 22 Oct: builds without this hack also got ERROR, but alternate it so neither variant is the
        // only one tried: even attempts ask for a temporary group, odd ones use Android's default.
        val temporary = macFailures % 2 == 0
        if (temporary) try {
            WifiP2pConfig::class.java.getField("netId").setInt(config, -1) // WifiP2pGroup.NETWORK_ID_TEMPORARY
        } catch (_: Throwable) {}
        log("$TAG connect(): joining peer MAC=$mac (WPS PBC, client, ${if (temporary) "temporary" else "default"} group; " +
            "${if (listed) "listed among ${listedPeers.size} peer(s)" else "NOT listed — blind try"}) …")
        mgr.connect(chan, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                acceptedAt = android.os.SystemClock.elapsedRealtime()
                log("$TAG connect(): MAC request accepted — waiting for group to form")
                watchAcceptedJoin(mgr, chan, log)
            }
            override fun onFailure(reason: Int) {
                macFailures++
                log("$TAG connect(): MAC failed (${reasonStr(reason)}) #$macFailures — will retry when the dash is listed again")
                connectIssued = false
                // Re-arm discovery so PEERS_CHANGED can retry the MAC join.
                try {
                    mgr.discoverPeers(chan, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() { log("$TAG discoverPeers: restarted after MAC fail") }
                        override fun onFailure(r: Int) {
                            log("$TAG discoverPeers restart failed (${reasonStr(r)})")
                        }
                    })
                } catch (_: Exception) {}
            }
        })
    }

    @Volatile private var acceptedRetries = 0

    /**
     * After an accepted connect(), give the group [ACCEPTED_WAIT_MS] to form; if it doesn't, cancel
     * the pending invitation and let the retry loop issue a fresh one (at most twice per attempt).
     */
    private fun watchAcceptedJoin(mgr: WifiP2pManager, chan: WifiP2pManager.Channel, log: (String) -> Unit) {
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (!active || connected || acceptedRetries >= 2) return@postDelayed
            acceptedRetries++
            log("$TAG no group ${ACCEPTED_WAIT_MS / 1000}s after the accepted connect — cancelling and inviting again ($acceptedRetries/2)")
            try {
                mgr.cancelConnect(chan, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { log("$TAG cancelConnect: ok") }
                    override fun onFailure(reason: Int) { log("$TAG cancelConnect: ${reasonStr(reason)}") }
                })
                mgr.removeGroup(chan, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) {}
                })
            } catch (_: Exception) {}
            connectIssued = false
            try {
                mgr.discoverPeers(chan, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { log("$TAG discoverPeers: restarted for a fresh invite") }
                    override fun onFailure(r: Int) { log("$TAG discoverPeers restart failed (${reasonStr(r)})") }
                })
            } catch (_: Exception) {}
        }, ACCEPTED_WAIT_MS)
    }

    private fun registerReceiver(
        ctx: Context,
        mgr: WifiP2pManager,
        chan: WifiP2pManager.Channel,
        qr: QrData,
        onConnected: (Inet4Address, Inet4Address) -> Unit,
        onFailed: (String) -> Unit,
        log: (String) -> Unit,
    ) {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        val rx = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context, intent: Intent) {
                if (!active) return
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) ==
                            WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        log("$TAG state: Wi-Fi P2P ${if (enabled) "ENABLED" else "DISABLED"}")
                        if (!enabled) fail(onFailed, log, "Wi-Fi P2P is disabled — enable Wi-Fi and retry")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        mgr.requestPeers(chan) { peers ->
                            if (!active || connected) return@requestPeers
                            listedPeers = peers.deviceList.mapNotNull { normalizeMac(it.deviceAddress) }.toSet()
                            if (peers.deviceList.isEmpty()) {
                                log("$TAG peers: (none yet)")
                                return@requestPeers
                            }
                            peersSeenAt = android.os.SystemClock.elapsedRealtime()
                            val wantMac = normalizeMac(qr.mac)
                            var matched: WifiP2pDevice? = null
                            for (d: WifiP2pDevice in peers.deviceList) {
                                log(
                                    "$TAG peer: name='${d.deviceName}' addr=${d.deviceAddress} " +
                                        "status=${deviceStatus(d.status)} isGO=${d.isGroupOwner} wps=${P2pWarmUp.wpsMethods(d)}",
                                )
                                val peerMac = normalizeMac(d.deviceAddress)
                                if (wantMac != null && peerMac != null && macMatches(wantMac, peerMac)) {
                                    matched = d
                                } else if (
                                    matched == null &&
                                    qr.ssid.isNotBlank() &&
                                    d.deviceName.contains(qr.ssid, ignoreCase = true)
                                ) {
                                    matched = d
                                }
                            }
                            val peer = matched ?: return@requestPeers
                            if (connectIssued) return@requestPeers
                            log(
                                "$TAG found matching peer '${peer.deviceName}' (${peer.deviceAddress}) — connecting",
                            )
                            // Prefer the shared MAC-join path (stops discovery first).
                            attemptMacJoin(
                                mgr,
                                chan,
                                qr.copy(mac = peer.deviceAddress.ifBlank { qr.mac }),
                                log,
                            )
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        mgr.requestConnectionInfo(chan) { info ->
                            log("$TAG conn: groupFormed=${info.groupFormed} isGO=${info.isGroupOwner} " +
                                "goAddr=${info.groupOwnerAddress?.hostAddress}")
                            if (info.groupFormed && !connected) {
                                onGroupFormed(mgr, chan, info, onConnected, onFailed, log)
                            } else if (!info.groupFormed && connected) {
                                connected = false
                                log("$TAG group lost")
                            }
                        }
                    }
                }
            }
        }
        receiver = rx
        // Not exported: only the system broadcasts these actions to us.
        registerSystemReceiver(ctx, rx, filter)
    }

    /** Normalize `aa:bb:…` / `aabb…` to lowercase colon form; null if not 12 hex digits. */
    private fun normalizeMac(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val hex = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
        if (hex.length != 12) return raw.takeIf { it.contains(':') && it.length >= 11 }?.lowercase()
        return hex.chunked(2).joinToString(":") { it.lowercase() }
    }

    /**
     * Exact match, or Wi‑Fi/BT locally-administered offset (±1 on the last octet) seen on some
     * Carbit units where the QR `mac=` and the P2P device address differ by one.
     */
    private fun macMatches(want: String, peer: String): Boolean {
        if (want.equals(peer, ignoreCase = true)) return true
        val w = want.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.lowercase()
        val p = peer.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.lowercase()
        if (w.length != 12 || p.length != 12) return false
        if (w == p) return true
        if (w.take(10) != p.take(10)) return false
        val wd = w.takeLast(2).toIntOrNull(16) ?: return false
        val pd = p.takeLast(2).toIntOrNull(16) ?: return false
        return abs(wd - pd) == 1
    }

    @SuppressLint("MissingPermission")
    private fun onGroupFormed(
        mgr: WifiP2pManager,
        chan: WifiP2pManager.Channel,
        info: WifiP2pInfo,
        onConnected: (Inet4Address, Inet4Address) -> Unit,
        onFailed: (String) -> Unit,
        log: (String) -> Unit,
    ) {
        clientOfGroup = !info.isGroupOwner
        if (info.isGroupOwner) {
            // We should be the client, not the GO. If we became GO the bike won't connect back.
            log("$TAG !! WE became the Group Owner — the bike is expected to be the GO. " +
                "This usually means the join fell back to creating a new group. Share this log.")
        }
        val gateway = info.groupOwnerAddress as? Inet4Address
            ?: (info.groupOwnerAddress?.let { asInet4(it) })
            ?: run { fail(onFailed, log, "group formed but GO address is not IPv4"); return }

        mgr.requestGroupInfo(chan) { group ->
            val iface = group?.`interface`
            log("$TAG group: name='${group?.networkName}' owner='${group?.owner?.deviceName}' iface=$iface " +
                "clients=${group?.clientList?.size ?: 0}")
            // DHCP on the P2P link can lag the "group formed" event; poll for our local address.
            thread(name = "p2p-ip-poll", isDaemon = true) {
                val bindIp = pollLocalP2pIp(iface, log)
                if (bindIp == null) {
                    fail(onFailed, log, "group formed but could not resolve our 192.168.49.x address on $iface")
                    return@thread
                }
                if (!active || connected) return@thread
                connected = true
                cancelTimeout()
                log("$TAG *** connected: phone=${bindIp.hostAddress} bike(GO)=${gateway.hostAddress} — starting PXC ***")
                onConnected(bindIp, gateway)
            }
        }
    }

    /** Poll for our IPv4 on the P2P interface (falls back to any 192.168.49.x that is not the GO). */
    private fun pollLocalP2pIp(ifaceName: String?, log: (String) -> Unit): Inet4Address? {
        val deadline = System.currentTimeMillis() + IP_POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && active) {
            localP2pIpv4(ifaceName, log)?.let { return it }
            try { Thread.sleep(IP_POLL_INTERVAL_MS) } catch (_: InterruptedException) { return null }
        }
        return localP2pIpv4(ifaceName, log)
    }

    private fun localP2pIpv4(ifaceName: String?, log: (String) -> Unit): Inet4Address? {
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                val nameMatches = ifaceName == null || ni.name == ifaceName || ni.name.startsWith("p2p")
                for (addr in ni.inetAddresses) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    // Prefer the exact P2P interface; otherwise accept the canonical P2P subnet.
                    if (ni.name == ifaceName) return addr
                    if (nameMatches && host.startsWith("192.168.49.") && host != "192.168.49.1") return addr
                }
            }
        } catch (e: Exception) {
            log("$TAG localP2pIpv4 error: ${e.message}")
        }
        return null
    }

    private fun startTimeout(onFailed: (String) -> Unit, log: (String) -> Unit, timeoutMs: Long) {
        cancelTimeout()
        timeoutThread = thread(name = "p2p-timeout", isDaemon = true) {
            try { Thread.sleep(timeoutMs) } catch (_: InterruptedException) { return@thread }
            // Ricky's 350E log (22 Oct): the one connect() that was accepted came 34 s in, the dash went
            // INVITED, and the 40 s window closed 6 s later. An accepted invitation gets 25 s of its own.
            val sinceAccept = android.os.SystemClock.elapsedRealtime() - acceptedAt
            if (active && !connected && acceptedAt > 0L && sinceAccept < ACCEPT_GRACE_MS) {
                log("$TAG timeout reached while an accepted invitation is pending — waiting ${(ACCEPT_GRACE_MS - sinceAccept) / 1000}s more")
                try { Thread.sleep(ACCEPT_GRACE_MS - sinceAccept) } catch (_: InterruptedException) { return@thread }
            }
            if (active && !connected) {
                fail(onFailed, log, "no P2P group formed within ${timeoutMs / 1000}s")
            }
        }
    }

    private fun cancelTimeout() {
        timeoutThread?.interrupt()
        timeoutThread = null
    }

    private fun fail(onFailed: (String) -> Unit, log: (String) -> Unit, reason: String) {
        if (!active) return
        active = false
        cancelTimeout()
        log("$TAG FAILED: $reason")
        onFailed(reason)
    }

    /** Stay in the Wi-Fi Direct group (dash clock) — do not [removeGroup]. */
    fun park(log: (String) -> Unit) {
        active = false
        connectIssued = false
        cancelTimeout()
        log("$TAG parked — group kept (keep Wi-Fi after disconnect)")
    }

    fun stop(log: (String) -> Unit) {
        active = false
        connected = false
        connectIssued = false
        cancelTimeout()
        val ctx = appContext
        receiver?.let { r -> if (ctx != null) try { ctx.unregisterReceiver(r) } catch (_: Exception) {} }
        receiver = null
        val mgr = manager
        val chan = channel
        if (mgr != null && chan != null) {
            try {
                mgr.cancelConnect(chan, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}
                    override fun onFailure(reason: Int) {}
                })
            } catch (_: Exception) {}
            try {
                mgr.removeGroup(chan, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { log("$TAG group removed") }
                    override fun onFailure(reason: Int) { /* no active group; ignore */ }
                })
            } catch (_: Exception) {}
        }
        manager = null
        channel = null
        appContext = null
    }

    private fun asInet4(a: InetAddress): Inet4Address? =
        (a as? Inet4Address) ?: try { InetAddress.getByName(a.hostAddress) as? Inet4Address } catch (_: Exception) { null }

    /**
     * The runtime permission Wi‑Fi Direct needs: «Nearby devices» (Wi‑Fi) on Android 13+, precise
     * location before. Without it every P2P call fails at once with a bare ERROR (350D log, 20 Oct:
     * the Connect prompt had only granted the Bluetooth half of «Nearby devices»).
     */
    fun permissionName(): String =
        if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.NEARBY_WIFI_DEVICES
        else android.Manifest.permission.ACCESS_FINE_LOCATION

    fun hasPermission(ctx: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(ctx, permissionName()) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun reasonStr(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
        WifiP2pManager.ERROR -> "ERROR"
        WifiP2pManager.BUSY -> "BUSY"
        WifiP2pManager.NO_SERVICE_REQUESTS -> "NO_SERVICE_REQUESTS"
        else -> "reason=$reason"
    }

    private fun deviceStatus(status: Int): String = when (status) {
        WifiP2pDevice.CONNECTED -> "CONNECTED"
        WifiP2pDevice.INVITED -> "INVITED"
        WifiP2pDevice.FAILED -> "FAILED"
        WifiP2pDevice.AVAILABLE -> "AVAILABLE"
        WifiP2pDevice.UNAVAILABLE -> "UNAVAILABLE"
        else -> "status=$status"
    }

    /**
     * Register a receiver that only the system feeds. On API 33+ the exported flag is mandatory;
     * these P2P actions are system broadcasts, so NOT_EXPORTED is correct and safe.
     */
    private fun registerSystemReceiver(ctx: Context, rx: BroadcastReceiver, filter: IntentFilter) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(rx, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(rx, filter)
        }
    }
}
