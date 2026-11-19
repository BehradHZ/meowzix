package dev.behradhz.meowzix.widget

import java.text.BreakIterator
import java.util.Locale

/** A launcher-safe title ticker: updates text only, never sends animation bitmaps. */
internal fun scrollingTitleWindow(text: String, offset: Int, width: Int = 34): String {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val graphemes = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        graphemes += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    if (graphemes.size <= width || width <= 0) return text
    val loop = graphemes + listOf(" ", "•", " ")
    val begin = offset.coerceAtLeast(0) % loop.size
    return (0 until width).joinToString("") { loop[(begin + it) % loop.size] }
}

internal object PlaybackWidgetTitleTicker {
    private val lock = Any()
    private var lastIdentity: String? = null
    private var offset = 0

    fun next(identity: String?, title: String): String = synchronized(lock) {
        if (identity != lastIdentity) {
            lastIdentity = identity
            offset = 0
        }
        offset += 4
        scrollingTitleWindow(title, offset)
    }
}
