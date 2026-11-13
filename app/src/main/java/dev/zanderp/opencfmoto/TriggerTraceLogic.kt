// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

/** Pure part of [TriggerTrace] (no Android types), unit tested. */
internal object TriggerTraceLogic {
    /** Which log lines are kept: the Bluetooth trigger and the Wi-Fi wait. */
    internal fun wants(msg: String): Boolean =
        msg.startsWith("[BT-TRIG]") || msg.startsWith("[WATCH]") || msg.startsWith("→ BT trigger") ||
            msg.startsWith("[LOCK-HOLD]")

    /** One stored entry: `epochMs<TAB>text`. */
    internal fun encode(epochMs: Long, text: String): String = "$epochMs\t${text.replace('\n', ' ')}"

    internal fun decode(line: String): Pair<Long, String>? {
        val i = line.indexOf('\t')
        if (i <= 0) return null
        val t = line.substring(0, i).toLongOrNull() ?: return null
        return t to line.substring(i + 1)
    }

    /** The last [max] lines. */
    internal fun trim(lines: List<String>, max: Int = 60): List<String> =
        if (lines.size <= max) lines else lines.subList(lines.size - max, lines.size)

    /** Entries newer than [afterMs], oldest first. */
    internal fun newer(lines: List<String>, afterMs: Long): List<Pair<Long, String>> =
        lines.mapNotNull { decode(it) }.filter { it.first > afterMs }

}
