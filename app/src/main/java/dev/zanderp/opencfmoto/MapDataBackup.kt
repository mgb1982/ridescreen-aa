// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export / import Map hub personal data (favorites, markers, history, parked, map prefs).
 * Kept separate from [SettingsBackup] bike-tuning packs.
 */
object MapDataBackup {
    const val FORMAT = "opencfmoto.map"
    const val VERSION = 1

    data class Result(val ok: Boolean, val message: String)

    fun exportJson(context: Context): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", isoNow())
        root.put("appVersion", BuildConfig.VERSION_NAME)
        root.put(
            "prefs",
            JSONObject()
                .put("units", MapPrefs.units(context).name)
                .put("voicePrompts", MapPrefs.voicePrompts(context))
                .put("showNextStop", MapPrefs.showNextStop(context))
                .put("autoFinishArrive", MapPrefs.autoFinishOnArrive(context))
                .put("chrome", MapPrefs.chrome(context).name)
                .put("buildings3d", MapPrefs.buildings3d(context))
                .put("mapTheme", NightPrefs.theme(context).name)
                .put("routeMode", MapPrefs.routeMode(context).name)
                .put("avoidTolls", MapPrefs.avoidTolls(context))
                .put("avoidHighways", MapPrefs.avoidHighways(context))
                .put("funCircuitKm", MapPrefs.funCircuitKm(context)),
        )
        root.put("favorites", placesArray(MapPlaces.favorites(context)))
        root.put("markers", placesArray(MapPlaces.markers(context)))
        root.put("history", placesArray(MapPlaces.history(context)))
        MapPlaces.parked(context)?.let { root.put("parked", placeObj(it)) }
        MapPlaces.home(context)?.let { root.put("home", placeObj(it)) }
        return root.toString(2)
    }

    fun importJson(context: Context, json: String): Result {
        val root = try {
            JSONObject(json.trim().removePrefix("\uFEFF"))
        } catch (e: Exception) {
            return Result(false, "Not valid JSON: ${e.message}")
        }
        if (root.optString("format") != FORMAT) {
            return Result(false, "Not a RideScreen AA map file")
        }
        val ver = root.optInt("version", 0)
        if (ver < 1 || ver > VERSION) {
            return Result(false, "Unsupported map backup version $ver")
        }
        try {
            root.optJSONObject("prefs")?.let { p ->
                p.optString("units").takeIf { it.isNotBlank() }?.let {
                    runCatching { MapPrefs.setUnits(context, MapUnits.valueOf(it)) }
                }
                if (p.has("voicePrompts")) MapPrefs.setVoicePrompts(context, p.optBoolean("voicePrompts"))
                if (p.has("showNextStop")) MapPrefs.setShowNextStop(context, p.optBoolean("showNextStop"))
                if (p.has("autoFinishArrive")) {
                    MapPrefs.setAutoFinishOnArrive(context, p.optBoolean("autoFinishArrive"))
                }
                p.optString("chrome").takeIf { it.isNotBlank() }?.let {
                    runCatching { MapPrefs.setChrome(context, MapChrome.valueOf(it)) }
                }
                if (p.has("buildings3d")) MapPrefs.setBuildings3d(context, p.optBoolean("buildings3d"))
                p.optString("mapTheme").takeIf { it.isNotBlank() }?.let {
                    runCatching { NightPrefs.setTheme(context, MapTheme.valueOf(it)) }
                }
                p.optString("routeMode").takeIf { it.isNotBlank() }?.let {
                    runCatching { MapPrefs.setRouteMode(context, RouteMode.valueOf(it)) }
                }
                if (p.has("avoidTolls")) MapPrefs.setAvoidTolls(context, p.optBoolean("avoidTolls"))
                if (p.has("avoidHighways")) MapPrefs.setAvoidHighways(context, p.optBoolean("avoidHighways"))
                if (p.has("funCircuitKm")) MapPrefs.setFunCircuitKm(context, p.optInt("funCircuitKm", 60))
            }
            MapPlaces.replaceAll(
                context,
                favorites = parsePlaces(root.optJSONArray("favorites")),
                markers = parsePlaces(root.optJSONArray("markers")),
                history = parsePlaces(root.optJSONArray("history")),
                parked = root.optJSONObject("parked")?.let { parsePlace(it) },
                home = root.optJSONObject("home")?.let { parsePlace(it) },
            )
        } catch (e: Exception) {
            return Result(false, "Import failed: ${e.message}")
        }
        return Result(true, "Map data imported")
    }

    fun suggestedFileName(): String =
        "RideScreen-AA-map-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json"

    private fun placesArray(list: List<MapPlace>): JSONArray {
        val arr = JSONArray()
        for (p in list) arr.put(placeObj(p))
        return arr
    }

    private fun placeObj(p: MapPlace): JSONObject =
        JSONObject()
            .put("name", p.name)
            .put("lat", p.lat)
            .put("lon", p.lon)
            .put("category", p.category)
            .put("subtitle", p.subtitle)

    private fun parsePlaces(arr: JSONArray?): List<MapPlace> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                parsePlace(arr.getJSONObject(i))?.let { add(it) }
            }
        }
    }

    private fun parsePlace(o: JSONObject): MapPlace? = try {
        MapPlace(
            name = o.optString("name"),
            lat = o.getDouble("lat"),
            lon = o.getDouble("lon"),
            category = o.optString("category"),
            subtitle = o.optString("subtitle"),
        )
    } catch (_: Exception) {
        null
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
}
