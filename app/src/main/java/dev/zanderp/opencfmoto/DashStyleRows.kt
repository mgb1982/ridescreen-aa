package dev.zanderp.opencfmoto

/** Pure helpers for the Home style sheet ([DashStyleSheet]); no Android types so they run in unit tests. */
object DashStyleRows {
    /** One list row: the item and whether it is the current one. Exactly one row is selected if [current] is in [all]. */
    data class Row<T>(val item: T, val selected: Boolean)

    fun <T> rows(all: List<T>, current: T): List<Row<T>> = all.map { Row(it, it == current) }

    /** Row text: the current style gets a check mark in front. */
    fun label(name: String, selected: Boolean): String = if (selected) "✓  $name" else name

    /** Cache key of the big preview: a change in any part means the picture must be redrawn. */
    fun previewKey(designId: String, light: Boolean, accent: Int, width: Int, height: Int, extra: String = ""): String =
        "$designId|$light|$accent|${width}x$height" + if (extra.isEmpty()) "" else "|$extra"
}
