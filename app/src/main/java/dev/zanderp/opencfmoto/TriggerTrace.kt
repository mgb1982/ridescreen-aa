// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps, on disk and at the moment they happen, the log lines of the automatic-connect triggers
 * (the dash's Bluetooth, [BtConnectTrigger]; and the Wi-Fi wait, [BikeWatchService]).
 *
 * Why: a process started only to answer the Bluetooth broadcast usually dies (or is killed) before
 * the in-memory log reaches the disk, so a "it didn't connect by itself" report arrived with a log
 * that showed nothing before the rider opened the app (13 Nov 2026), and we could not tell "the
 * broadcast never arrived" from "it arrived and was ignored". Only lines of those two components are
 * kept (a few per day), never anything else. The next time the app starts they are replayed into
 * the log as `[TRACE]` lines with their original time.
 */
internal object TriggerTrace {
    private const val FILE = "trigger_trace.txt"
    private const val MAX_LINES = 60
    private const val MAX_BYTES = 16 * 1024

    @Volatile private var file: File? = null
    /** When this process started: its own lines are already in the live log, so they are not replayed. */
    @Volatile private var startedAt = Long.MAX_VALUE

    fun init(ctx: Context) {
        startedAt = System.currentTimeMillis()
        file = File(ctx.applicationContext.filesDir, FILE)
    }

    /** Called from [LogBus.log] for every line; writes only the wanted ones. Never throws. */
    fun onLog(safeMsg: String) {
        val f = file ?: return
        if (!TriggerTraceLogic.wants(safeMsg)) return
        try {
            synchronized(this) {
                f.appendText(TriggerTraceLogic.encode(System.currentTimeMillis(), safeMsg) + "\n")
                if (f.length() > MAX_BYTES) f.writeText(TriggerTraceLogic.trim(f.readLines(), MAX_LINES).joinToString("\n") + "\n")
            }
        } catch (_: Exception) {}
    }

    /**
     * At process start: puts the entries written since the last replay into the log as `[TRACE]`
     * lines (their own time first), then remembers the newest one so they appear only once.
     */
    fun replay(ctx: Context) {
        val f = file ?: return
        try {
            val prefs = ctx.applicationContext.getSharedPreferences("trigger_trace", Context.MODE_PRIVATE)
            val after = prefs.getLong("replayed_to", 0L)
            val entries = synchronized(this) { if (f.exists()) TriggerTraceLogic.newer(f.readLines(), after).filter { it.first < startedAt } else emptyList() }
            if (entries.isEmpty()) return
            val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)
            LogBus.log("[TRACE] ${entries.size} trigger line(s) written before this start (own time, kept on disk):")
            for ((t, text) in entries) LogBus.log("[TRACE] ${fmt.format(Date(t))}  $text")
            prefs.edit().putLong("replayed_to", entries.last().first).apply()
        } catch (_: Exception) {}
    }
}
