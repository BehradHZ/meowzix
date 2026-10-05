package dev.behradhz.meowzix.domain.library

import dev.behradhz.meowzix.core.model.Track
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class TrackMetadataOverride(
    val trackId: UUID,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkRef: String? = null,
    val updatedAtEpochMs: Long,
) {
    val isEmpty: Boolean get() = title == null && artist == null && album == null && artworkRef == null
}

enum class DuplicateEvidence { EXACT_CONTENT, METADATA_AND_DURATION }

data class DuplicateCandidate(
    val track: Track,
    val evidence: DuplicateEvidence,
    val sourceLabels: List<String> = emptyList(),
)

data class TrackMergeResult(
    val journalId: UUID,
    val survivorTrackId: UUID,
    val mergedTrackId: UUID,
    val evidence: DuplicateEvidence,
)

data class TrackMergeJournal(
    val id: UUID,
    val survivorTrackId: UUID,
    val mergedTrackId: UUID,
    val createdAtEpochMs: Long,
)

enum class RuleMatchMode { ALL, ANY }
enum class RuleKind { FAVORITE, OFFLINE, ADDED_WITHIN_DAYS, NOT_LISTENED_WITHIN_DAYS, ARTIST_IS, ALBUM_IS }
enum class RulePlaylistSort { RECENTLY_ADDED, TITLE, ARTIST, LAST_PLAYED }

data class PlaylistRule(val kind: RuleKind, val value: String? = null)

data class RulePlaylistDefinition(
    val playlistId: UUID,
    val matchMode: RuleMatchMode,
    val rules: List<PlaylistRule>,
    val sort: RulePlaylistSort,
    val updatedAtEpochMs: Long,
)

data class RulePlaylistRecord(
    val title: String,
    val definition: RulePlaylistDefinition,
)

interface LibraryToolsRepository {
    fun observeMetadataOverrides(): Flow<Map<UUID, TrackMetadataOverride>>
    suspend fun metadataOverride(trackId: UUID): TrackMetadataOverride?
    suspend fun setMetadataOverride(value: TrackMetadataOverride?)
    suspend fun duplicateCandidates(trackId: UUID): List<DuplicateCandidate>

    /**
     * Merge [mergedTrackId] into [survivorTrackId] without deleting either Track row or any audio.
     * Metadata-only matches require an explicit confirmation from the user.
     */
    suspend fun mergeTracks(
        survivorTrackId: UUID,
        mergedTrackId: UUID,
        confirmMetadataOnly: Boolean = false,
    ): TrackMergeResult

    /** Undo a merge created by this version of Meowzix using its provenance journal. */
    suspend fun unmerge(journalId: UUID): Boolean
    suspend fun activeMerges(): List<TrackMergeJournal>

    fun observeRulePlaylists(): Flow<List<RulePlaylistDefinition>>
    fun observeRulePlaylistRecords(): Flow<List<RulePlaylistRecord>>
    fun observeRulePlaylistTracks(playlistId: UUID): Flow<List<Track>>
    suspend fun rulePlaylist(playlistId: UUID): RulePlaylistDefinition?
    suspend fun saveRulePlaylist(definition: RulePlaylistDefinition)
    suspend fun deleteRulePlaylist(playlistId: UUID)
    /** Evaluates a bounded membership snapshot. Playback must use this snapshot for the current queue. */
    suspend fun evaluateRulePlaylist(playlistId: UUID, limit: Int = 500): List<Track>
}

fun Track.withMetadataOverride(override: TrackMetadataOverride?): Track {
    if (override == null) return this
    val effectiveTitle = override.title ?: title
    val effectiveArtist = override.artist ?: artist
    return copy(
        title = effectiveTitle,
        normalizedTitle = dev.behradhz.meowzix.core.common.TextNormalizer.normalize(effectiveTitle) ?: normalizedTitle,
        artist = effectiveArtist,
        normalizedArtist = dev.behradhz.meowzix.core.common.TextNormalizer.normalize(effectiveArtist),
        album = override.album ?: album,
        artworkRef = override.artworkRef ?: artworkRef,
    )
}