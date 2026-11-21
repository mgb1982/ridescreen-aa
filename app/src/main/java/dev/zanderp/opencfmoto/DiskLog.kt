// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Durable log: every line goes to filesDir/logs/log-YYYYMMDD.txt through one background thread (flushed
 * every ~2 s and on [flushNow]). Lines are ALWAYS redacted, whatever LogBus.includeSecrets says.
 * filesDir survives app updates, so these files carry a ride across a reinstall.
 */
object DiskLog {
    private class Item(val at: Long, val text: String)

    private val queue = ConcurrentLinkedQueue<Item>()
    private val writeLock = Any()
    @Volatile private var dir: File? = null
    private var currentDay: String? = null
    private val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)

    fun init(ctx: Context) {
        if (dir != null) return
        val d = File(ctx.applicationContext.filesDir, "logs").apply { mkdirs() }
        dir = d
        prune()
        Thread({
            while (true) {
                try { Thread.sleep(2000); flushNow() } catch (_: Throwable) {}
            }
        }, "log-disk").apply { isDaemon = true }.start()
    }

    private val preInit = java.util.concurrent.atomic.AtomicInteger()
    private const val PRE_INIT_MAX = 2_000

    fun dir(): File? = dir

    /** Called from LogBus with the already-timestamp-free message; redacts again unconditionally. */
    fun enqueue(msg: String, now: Long = System.currentTimeMillis()) {
        // Lines from before [init] (app start, restored session) wait in the queue; capped so a missing
        // init can never grow it without bound.
        if (dir == null && preInit.incrementAndGet() > PRE_INIT_MAX) return
        queue.add(Item(now, LogRedactor.redact(msg)))
    }

    fun flushNow() {
        val d = dir ?: return
        synchronized(writeLock) {
            try {
                if (queue.isEmpty()) return
                val byDay = LinkedHashMap<String, StringBuilder>()
                while (true) {
                    val it = queue.poll() ?: break
                    val day = LogStoreLogic.dayKey(it.at)
                    byDay.getOrPut(day) { StringBuilder() }
                        .append(synchronized(ts) { ts.format(java.util.Date(it.at)) }).append("  ").append(it.text).append('\n')
                }
                for ((day, text) in byDay) {
                    if (day != currentDay) { currentDay = day; d.mkdirs(); prune() }
                    FileOutputStream(File(d, "log-$day.txt"), true).use { it.write(text.toString().toByteArray(Charsets.UTF_8)) }
                }
            } catch (_: Throwable) {}
        }
    }

    fun entries(): List<LogStoreLogic.Entry> =
        dir?.listFiles()?.filter { it.isFile }?.map { LogStoreLogic.Entry(it.name, it.length()) } ?: emptyList()

    private fun prune() {
        val d = dir ?: return
        try {
            LogStoreLogic.toPrune(entries(), LogStoreLogic.dayKey(System.currentTimeMillis())).forEach { File(d, it).delete() }
        } catch (_: Throwable) {}
    }

    /** Clear Logs: drop pending lines and every file. */
    fun clearAll() {
        synchronized(writeLock) {
            queue.clear()
            dir?.listFiles()?.forEach { try { it.delete() } catch (_: Throwable) {} }
            currentDay = null
        }
    }

    /**
     * Writes the share section: previous day then today, newest data kept when over [budget] bytes
     * (an ellipsis marker replaces what was dropped). Returns bytes written.
     */
    fun writeShare(w: java.io.Writer, budget: Long): Long {
        val d = dir ?: return 0L
        flushNow()
        val names = LogStoreLogic.shareFiles(entries().map { it.name }, LogStoreLogic.dayKey(System.currentTimeMillis()))
        val files = names.map { File(d, it) }
        val skips = LogStoreLogic.skipBytes(files.map { it.length() }, budget)
        var written = 0L
        files.forEachIndexed { i, f ->
            val header = "\n\n===== DISK LOG (${f.name}) =====\n"
            w.write(header)
            val bytes = try { f.readBytes() } catch (_: Exception) { ByteArray(0) }
            var from = skips[i].toInt().coerceAtMost(bytes.size)
            if (from > 0) {
                while (from < bytes.size && bytes[from - 1] != '\n'.code.toByte()) from++
                w.write("…\n")
            }
            val text = String(bytes, from, bytes.size - from, Charsets.UTF_8)
            w.write(text)
            written += text.length
        }
        return written
    }
}
