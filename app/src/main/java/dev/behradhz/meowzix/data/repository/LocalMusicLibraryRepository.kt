package dev.behradhz.meowzix.data.repository

import androidx.room.withTransaction
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LocalMediaSourceEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.localmedia.LocalMediaScanner
import dev.behradhz.meowzix.data.localmedia.ScannedLocalTrack
import dev.behradhz.meowzix.domain.library.LibraryTrack
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.LocalLibraryRefreshResult
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class LocalMusicLibraryRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val dao: LibraryDao,
    private val scanner: LocalMediaScanner,
) : MusicLibraryRepository, PlaybackCatalog {
    private var lastSuccessfulRefreshAtEpochMs: Long? = null

    override fun observeTracks(): Flow<List<Track>> = dao.observeAvailableTracks()
        .map { rows -> rows.map(TrackEntity::toDomain) }
        .flowOn(Dispatchers.Default)

    override fun observeLibraryTracks(): Flow<List<LibraryTrack>> =
        combine(dao.observeAvailableTracks(), dao.observeActiveSources()) { tracks, sources ->
            val sourcesByTrack = sources.groupBy { it.trackId }
            tracks.map { entity ->
                val trackSources = sourcesByTrack[entity.id].orEmpty()
                val availability = when {
                    trackSources.any { it.availability == SourceAvailability.AVAILABLE_LOCAL } -> LibraryTrackAvailability.OFFLINE
                    trackSources.any { it.type == TrackSourceType.TELEGRAM_REMOTE && it.availability == SourceAvailability.REMOTE_ONLY } -> LibraryTrackAvailability.CLOUD
                    else -> LibraryTrackAvailability.UNAVAILABLE
                }
                LibraryTrack(entity.toDomain(), availability)
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun shouldRefreshLocalMusic(): Boolean {
        val dbVerifiedAt = dao.latestLocalVerificationEpochMs() ?: 0L
        val lastVerifiedAt = maxOf(dbVerifiedAt, lastSuccessfulRefreshAtEpochMs ?: 0L)
        if (lastVerifiedAt == 0L) return true
        return Instant.now().toEpochMilli() - lastVerifiedAt >= AUTO_REFRESH_INTERVAL_MS
    }

    override suspend fun refreshLocalMusic(): LocalLibraryRefreshResult {
        val scanned = scanner.scan()
        val now = Instant.now().toEpochMilli()
        var updatedCount = 0

        val plan = database.withTransaction {
            val existingSources = dao.allLocalSources()
            val existingSourcesById = existingSources.associateBy { it.id }
            val existingTracksById = dao.allTracksWithLocalSources().associateBy { it.id }
            val existingLocalMediaBySourceId = dao.allLocalMediaSources().associateBy { it.trackSourceId }
            val snapshots = existingSources.mapNotNull { source ->
                source.contentUri?.let { uri ->
                    ExistingLocalSourceSnapshot(source.id, source.trackId, uri)
                }
            }
            val reconciliation = LocalLibraryReconciler.plan(scanned, snapshots)

            for (item in reconciliation.toCreate) persistScannedItem(item, null, null, now)

            val unchangedSourceIds = mutableListOf<String>()
            for (match in reconciliation.toUpdate) {
                val existingSource = existingSourcesById[match.existing.sourceId] ?: continue
                val existingTrack = existingTracksById[match.existing.trackId]
                val existingLocalMedia = existingLocalMediaBySourceId[existingSource.id]
                if (isUnchanged(match.scanned, existingSource, existingTrack, existingLocalMedia)) {
                    unchangedSourceIds += existingSource.id
                } else {
                    persistScannedItem(match.scanned, existingSource, existingTrack, now)
                    updatedCount++
                }
            }

            updateAvailabilityInChunks(unchangedSourceIds, SourceAvailability.AVAILABLE_LOCAL, now)
            updateAvailabilityInChunks(reconciliation.missingSourceIds, SourceAvailability.MISSING, now)
            reconciliation
        }

        lastSuccessfulRefreshAtEpochMs = now
        return LocalLibraryRefreshResult(
            discovered = plan.discovered,
            created = plan.toCreate.size,
            updated = updatedCount,
            markedMissing = plan.missingSourceIds.size,
        )
    }

    override suspend fun availableLocalTracks(): List<PlayableTrack> {
        val rows = dao.availableLocalPlaybackRows()
        return withContext(Dispatchers.Default) {
            rows.distinctBy { it.id }.map { row ->
                PlayableTrack(
                    id = UUID.fromString(row.id),
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    durationMs = row.durationMs,
                    artworkRef = row.artworkRef,
                    contentUri = row.contentUri,
                )
            }
        }
    }

    private suspend fun updateAvailabilityInChunks(
        sourceIds: List<String>,
        availability: SourceAvailability,
        verifiedAt: Long,
    ) {
        sourceIds.chunked(AVAILABILITY_UPDATE_CHUNK_SIZE).forEach { chunk ->
            dao.updateAvailability(chunk, availability, verifiedAt)
        }
    }

    private suspend fun persistScannedItem(
        item: ScannedLocalTrack,
        existingSource: TrackSourceEntity?,
        existingTrack: TrackEntity?,
        now: Long,
    ) {
        val trackId = existingTrack?.id ?: existingSource?.trackId ?: UUID.randomUUID().toString()
        val sourceId = existingSource?.id ?: UUID.randomUUID().toString()

        dao.upsertTrack(
            TrackEntity(
                id = trackId,
                title = item.title,
                normalizedTitle = TextNormalizer.normalize(item.title) ?: "unknown track",
                artist = item.artist,
                normalizedArtist = TextNormalizer.normalize(item.artist),
                album = item.album,
                durationMs = item.durationMs,
                trackNumber = item.trackNumber,
                year = item.year,
                artworkRef = item.artworkRef,
                favorite = existingTrack?.favorite ?: false,
                hidden = existingTrack?.hidden ?: false,
                createdAtEpochMs = existingTrack?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
            ),
        )
        dao.upsertSource(
            TrackSourceEntity(
                id = sourceId,
                trackId = trackId,
                type = TrackSourceType.LOCAL_MEDIASTORE,
                availability = SourceAvailability.AVAILABLE_LOCAL,
                contentUri = item.contentUri,
                localPath = null,
                mimeType = item.mimeType,
                fileSizeBytes = item.fileSizeBytes,
                contentHashSha256 = existingSource?.contentHashSha256,
                trainingEligible = existingSource?.trainingEligible ?: true,
                createdAtEpochMs = existingSource?.createdAtEpochMs ?: now,
                lastVerifiedAtEpochMs = now,
            ),
        )
        dao.upsertLocalMediaSource(
            LocalMediaSourceEntity(
                trackSourceId = sourceId,
                mediaStoreId = item.mediaStoreId,
                contentUri = item.contentUri,
                relativePath = item.relativePath,
                displayName = item.displayName,
                dateModifiedSeconds = item.dateModifiedSeconds,
            ),
        )
    }

    private fun isUnchanged(
        item: ScannedLocalTrack,
        source: TrackSourceEntity,
        track: TrackEntity?,
        localMedia: LocalMediaSourceEntity?,
    ): Boolean {
        if (track == null || localMedia == null) return false
        return source.availability == SourceAvailability.AVAILABLE_LOCAL &&
            source.contentUri == item.contentUri &&
            source.mimeType == item.mimeType &&
            source.fileSizeBytes == item.fileSizeBytes &&
            localMedia.mediaStoreId == item.mediaStoreId &&
            localMedia.contentUri == item.contentUri &&
            localMedia.relativePath == item.relativePath &&
            localMedia.displayName == item.displayName &&
            localMedia.dateModifiedSeconds == item.dateModifiedSeconds &&
            track.title == item.title &&
            track.artist == item.artist &&
            track.album == item.album &&
            track.durationMs == item.durationMs &&
            track.trackNumber == item.trackNumber &&
            track.year == item.year &&
            track.artworkRef == item.artworkRef
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val AVAILABILITY_UPDATE_CHUNK_SIZE = 500
    }
}

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
