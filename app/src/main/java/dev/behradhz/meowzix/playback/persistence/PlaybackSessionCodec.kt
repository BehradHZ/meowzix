package dev.behradhz.meowzix.playback.persistence

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.nio.charset.StandardCharsets
import java.util.Base64

object PlaybackSessionCodec {
    private const val VERSION = "v1"
    private const val NULL_VALUE = "-"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(session: PersistedPlaybackSession): String = buildString {
        appendLine(
            listOf(
                VERSION,
                session.currentIndex.toString(),
                session.positionMs.toString(),
                session.playbackMode.name,
                session.repeatMode.name,
            ).joinToString("|"),
        )
        session.items.forEach { item ->
            appendLine(
                listOf(
                    "i",
                    encodeText(item.mediaId),
                    encodeText(item.uri),
                    encodeText(item.title),
                    encodeNullableText(item.artist),
                    encodeNullableText(item.artworkUri),
                    item.durationMs.toString(),
                ).joinToString("|"),
            )
        }
    }

    fun decode(value: String): PersistedPlaybackSession? = runCatching {
        val lines = value.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.first().split('|')
        require(header.size == 5 && header[0] == VERSION)

        val items = lines.drop(1).map { line ->
            val fields = line.split('|')
            require(fields.size == 7 && fields[0] == "i")
            PersistedPlaybackItem(
                mediaId = decodeText(fields[1]),
                uri = decodeText(fields[2]),
                title = decodeText(fields[3]),
                artist = decodeNullableText(fields[4]),
                artworkUri = decodeNullableText(fields[5]),
                durationMs = fields[6].toLong().coerceAtLeast(0),
            )
        }

        val requestedIndex = header[1].toInt()
        PersistedPlaybackSession(
            items = items,
            currentIndex = if (items.isEmpty()) 0 else requestedIndex.coerceIn(items.indices),
            positionMs = header[2].toLong().coerceAtLeast(0),
            playbackMode = PlaybackMode.valueOf(header[3]),
            repeatMode = RepeatMode.valueOf(header[4]),
        )
    }.getOrNull()

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)

    private fun encodeNullableText(value: String?): String = value?.let(::encodeText) ?: NULL_VALUE

    private fun decodeNullableText(value: String): String? =
        if (value == NULL_VALUE) null else decodeText(value)
}
