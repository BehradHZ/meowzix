package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.data.db.PlaylistTrackEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedback
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import java.time.Instant
import java.util.UUID

internal data class TrackMergeSnapshot(
    val survivorFavorite: Boolean,
    val survivorHidden: Boolean,
    val mergedFavorite: Boolean,
    val mergedHidden: Boolean,
    val survivorOverride: TrackMetadataOverrideEntity?,
    val sourceIds: List<String>,
    val playlistEntries: List<PlaylistSnapshotEntry>,
    val lyricIds: List<String>,
    val historyEventIds: List<String>,
    val survivorFeedback: List<RecommendationFeedback>,
    val mergedFeedback: List<RecommendationFeedback>,
)

internal data class PlaylistSnapshotEntry(
    val entry: PlaylistTrackEntity,
    val survivorWasAlreadyPresent: Boolean,
)

internal object TrackMergeSnapshotCodec {
    private const val CURRENT_VERSION = 2
    private const val LEGACY_VERSION = 1
    private const val LEGACY_NULL = "~"
    private const val NULL_TAG = "N"
    private const val VALUE_TAG = "V"

    fun encode(value: TrackMergeSnapshot): String = buildString {
        line("v", CURRENT_VERSION.toString())
        line(
            "flags",
            value.survivorFavorite.toString(), value.survivorHidden.toString(),
            value.mergedFavorite.toString(), value.mergedHidden.toString(),
        )
        value.survivorOverride?.let { row ->
            line(
                "override", row.trackId, nullable(row.title), nullable(row.artist), nullable(row.album),
                nullable(row.artworkRef), row.updatedAtEpochMs.toString(),
            )
        }
        value.sourceIds.forEach { line("source", it) }
        value.playlistEntries.forEach { row ->
            line(
                "playlist", row.entry.playlistId, row.entry.trackId, row.entry.position.toString(),
                row.entry.addedAtEpochMs.toString(), row.survivorWasAlreadyPresent.toString(),
            )
        }
        value.lyricIds.forEach { line("lyric", it) }
        value.historyEventIds.forEach { line("event", it) }
        value.survivorFeedback.forEach { feedback("feedback-survivor", it) }
        value.mergedFeedback.forEach { feedback("feedback-merged", it) }
    }

    fun decode(raw: String): TrackMergeSnapshot {
        val rows = raw.lineSequence()
            .filter(String::isNotBlank)
            .map { line -> line.split('\t').map(::unescape) }
            .toList()

        val versionRows = rows.filter { it.firstOrNull() == "v" }
        require(versionRows.size == 1 && versionRows.single().size == 2) { "Malformed merge snapshot version" }
        val version = versionRows.single()[1].toIntOrNull()
        require(version == LEGACY_VERSION || version == CURRENT_VERSION) {
            "Unsupported merge snapshot version: $version"
        }

        var survivorFavorite = false
        var survivorHidden = false
        var mergedFavorite = false
        var mergedHidden = false
        var survivorOverride: TrackMetadataOverrideEntity? = null
        val sources = mutableListOf<String>()
        val playlists = mutableListOf<PlaylistSnapshotEntry>()
        val lyrics = mutableListOf<String>()
        val events = mutableListOf<String>()
        val survivorFeedback = mutableListOf<RecommendationFeedback>()
        val mergedFeedback = mutableListOf<RecommendationFeedback>()
        var flagsSeen = false
        var overrideSeen = false

        rows.forEach { f ->
            when (f.firstOrNull()) {
                "v" -> Unit
                "flags" -> {
                    require(!flagsSeen && f.size == 5) { "Malformed merge snapshot flags" }
                    flagsSeen = true
                    survivorFavorite = f[1].toBooleanStrict()
                    survivorHidden = f[2].toBooleanStrict()
                    mergedFavorite = f[3].toBooleanStrict()
                    mergedHidden = f[4].toBooleanStrict()
                }
                "override" -> {
                    require(!overrideSeen && f.size == 7) { "Malformed merge snapshot override" }
                    overrideSeen = true
                    survivorOverride = TrackMetadataOverrideEntity(
                        trackId = f[1],
                        title = denull(f[2], version),
                        artist = denull(f[3], version),
                        album = denull(f[4], version),
                        artworkRef = denull(f[5], version),
                        updatedAtEpochMs = f[6].toLong(),
                    )
                }
                "source" -> {
                    require(f.size == 2) { "Malformed merge snapshot source" }
                    sources += f[1]
                }
                "playlist" -> {
                    require(f.size == 6) { "Malformed merge snapshot playlist" }
                    playlists += PlaylistSnapshotEntry(
                        PlaylistTrackEntity(f[1], f[2], f[3].toInt(), f[4].toLong()),
                        f[5].toBooleanStrict(),
                    )
                }
                "lyric" -> {
                    require(f.size == 2) { "Malformed merge snapshot lyric" }
                    lyrics += f[1]
                }
                "event" -> {
                    require(f.size == 2) { "Malformed merge snapshot event" }
                    events += f[1]
                }
                "feedback-survivor" -> survivorFeedback += decodeFeedback(f, version)
                "feedback-merged" -> mergedFeedback += decodeFeedback(f, version)
                else -> require(false) { "Malformed merge snapshot row" }
            }
        }

        require(flagsSeen) { "Malformed merge snapshot: missing flags" }
        return TrackMergeSnapshot(
            survivorFavorite, survivorHidden, mergedFavorite, mergedHidden, survivorOverride,
            sources.distinct(), playlists, lyrics.distinct(), events.distinct(), survivorFeedback, mergedFeedback,
        )
    }

    private fun StringBuilder.feedback(type: String, value: RecommendationFeedback) = line(
        type,
        value.trackId.toString(), value.action.name, value.createdAt.toEpochMilli().toString(),
        nullable(value.expiresAt?.toEpochMilli()?.toString()),
    )

    private fun decodeFeedback(f: List<String>, version: Int): RecommendationFeedback {
        require(f.size == 5) { "Malformed merge snapshot feedback" }
        return RecommendationFeedback(
            trackId = UUID.fromString(f[1]),
            action = RecommendationFeedbackAction.valueOf(f[2]),
            createdAt = Instant.ofEpochMilli(f[3].toLong()),
            expiresAt = denull(f[4], version)?.toLong()?.let(Instant::ofEpochMilli),
        )
    }

    private fun StringBuilder.line(vararg fields: String) {
        append(fields.joinToString("\t") { escape(it) }).append('\n')
    }

    private fun nullable(value: String?): String = if (value == null) NULL_TAG else VALUE_TAG + value

    private fun denull(value: String, version: Int): String? = when (version) {
        LEGACY_VERSION -> value.takeUnless { it == LEGACY_NULL }
        CURRENT_VERSION -> when {
            value == NULL_TAG -> null
            value.startsWith(VALUE_TAG) -> value.substring(VALUE_TAG.length)
            else -> throw IllegalArgumentException("Malformed nullable merge snapshot field")
        }
        else -> throw IllegalArgumentException("Unsupported merge snapshot version: $version")
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '%' -> append("%25")
                '~' -> append("%7E")
                '\t' -> append("%09")
                '\n' -> append("%0A")
                '\r' -> append("%0D")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch != '%') {
                append(ch)
                index += 1
                continue
            }
            require(index + 2 < value.length) { "Malformed merge snapshot escape" }
            when (value.substring(index, index + 3)) {
                "%25" -> append('%')
                "%7E" -> append('~')
                "%09" -> append('\t')
                "%0A" -> append('\n')
                "%0D" -> append('\r')
                else -> throw IllegalArgumentException("Malformed merge snapshot escape")
            }
            index += 3
        }
    }
}
