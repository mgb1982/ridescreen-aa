package dev.zanderp.opencfmoto

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Mirrors net.easyconn.carman.common.base.QrResult.parseResult — the bike's QR is a URL
 * whose query string carries the connection params:
 *
 *   http://www.carbit.com.cn/...?modelid=...&sn=...&action=9
 *     &ssid=CFMOTO-xxxxxx&pwd=xxxxxx&auth=wpa2-psk
 *     &mac=xx:xx:xx:xx:xx:xx&name=CFMOTO-xxxxxx
 *
 * Moto Morini / MotoFun uses a different delimiter style (hash separators — not a URL fragment):
 *
 *   http://admin.motomorini.com/app.html?Wifi=ML174167#12345678#dc0d30da1b6c
 *     &MachineID=dc0d30da1b6c&ProductID=00297
 *
 * Kove / Thinkerride SoftAP uses positional query fields (not ssid=/pwd=):
 *
 *   http://g.thinkerride.com?CQKY_5e1c6cb10&81316044&ap=1
 *
 * `action` is a bitmask: bit0=basic AP, bit1=AP+internet, bit3=Wi-Fi P2P, bit6=BT,
 * bit7=phone-hosts-hotspot (dash is STA; no ssid/pwd in the QR — see [supportsPhoneHotspot]).
 */
data class QrData(
    val ssid: String,
    val pwd: String,
    val auth: String?,
    val mac: String?,
    val name: String?,
    val action: Int,
    val modelId: String?,
    val sn: String?,
    val channel: String?,
    /** `ip=` from the QR (some dashes print the address of their group owner). Null when absent. */
    val ip: String? = null,
) {
    val supportsAp: Boolean get() = (action and 1) != 0 || (action and 2) != 0
    val supportsP2p: Boolean get() = (action and 8) != 0
    /** Bit7 / empty SoftAP creds — rider enables Android hotspot; dash joins the phone. */
    val supportsPhoneHotspot: Boolean
        get() = (action and 128) != 0 || (ssid.isBlank() && !mac.isNullOrBlank())

    companion object {
        fun parse(raw: String): QrData? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            parseCarbit(trimmed)?.let { return it }
            parseCarbitToken(trimmed)?.let { return it }
            parseMotoMorini(trimmed)?.let { return it }
            parseThinkerride(trimmed)?.let { return it }
            return null
        }

        /**
         * Classic Carbit / EasyConnect query params. SoftAP QRs carry `ssid=` + `pwd=`.
         * Phone-hotspot QRs (Zontes etc.) often only carry `action=128` + `bm=` (MAC) — no ssid/pwd.
         */
        private fun parseCarbit(raw: String): QrData? {
            val q = queryParams(raw)
            val action = q["action"]?.toIntOrNull() ?: 0
            val mac = formatMac(q["mac"]) ?: formatMac(q["bm"])
            val ssid = q["ssid"].orEmpty().trim()
            val pwd = q["pwd"].orEmpty()
            val phoneHotspot = (action and 128) != 0 || (ssid.isEmpty() && mac != null && q.containsKey("bm"))
            // v2.3 · Wi‑Fi Direct-only dashes (Zontes 350D, 19 Oct: `action=8&mac=…&name=ZT_…`, no
            // ssid/pwd): join the dash's P2P group by its MAC — the existing P2P path does that.
            val p2pOnly = ssid.isEmpty() && !phoneHotspot && (action and 8) != 0 && mac != null
            if (p2pOnly) {
                val name = q["name"]?.takeIf { it.isNotBlank() } ?: "ZT_${mac!!.replace(":", "")}"
                return QrData(
                    ssid = name, pwd = "", auth = q["auth"], mac = mac, name = name, action = action,
                    modelId = q["modelid"], sn = q["sn"], channel = q["channel"], ip = q["ip"]?.trim()?.ifBlank { null },
                )
            }
            if (ssid.isEmpty() && !phoneHotspot) return null
            if (ssid.isNotEmpty() && pwd.isEmpty() && !phoneHotspot) return null
            val display = q["name"]?.takeIf { it.isNotBlank() }
                ?: if (ssid.isNotEmpty()) null
                else mac?.let { "Phone hotspot (${it.takeLast(8)})" }
            return QrData(
                ssid = ssid.ifEmpty { "PHONE-HOTSPOT-${mac?.replace(":", "").orEmpty().takeLast(6)}" },
                pwd = pwd,
                auth = q["auth"],
                mac = mac,
                name = display,
                action = if (phoneHotspot && (action and 128) == 0) action or 128 else action,
                modelId = q["modelid"],
                sn = q["sn"],
                channel = q["channel"],
                ip = q["ip"]?.trim()?.ifBlank { null },
            )
        }

        /** Opaque `CARBIT` + 12 hex digits printed as a second QR on some Zontes units. */
        private fun parseCarbitToken(raw: String): QrData? {
            val m = Regex("""(?i)^CARBIT([0-9A-F]{12})$""").matchEntire(raw.trim()) ?: return null
            val mac = formatMac(m.groupValues[1]) ?: return null
            return QrData(
                ssid = "PHONE-HOTSPOT-${m.groupValues[1].takeLast(6)}",
                pwd = "",
                auth = null,
                mac = mac,
                name = "Phone hotspot (${mac.takeLast(8)})",
                action = 128,
                modelId = null,
                sn = null,
                channel = null,
            )
        }

        /** Query map from a URL (before `#` fragment). Keys are lower-cased. */
        private fun queryParams(raw: String): Map<String, String> {
            val query = raw.substringAfter('?', missingDelimiterValue = "")
                .substringBefore('#')
            if (query.isEmpty()) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (part in query.split('&')) {
                if (part.isEmpty()) continue
                val eq = part.indexOf('=')
                val key = if (eq >= 0) part.substring(0, eq) else part
                val value = if (eq >= 0) part.substring(eq + 1) else ""
                if (key.isEmpty()) continue
                out[key.lowercase()] = try {
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    value
                }
            }
            return out
        }

        /**
         * Moto Morini / MotoFun dash QR.
         *
         * Uses `#` as field separators after `Wifi=`, so [Uri] fragment parsing would eat the
         * password — we scan the raw string instead.
         */
        private fun parseMotoMorini(raw: String): QrData? {
            val wifi = Regex("""(?i)(?:^|[?&])Wifi=([^&#\s]+)""").find(raw) ?: return null
            val ssid = wifi.groupValues[1].trim()
            if (ssid.isEmpty()) return null

            // After Wifi=<ssid> expect #<pwd>#<mac>&MachineID=…&ProductID=…
            val after = raw.substring(wifi.range.last + 1)
            if (!after.startsWith('#')) return null
            val hashParts = after.removePrefix("#").split('#', limit = 2)
            val pwd = hashParts.getOrNull(0)?.substringBefore('&')?.trim().orEmpty()
            if (pwd.isEmpty()) return null

            val macToken = hashParts.getOrNull(1)?.substringBefore('&')?.trim()
                ?.takeIf { it.isNotEmpty() }
            val machineId = Regex("""(?i)MachineID=([^&#\s]+)""").find(raw)?.groupValues?.get(1)
            val productId = Regex("""(?i)ProductID=([^&#\s]+)""").find(raw)?.groupValues?.get(1)

            return QrData(
                ssid = ssid,
                pwd = pwd,
                auth = "wpa2-psk",
                mac = formatMac(macToken) ?: formatMac(machineId),
                name = ssid,
                action = 1, // SoftAP
                modelId = productId,
                sn = machineId,
                channel = null,
            )
        }

        /**
         * Kove / Thinkerride SoftAP pairing QR.
         *
         * Query is positional: `?<SSID>&<PWD>&ap=1` (fields often omit `=`). Also accepts a bare
         * `CQKY_…&…&ap=1` token if the host was stripped.
         */
        private fun parseThinkerride(raw: String): QrData? {
            val trimmed = raw.trim()
            val looksThinker = trimmed.contains("thinkerride.com", ignoreCase = true) ||
                Regex("""(?i)^CQKY_[^\s&]+&""").containsMatchIn(trimmed)
            if (!looksThinker) return null

            val query = when {
                trimmed.contains('?') -> trimmed.substringAfter('?').substringBefore('#')
                Regex("""(?i)^CQKY_""").containsMatchIn(trimmed) -> trimmed.substringBefore('#')
                else -> return null
            }
            if (query.isBlank()) return null

            var ssid: String? = null
            var pwd: String? = null
            for (part in query.split('&')) {
                if (part.isEmpty()) continue
                val eq = part.indexOf('=')
                if (eq >= 0) {
                    val key = part.substring(0, eq).trim().lowercase()
                    val value = try {
                        URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8.name())
                    } catch (_: Exception) {
                        part.substring(eq + 1)
                    }.trim()
                    when (key) {
                        "ssid" -> if (value.isNotEmpty()) ssid = value
                        "pwd", "password", "pass" -> if (value.isNotEmpty()) pwd = value
                        "ap" -> { /* SoftAP flag; pairing QRs always use action=1 */ }
                        else -> {
                            // Unknown key=value — ignore (do not treat as positional SSID).
                        }
                    }
                } else {
                    val token = try {
                        URLDecoder.decode(part, StandardCharsets.UTF_8.name())
                    } catch (_: Exception) {
                        part
                    }.trim()
                    if (token.isEmpty() || token.equals("ap", ignoreCase = true)) continue
                    when {
                        ssid == null -> ssid = token
                        pwd == null -> pwd = token
                    }
                }
            }
            val s = ssid?.trim().orEmpty()
            val p = pwd.orEmpty()
            if (s.isEmpty() || p.isEmpty()) return null
            return QrData(
                ssid = s,
                pwd = p,
                auth = "wpa2-psk",
                mac = null,
                name = s,
                action = 1, // SoftAP
                modelId = null,
                sn = null,
                channel = null,
            )
        }

        /** `aabbccddeeff` / `aa:bb:…` → colon form; null if not 12 hex digits. */
        private fun formatMac(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val hex = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            if (hex.length != 12) {
                // Already colon-separated or other — keep if it looks like a MAC.
                return raw.takeIf { it.contains(':') && it.length >= 11 }
            }
            return hex.chunked(2).joinToString(":") { it.lowercase() }
        }

        /**
         * Hint for failed scans (vehicle-info QR, app-download QR, etc.) — not a pairing code.
         */
        fun parseFailureHint(raw: String): String? {
            val t = raw.trim()
            when {
                t.contains("motomorini.com", ignoreCase = true) &&
                    !t.contains("Wifi=", ignoreCase = true) ->
                    return "Moto Morini QR has no Wifi= field — open the phone-link / MotoFun QR on the dash"
                Regex("""(?i)^code:.*engine:.*vin:""").containsMatchIn(t) ||
                    t.contains("color:", ignoreCase = true) && t.contains("vin:", ignoreCase = true) ->
                    return "that QR is bike info (color/VIN), not Wi‑Fi pairing — open MotoPlay / EasyConnect / MotoFun QR"
                t.contains("carbit.com", ignoreCase = true) &&
                    t.contains("downsdk", ignoreCase = true) ->
                    return "that is the Carbit app-download QR, not pairing — open MotoPlay / EasyConnect so the Wi-Fi QR (SSID + password) stays on screen"
                t.contains("carbit.com", ignoreCase = true) &&
                    !t.contains("ssid=", ignoreCase = true) &&
                    (t.contains("action=128", ignoreCase = true) || t.contains("bm=", ignoreCase = true)) ->
                    return "this dash joins your phone hotspot — turn on Android hotspot with the SSID/password shown on the dash, then Connect"
                Regex("""(?i)^CARBIT[0-9A-F]{12}$""").containsMatchIn(t) ->
                    return "Carbit ID QR — enable Android hotspot with the SSID/password on the dash, then Connect"
                t.contains("thinkerride.com", ignoreCase = true) ||
                    t.contains("kove", ignoreCase = true) && t.contains("download", ignoreCase = true) ||
                    t.contains("KOVE APP", ignoreCase = true) ->
                    return "Kove/Thinkerride — need the SoftAP pairing QR (SSID+password on the dash, or g.thinkerride.com?SSID&PWD&ap=1). Garage → enter SSID/pwd if the QR is only “download KOVE APP”"
                t.startsWith("http", ignoreCase = true) &&
                    !t.contains("ssid=", ignoreCase = true) &&
                    !t.contains("Wifi=", ignoreCase = true) ->
                    return "URL QR without ssid/pwd — need the dash pairing QR (Carbit / EasyConnect / MotoFun / Thinkerride SoftAP), or enable phone hotspot if the dash asks for it"
                else -> return null
            }
        }

        /**
         * Synthetic pairing URL for bikes that show SSID + password (e.g. Benelli TRK) instead of
         * a scannable Carbit QR. [action]=1 → SoftAP path (not Wi‑Fi Direct).
         * @return raw URL + parsed [QrData], or null if SSID/password blank.
         */
        fun buildManual(ssid: String, pwd: String, displayName: String? = null): Pair<String, QrData>? {
            val s = ssid.trim()
            val p = pwd // keep internal spaces; only require non-blank
            if (s.isEmpty() || p.isEmpty()) return null
            val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: s
            val raw = Uri.Builder()
                .scheme("http")
                .authority("manual.opencfmoto.local")
                .path("/")
                .appendQueryParameter("ssid", s)
                .appendQueryParameter("pwd", p)
                .appendQueryParameter("auth", "wpa2-psk")
                .appendQueryParameter("action", "1")
                .appendQueryParameter("name", name)
                .build()
                .toString()
            val qr = parse(raw) ?: return null
            return raw to qr
        }
    }
}
