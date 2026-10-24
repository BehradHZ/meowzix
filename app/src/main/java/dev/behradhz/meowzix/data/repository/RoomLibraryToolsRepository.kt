package dev.behradhz.meowzix.data.repository

import androidx.room.withTransaction
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LibraryToolsDao
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackMergeJournalEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer
import dev.behradhz.meowzix.domain.library.DuplicateCandidate
import dev.behradhz.meowzix.domain.library.DuplicateEvidence
import dev.behradhz.meowzix.domain.library.LibraryToolsRepository
import dev.behradhz.meowzix.domain.library.TrackMergeJournal
import dev.behradhz.meowzix.domain.library.TrackMergeResult
import dev.behradhz.meowzix.domain.library.TrackMetadataOverride
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedback
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomLibraryToolsRepository @Inject constructor(
    private val toolsDao: LibraryToolsDao,
    private val libraryDao: LibraryDao,
    private val database: MeowzixDatabase,
    private val feedbackRepository: RecommendationFeedbackRepository,
    private val personalizationTrainer: PersonalizationTrainer,
    private val searchIndexer: EffectiveTrackSearchIndexer,
    private val clock: LibraryToolsClock,
) : LibraryToolsRepository {

    override fun observeMetadataOverrides(): Flow<Map<UUID, TrackMetadataOverride>> =
        toolsDao.observeMetadataOverrides().map { rows ->
            rows.associate { entity -> UUID.fromString(entity.trackId) to entity.toDomain() }
        }

    override suspend fun metadataOverride(trackId: UUID): TrackMetadataOverride? =
        toolsDao.metadataOverride(trackId.toString())?.toDomain()

    override suspend fun setMetadataOverride(value: TrackMetadataOverride?) {
        if (value == null) return
        val trackId = value.trackId.toString()
        if (value.isEmpty) {
            toolsDao.deleteMetadataOverride(trackId)
            searchIndexer.refreshTrack(trackId)
            return
        }
        toolsDao.upsertMetadataOverride(
            TrackMetadataOverrideEntity(
                trackId = trackId,
                title = value.title.cleaned(),
                artist = value.artist.cleaned(),
                album = value.album.cleaned(),
                artworkRef = value.artworkRef.cleaned(maxLength = 4_096),
                updatedAtEpochMs = value.updatedAtEpochMs,
            ),
        )
        searchIndexer.refreshTrack(trackId)
    }

    override suspend fun duplicateCandidates(trackId: UUID): List<DuplicateCandidate> {
        val id = trackId.toString()
        val track = libraryDao.trackById(id) ?: return emptyList()
        val exactIds = libraryDao.sourcesForTrack(id)
            .mapNotNull { it.contentHashSha256 }
            .distinct()
            .flatMap { libraryDao.matchingTracksByContentHash(it) }
            .map { it.trackId }
            .filterNot { it == id }
            .toSet()

        val tolerance = max(2_000L, track.durationMs / 50L)
        val metadataIds = track.normalizedArtist?.let { artist ->
            libraryDao.matchingTrackCandidates(
                normalizedTitle = track.normalizedTitle,
                normalizedArtist = artist,
                minDurationMs = (track.durationMs - tolerance).coerceAtLeast(0L),
                maxDurationMs = track.durationMs + tolerance,
            ).map { it.id }.filterNot { it == id }.toSet()
        }.orEmpty()

        return (exactIds + metadataIds).mapNotNull { candidateId ->
            libraryDao.trackById(candidateId)?.let { entity ->
                DuplicateCandidate(
                    track = entity.toDomain(),
                    evidence = if (candidateId in exactIds) DuplicateEvidence.EXACT_CONTENT else DuplicateEvidence.METADATA_AND_DURATION,
                    sourceLabels = libraryDao.sourcesForTrack(candidateId)
                        .map { source -> "${source.type.name.lowercase()} · ${source.availability.name.lowercase()}" }
                        .distinct()
                        .sorted(),
                )
            }
        }.sortedWith(compareBy({ it.evidence != DuplicateEvidence.EXACT_CONTENT }, { it.track.normalizedTitle }))
    }

    override suspend fun mergeTracks(
        survivorTrackId: UUID,
        mergedTrackId: UUID,
        confirmMetadataOnly: Boolean,
    ): TrackMergeResult {
        require(survivorTrackId != mergedTrackId) { "A track cannot be merged into itself" }
        val survivorId = survivorTrackId.toString()
        val mergedId = mergedTrackId.toString()
        val survivor = requireNotNull(libraryDao.trackById(survivorId)) { "Survivor track does not exist" }
        val merged = requireNotNull(libraryDao.trackById(mergedId)) { "Merged track does not exist" }
        require(!survivor.hidden && !merged.hidden) { "Only visible canonical tracks can be merged" }
        val active = toolsDao.activeMergeJournal()
        require(active.none { row ->
            row.survivorTrackId in setOf(survivorId, mergedId) || row.mergedTrackId in setOf(survivorId, mergedId)
        }) { "Nested or repeated merges must be undone before merging again" }

        val evidence = duplicateCandidates(survivorTrackId).firstOrNull { it.track.id == mergedTrackId }?.evidence
            ?: duplicateCandidates(mergedTrackId).firstOrNull { it.track.id == survivorTrackId }?.evidence
            ?: error("Tracks are not a duplicate candidate pair")
        require(evidence != DuplicateEvidence.METADATA_AND_DURATION || confirmMetadataOnly) {
            "Metadata-only duplicate merges require explicit confirmation"
        }

        val playlistDao = database.playlistDao()
        val lyricsDao = database.lyricsDao()
        val mergeDao = database.trackMergeDao()
        val survivorOverride = toolsDao.metadataOverride(survivorId)
        val mergedOverride = toolsDao.metadataOverride(mergedId)
        val mergedPlaylistEntries = playlistDao.entriesForTrack(mergedId)
        val mergedFeedback = feedbackRepository.snapshot().filter { it.trackId == mergedTrackId }
        val survivorFeedback = feedbackRepository.snapshot().filter { it.trackId == survivorTrackId }
        val snapshot = TrackMergeSnapshot(
            survivorFavorite = survivor.favorite,
            survivorHidden = survivor.hidden,
            mergedFavorite = merged.favorite,
            mergedHidden = merged.hidden,
            survivorOverride = survivorOverride,
            sourceIds = libraryDao.sourcesForTrack(mergedId).map { it.id },
            playlistEntries = mergedPlaylistEntries.map { entry ->
                PlaylistSnapshotEntry(entry, playlistDao.entry(entry.playlistId, survivorId) != null)
            },
            lyricIds = lyricsDao.versionsForTrack(mergedId).map { it.id },
            historyEventIds = mergeDao.eventIdsForTrack(mergedId),
            survivorFeedback = survivorFeedback,
            mergedFeedback = mergedFeedback,
        )
        val now = clock.millis()
        val journalId = UUID.randomUUID()

        database.withTransaction {
            toolsDao.upsertMergeJournal(
                TrackMergeJournalEntity(
                    id = journalId.toString(),
                    survivorTrackId = survivorId,
                    mergedTrackId = mergedId,
                    snapshotJson = TrackMergeSnapshotCodec.encode(snapshot),
                    createdAtEpochMs = now,
                    reversedAtEpochMs = null,
                ),
            )

            snapshot.sourceIds.forEach { sourceId ->
                libraryDao.sourceById(sourceId)?.takeIf { it.trackId == mergedId }?.let {
                    libraryDao.moveSource(sourceId, survivorId)
                }
            }

            snapshot.playlistEntries.forEach { row ->
                playlistDao.removeTrack(row.entry.playlistId, mergedId)
                if (!row.survivorWasAlreadyPresent) {
                    playlistDao.insertTrack(row.entry.copy(trackId = survivorId))
                }
                playlistDao.touchPlaylist(row.entry.playlistId, now)
            }

            snapshot.lyricIds.forEach { lyricsDao.moveVersion(it, mergedId, survivorId) }
            snapshot.historyEventIds.forEach { mergeDao.moveEvent(it, mergedId, survivorId) }

            libraryDao.upsertTrack(survivor.copy(favorite = survivor.favorite || merged.favorite, updatedAtEpochMs = now))
            libraryDao.upsertTrack(merged.copy(hidden = true, updatedAtEpochMs = now))

            val combinedOverride = combineOverrides(survivorId, survivorOverride, mergedOverride, now)
            if (combinedOverride != null) toolsDao.upsertMetadataOverride(combinedOverride)

            // Audio features remain attached to their original logical Track row. While the merge
            // is active, TrainingDatasetBuilder canonicalizes the merged ID to the survivor; after
            // unmerge the same vectors become valid for the restored identity again.
            database.historyDao().rebuildPreferenceStats(0L)
        }

        mergeFeedback(survivorTrackId, mergedTrackId, survivorFeedback, mergedFeedback)
        searchIndexer.refreshTracks(listOf(survivorId, mergedId))
        personalizationTrainer.rebuildFromStoredHistory()
        return TrackMergeResult(journalId, survivorTrackId, mergedTrackId, evidence)
    }

    override suspend fun unmerge(journalId: UUID): Boolean {
        val journal = toolsDao.mergeJournal(journalId.toString()) ?: return false
        if (journal.reversedAtEpochMs != null) return false
        val snapshot = runCatching { TrackMergeSnapshotCodec.decode(journal.snapshotJson) }.getOrElse { return false }
        val survivorId = journal.survivorTrackId
        val mergedId = journal.mergedTrackId
        val survivor = libraryDao.trackById(survivorId) ?: return false
        val merged = libraryDao.trackById(mergedId) ?: return false
        val playlistDao = database.playlistDao()
        val lyricsDao = database.lyricsDao()
        val mergeDao = database.trackMergeDao()
        val now = clock.millis()

        database.withTransaction {
            snapshot.sourceIds.forEach { sourceId ->
                libraryDao.sourceById(sourceId)?.takeIf { it.trackId == survivorId }?.let {
                    libraryDao.moveSource(sourceId, mergedId)
                }
            }

            snapshot.playlistEntries.forEach { row ->
                val currentSurvivor = playlistDao.entry(row.entry.playlistId, survivorId)
                if (!row.survivorWasAlreadyPresent && currentSurvivor?.addedAtEpochMs == row.entry.addedAtEpochMs) {
                    playlistDao.removeTrack(row.entry.playlistId, survivorId)
                }
                if (playlistDao.entry(row.entry.playlistId, mergedId) == null) {
                    val current = playlistDao.entries(row.entry.playlistId)
                    val occupied = current.map { it.position }.toSet()
                    val restoredPosition = if (row.entry.position !in occupied) row.entry.position else nextFreePosition(occupied, row.entry.position)
                    playlistDao.insertTrack(row.entry.copy(trackId = mergedId, position = restoredPosition))
                }
                playlistDao.touchPlaylist(row.entry.playlistId, now)
            }

            snapshot.lyricIds.forEach { id ->
                lyricsDao.byId(id)?.takeIf { it.trackId == survivorId }?.let { lyricsDao.moveVersion(id, survivorId, mergedId) }
            }
            snapshot.historyEventIds.forEach { mergeDao.moveEvent(it, survivorId, mergedId) }

            libraryDao.upsertTrack(
                survivor.copy(
                    favorite = snapshot.survivorFavorite,
                    hidden = snapshot.survivorHidden,
                    updatedAtEpochMs = now,
                ),
            )
            libraryDao.upsertTrack(
                merged.copy(
                    favorite = snapshot.mergedFavorite,
                    hidden = snapshot.mergedHidden,
                    updatedAtEpochMs = now,
                ),
            )

            toolsDao.deleteMetadataOverride(survivorId)
            snapshot.survivorOverride?.let { toolsDao.upsertMetadataOverride(it) }
            database.historyDao().rebuildPreferenceStats(0L)
            check(toolsDao.markMergeReversed(journal.id, now) == 1) { "Merge was already reversed" }
        }

        restoreFeedback(UUID.fromString(survivorId), snapshot.survivorFeedback)
        restoreFeedback(UUID.fromString(mergedId), snapshot.mergedFeedback)
        searchIndexer.refreshTracks(listOf(survivorId, mergedId))
        personalizationTrainer.rebuildFromStoredHistory()
        return true
    }

    override suspend fun activeMerges(): List<TrackMergeJournal> = toolsDao.activeMergeJournal().mapNotNull { row ->
        runCatching {
            TrackMergeJournal(
                id = UUID.fromString(row.id),
                survivorTrackId = UUID.fromString(row.survivorTrackId),
                mergedTrackId = UUID.fromString(row.mergedTrackId),
                createdAtEpochMs = row.createdAtEpochMs,
            )
        }.getOrNull()
    }

    private suspend fun mergeFeedback(
        survivorId: UUID,
        mergedId: UUID,
        survivorRows: List<RecommendationFeedback>,
        mergedRows: List<RecommendationFeedback>,
    ) {
        val survivorPreference = survivorRows.filter { it.action != RecommendationFeedbackAction.SNOOZE }.maxByOrNull { it.createdAt }
        val mergedPreference = mergedRows.filter { it.action != RecommendationFeedbackAction.SNOOZE }.maxByOrNull { it.createdAt }
        val preference = survivorPreference ?: mergedPreference
        val snooze = (survivorRows + mergedRows)
            .filter { it.action == RecommendationFeedbackAction.SNOOZE }
            .maxByOrNull { it.expiresAt ?: it.createdAt }
        feedbackRepository.undo(survivorId)
        feedbackRepository.undo(mergedId)
        preference?.let { putFeedback(survivorId, it) }
        snooze?.let { putFeedback(survivorId, it) }
    }

    private suspend fun restoreFeedback(trackId: UUID, rows: List<RecommendationFeedback>) {
        feedbackRepository.undo(trackId)
        rows.sortedBy { it.createdAt }.forEach { putFeedback(trackId, it) }
    }

    private suspend fun putFeedback(trackId: UUID, row: RecommendationFeedback) {
        val duration = row.expiresAt?.let { Duration.between(row.createdAt, it) }
            ?.coerceAtLeast(Duration.ofMinutes(1)) ?: RecommendationFeedback.DEFAULT_SNOOZE
        feedbackRepository.set(trackId, row.action, row.createdAt, duration)
    }

    private fun combineOverrides(
        survivorId: String,
        survivor: TrackMetadataOverrideEntity?,
        merged: TrackMetadataOverrideEntity?,
        now: Long,
    ): TrackMetadataOverrideEntity? {
        if (survivor == null && merged == null) return null
        return TrackMetadataOverrideEntity(
            trackId = survivorId,
            title = survivor?.title ?: merged?.title,
            artist = survivor?.artist ?: merged?.artist,
            album = survivor?.album ?: merged?.album,
            artworkRef = survivor?.artworkRef ?: merged?.artworkRef,
            updatedAtEpochMs = now,
        )
    }

}

private fun nextFreePosition(occupied: Set<Int>, preferred: Int): Int {
    var candidate = preferred.coerceAtLeast(0)
    while (candidate in occupied) candidate++
    return candidate
}

private fun String?.cleaned(maxLength: Int = 512): String? = this?.trim()?.take(maxLength)?.takeIf(String::isNotBlank)

private fun TrackMetadataOverrideEntity.toDomain() = TrackMetadataOverride(
    trackId = UUID.fromString(trackId),
    title = title,
    artist = artist,
    album = album,
    artworkRef = artworkRef,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun TrackEntity.toDomain() = Track(
    id = UUID.fromString(id),
    title = title,
    normalizedTitle = normalizedTitle,
    artist = artist,
    normalizedArtist = normalizedArtist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    artworkRef = artworkRef,
    favorite = favorite,
    hidden = hidden,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)
