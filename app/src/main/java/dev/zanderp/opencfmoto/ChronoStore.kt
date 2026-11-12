// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The last 20 timer runs as our own JSON in SharedPreferences; records are derived from them ([ChronoHistory]). */
internal object ChronoStore {
    private const val PREFS = "chrono"
    private const val KEY = "runs_v1"

    fun load(ctx: Context): List<ChronoRun> = try {
        val arr = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ChronoRun(
                atMs = o.getLong("at"), imperial = o.optBoolean("mph"), gps = o.optBoolean("gps"),
                t1 = if (o.has("t1")) o.getDouble("t1") else null,
                t2 = if (o.has("t2")) o.getDouble("t2") else null,
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun save(ctx: Context, list: List<ChronoRun>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().put("at", r.atMs).put("mph", r.imperial).put("gps", r.gps).apply {
                r.t1?.let { put("t1", it) }
                r.t2?.let { put("t2", it) }
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** Stores [run]; returns which targets (first, second) just became records. */
    @Synchronized
    fun add(ctx: Context, run: ChronoRun): Pair<Boolean, Boolean> {
        val before = load(ctx)
        save(ctx, ChronoHistory.add(before, run))
        return ChronoHistory.newRecords(before, run)
    }

    @Synchronized
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
