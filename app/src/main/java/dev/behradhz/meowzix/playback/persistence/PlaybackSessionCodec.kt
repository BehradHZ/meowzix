package dev.behradhz.meowzix.playback.persistence

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.nio.charset.StandardCharsets
import java.util.Base64

object PlaybackSessionCodec {
    private const val VERSION = "v2"
    private const val LEGACY_VERSION = "v1"
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
                session.logicalCurrentIndex.toString(),
                session.materializedStartIndex.toString(),
                session.materializedEndExclusive.toString(),
                session.shuffleSeed?.toString() ?: NULL_VALUE,
            ).joinToString("|"),
        )
        session.logicalMediaIds.forEach { mediaId ->
            appendLine(listOf("q", encodeText(mediaId)).joinToString("|"))
        }
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
        when (header.firstOrNull()) {
            VERSION -> decodeV2(header, lines.drop(1))
            LEGACY_VERSION -> decodeV1(header, lines.drop(1))
            else -> error("Unsupported playback session version")
        }
    }.getOrNull()

    private fun decodeV2(header: List<String>, body: List<String>): PersistedPlaybackSession {
        require(header.size == 9)
        val items = body.filter { it.startsWith("i|") }.map(::decodeItem)
        val logicalIds = body.filter { it.startsWith("q|") }.map { line ->
            val fields = line.split('|')
            require(fields.size == 2)
            decodeText(fields[1])
        }.distinct()
        val requestedIndex = header[1].toInt()
        val logicalIndex = header[5].toInt()
        val normalizedLogicalIds = logicalIds.ifEmpty { items.map(PersistedPlaybackItem::mediaId) }
        return PersistedPlaybackSession(
            items = items,
            currentIndex = if (items.isEmpty()) 0 else requestedIndex.coerceIn(items.indices),
            positionMs = header[2].toLong().coerceAtLeast(0),
            playbackMode = PlaybackMode.valueOf(header[3]),
            repeatMode = RepeatMode.valueOf(header[4]),
            logicalMediaIds = normalizedLogicalIds,
            logicalCurrentIndex = if (normalizedLogicalIds.isEmpty()) -1 else logicalIndex.coerceIn(normalizedLogicalIds.indices),
            materializedStartIndex = header[6].toInt().coerceAtLeast(0),
            materializedEndExclusive = header[7].toInt().coerceAtLeast(0),
            shuffleSeed = header[8].takeUnless { it == NULL_VALUE }?.toLong(),
        )
    }

    private fun decodeV1(header: List<String>, body: List<String>): PersistedPlaybackSession {
        require(header.size == 5)
        val items = body.map(::decodeItem)
        val requestedIndex = header[1].toInt()
        val currentIndex = if (items.isEmpty()) 0 else requestedIndex.coerceIn(items.indices)
        return PersistedPlaybackSession(
            items = items,
            currentIndex = currentIndex,
            positionMs = header[2].toLong().coerceAtLeast(0),
            playbackMode = PlaybackMode.valueOf(header[3]),
            repeatMode = RepeatMode.valueOf(header[4]),
            logicalMediaIds = items.map(PersistedPlaybackItem::mediaId),
            logicalCurrentIndex = if (items.isEmpty()) -1 else currentIndex,
            materializedStartIndex = 0,
            materializedEndExclusive = items.size,
            shuffleSeed = null,
        )
    }

    private fun decodeItem(line: String): PersistedPlaybackItem {
        val fields = line.split('|')
        require(fields.size == 7 && fields[0] == "i")
        return PersistedPlaybackItem(
            mediaId = decodeText(fields[1]),
            uri = decodeText(fields[2]),
            title = decodeText(fields[3]),
            artist = decodeNullableText(fields[4]),
            artworkUri = decodeNullableText(fields[5]),
            durationMs = fields[6].toLong().coerceAtLeast(0),
        )
    }

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)

    private fun encodeNullableText(value: String?): String = value?.let(::encodeText) ?: NULL_VALUE

    private fun decodeNullableText(value: String): String? =
        if (value == NULL_VALUE) null else decodeText(value)
}
