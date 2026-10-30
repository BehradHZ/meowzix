package dev.behradhz.meowzix.playback.persistence

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.nio.charset.StandardCharsets
import java.util.Base64

object PlaybackSessionCodec {
    private const val VERSION = "v5"
    private const val PREVIOUS_VERSION = "v4"
    private const val LEGACY_VERSION = "v3"
    private const val OLDER_VERSION = "v2"
    private const val ORIGINAL_VERSION = "v1"
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
                session.generatedStarted.toString(),
                session.nextGenerationAt.toString(),
            ).joinToString("|"),
        )
        session.logicalMediaIds.forEachIndexed { index, mediaId ->
            val origin = session.logicalOrigins.getOrNull(index)
                ?: if (session.playbackMode == PlaybackMode.SMART_SHUFFLE && index != session.logicalCurrentIndex) {
                    QueueItemOrigin.GENERATED
                } else {
                    QueueItemOrigin.MANUAL
                }
            appendLine(listOf(
                "q", encodeText(mediaId), origin.name,
                session.logicalOccurrenceIds.getOrNull(index) ?: NULL_VALUE,
                session.logicalGroupIds.getOrNull(index) ?: NULL_VALUE,
            ).joinToString("|"))
        }
        if (session.logicalGroupIds.isNotEmpty()) appendLine("k|groups")
        session.logicalGroups.forEach {
            appendLine(listOf("g", it.id, it.kind, encodeText(it.title)).joinToString("|"))
        }
        session.sourceMediaIds.forEach { appendLine("s|${encodeText(it)}") }
        session.pendingMediaIds.forEach { appendLine("r|${encodeText(it)}") }
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
            VERSION -> decodeV5(header, lines.drop(1))
            PREVIOUS_VERSION -> decodeV4(header, lines.drop(1))
            LEGACY_VERSION -> decodeV3(header, lines.drop(1))
            OLDER_VERSION -> decodeV2(header, lines.drop(1))
            ORIGINAL_VERSION -> decodeV1(header, lines.drop(1))
            else -> error("Unsupported playback session version")
        }
    }.getOrNull()

    private fun decodeV5(header: List<String>, body: List<String>): PersistedPlaybackSession {
        require(header.size == 11)
        val legacyBody = body.map { line ->
            if (line.startsWith("q|")) line.split('|').take(3).joinToString("|") else line
        }
        val old = decodeV4(header, legacyBody)
        val entries = body.filter { it.startsWith("q|") }.map { it.split('|') }
        require(entries.all { it.size == 5 })
        val groups = body.filter { it.startsWith("g|") }.map { it.split('|') }
        require(groups.all { it.size == 4 })
        return old.copy(
            logicalOccurrenceIds = entries.map { it[3] }
                .takeIf { values -> values.all { it != NULL_VALUE } }.orEmpty(),
            logicalGroupIds = if (body.any { it == "k|groups" }) {
                entries.map { it[4].takeUnless { raw -> raw == NULL_VALUE } }
            } else emptyList(),
            logicalGroups = groups.map { PersistedQueueGroup(it[1], it[2], decodeText(it[3])) },
        )
    }

    private fun decodeV4(header: List<String>, body: List<String>): PersistedPlaybackSession {
        require(header.size == 11)
        val base = decodeV3(header.take(9), body)
        return base.copy(
            sourceMediaIds = body.filter { it.startsWith("s|") }.map { decodeText(it.substring(2)) },
            pendingMediaIds = body.filter { it.startsWith("r|") }.map { decodeText(it.substring(2)) },
            generatedStarted = header[9].toInt().coerceAtLeast(0),
            nextGenerationAt = header[10].toInt().coerceAtLeast(90),
        )
    }

    private fun decodeV3(header: List<String>, body: List<String>): PersistedPlaybackSession {
        require(header.size == 9)
        val items = body.filter { it.startsWith("i|") }.map(::decodeItem)
        val queue = body.filter { it.startsWith("q|") }.map { line ->
            val fields = line.split('|')
            require(fields.size == 3)
            decodeText(fields[1]) to QueueItemOrigin.valueOf(fields[2])
        }
        val requestedIndex = header[1].toInt()
        val logicalIndex = header[5].toInt()
        val logicalIds = queue.map { it.first }
        val normalizedLogicalIds = logicalIds.ifEmpty { items.map(PersistedPlaybackItem::mediaId) }
        val origins = if (queue.isEmpty()) {
            normalizedLogicalIds.indices.map { index -> fallbackOrigin(PlaybackMode.valueOf(header[3]), index, logicalIndex) }
        } else queue.map { it.second }
        return PersistedPlaybackSession(
            items = items,
            currentIndex = if (items.isEmpty()) 0 else requestedIndex.coerceIn(items.indices),
            positionMs = header[2].toLong().coerceAtLeast(0),
            playbackMode = PlaybackMode.valueOf(header[3]),
            repeatMode = RepeatMode.valueOf(header[4]),
            logicalMediaIds = normalizedLogicalIds,
            logicalOrigins = origins,
            logicalCurrentIndex = if (normalizedLogicalIds.isEmpty()) -1 else logicalIndex.coerceIn(normalizedLogicalIds.indices),
            materializedStartIndex = header[6].toInt().coerceAtLeast(0),
            materializedEndExclusive = header[7].toInt().coerceAtLeast(0),
            shuffleSeed = header[8].takeUnless { it == NULL_VALUE }?.toLong(),
        )
    }

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
        val mode = PlaybackMode.valueOf(header[3])
        val normalizedLogicalIds = logicalIds.ifEmpty { items.map(PersistedPlaybackItem::mediaId) }
        return PersistedPlaybackSession(
            items = items,
            currentIndex = if (items.isEmpty()) 0 else requestedIndex.coerceIn(items.indices),
            positionMs = header[2].toLong().coerceAtLeast(0),
            playbackMode = mode,
            repeatMode = RepeatMode.valueOf(header[4]),
            logicalMediaIds = normalizedLogicalIds,
            logicalOrigins = normalizedLogicalIds.indices.map { index -> fallbackOrigin(mode, index, logicalIndex) },
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
        val mode = PlaybackMode.valueOf(header[3])
        return PersistedPlaybackSession(
            items = items,
            currentIndex = currentIndex,
            positionMs = header[2].toLong().coerceAtLeast(0),
            playbackMode = mode,
            repeatMode = RepeatMode.valueOf(header[4]),
            logicalMediaIds = items.map(PersistedPlaybackItem::mediaId),
            logicalOrigins = items.indices.map { index -> fallbackOrigin(mode, index, currentIndex) },
            logicalCurrentIndex = if (items.isEmpty()) -1 else currentIndex,
            materializedStartIndex = 0,
            materializedEndExclusive = items.size,
            shuffleSeed = null,
        )
    }

    private fun fallbackOrigin(mode: PlaybackMode, index: Int, currentIndex: Int): QueueItemOrigin =
        if (mode == PlaybackMode.SMART_SHUFFLE && index != currentIndex) QueueItemOrigin.GENERATED else QueueItemOrigin.MANUAL

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
