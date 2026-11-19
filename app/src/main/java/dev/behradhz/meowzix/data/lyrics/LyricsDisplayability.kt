package dev.behradhz.meowzix.data.lyrics

import dev.behradhz.meowzix.domain.lyrics.LrcParser
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType

/** The same visibility rule applies to embedded lyrics and discovered sidecar files. */
internal fun isDisplayableLyrics(text: String): Boolean {
    val parsed = LrcParser.parse(text)
    return when (parsed.contentType) {
        LyricsContentType.TIMED -> parsed.timedLines.any { it.text.isNotBlank() }
        LyricsContentType.PLAIN -> parsed.plainLines.any(String::isNotBlank)
        else -> false
    }
}
