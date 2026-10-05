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
    private const val VERSION = 1
    private const val NULL = "~"

    fun encode(value: TrackMergeSnapshot): String = buildString {
        line("v", VERSION.toString())
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
        var version: Int? = null

        raw.lineSequence().filter(String::isNotBlank).forEach { line ->
            val f = line.split('\t').map(::unescape)
            when (f.firstOrNull()) {
                "v" -> version = f.getOrNull(1)?.toIntOrNull()
                "flags" -> {
                    survivorFavorite = f.required(1).toBooleanStrict()
                    survivorHidden = f.required(2).toBooleanStrict()
                    mergedFavorite = f.required(3).toBooleanStrict()
                    mergedHidden = f.required(4).toBooleanStrict()
                }
                "override" -> survivorOverride = TrackMetadataOverrideEntity(
                    trackId = f.required(1), title = denull(f.required(2)), artist = denull(f.required(3)),
                    album = denull(f.required(4)), artworkRef = denull(f.required(5)),
                    updatedAtEpochMs = f.required(6).toLong(),
                )
                "source" -> sources += f.required(1)
                "playlist" -> playlists += PlaylistSnapshotEntry(
                    PlaylistTrackEntity(f.required(1), f.required(2), f.required(3).toInt(), f.required(4).toLong()),
                    f.required(5).toBooleanStrict(),
                )
                "lyric" -> lyrics += f.required(1)
                "event" -> events += f.required(1)
                "feedback-survivor" -> survivorFeedback += decodeFeedback(f)
                "feedback-merged" -> mergedFeedback += decodeFeedback(f)
            }
        }
        require(version == VERSION) { "Unsupported merge snapshot version: $version" }
        return TrackMergeSnapshot(
            survivorFavorite, survivorHidden, mergedFavorite, mergedHidden, survivorOverride,
            sources.distinct(), playlists, lyrics.distinct(), events.distinct(), survivorFeedback, mergedFeedback,
        )
    }

    private fun StringBuilder.feedback(type: String, value: RecommendationFeedback) = line(
        type,
        value.trackId.toString(), value.action.name, value.createdAt.toEpochMilli().toString(),
        value.expiresAt?.toEpochMilli()?.toString() ?: NULL,
    )

    private fun decodeFeedback(f: List<String>): RecommendationFeedback = RecommendationFeedback(
        trackId = UUID.fromString(f.required(1)),
        action = RecommendationFeedbackAction.valueOf(f.required(2)),
        createdAt = Instant.ofEpochMilli(f.required(3).toLong()),
        expiresAt = f.required(4).takeUnless { it == NULL }?.toLong()?.let(Instant::ofEpochMilli),
    )

    private fun StringBuilder.line(vararg fields: String) {
        append(fields.joinToString("\t") { escape(it) }).append('\n')
    }

    private fun nullable(value: String?): String = value ?: NULL
    private fun denull(value: String): String? = value.takeUnless { it == NULL }
    private fun List<String>.required(index: Int): String = getOrNull(index) ?: error("Malformed merge snapshot")

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

    private fun unescape(value: String): String {
        var result = value
        result = result.replace("%0D", "\r")
            .replace("%0A", "\n")
            .replace("%09", "\t")
            .replace("%7E", "~")
            .replace("%25", "%")
        return result
    }
}