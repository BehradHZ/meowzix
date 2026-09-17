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
import dev.behradhz.meowzix.domain.library.LocalLibraryRefreshResult
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LocalMusicLibraryRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val dao: LibraryDao,
    private val scanner: LocalMediaScanner,
) : MusicLibraryRepository {

    override fun observeTracks(): Flow<List<Track>> = dao.observeAvailableLocalTracks().map { rows ->
        rows.map(TrackEntity::toDomain)
    }

    override suspend fun refreshLocalMusic(): LocalLibraryRefreshResult {
        val scanned = scanner.scan()
        val now = Instant.now().toEpochMilli()

        val plan = database.withTransaction {
            val existingSources = dao.allLocalSources()
            val existingSourcesById = existingSources.associateBy { it.id }
            val existingTracksById = dao.allTracksWithLocalSources().associateBy { it.id }
            val snapshots = existingSources.mapNotNull { source ->
                source.contentUri?.let { uri ->
                    ExistingLocalSourceSnapshot(
                        sourceId = source.id,
                        trackId = source.trackId,
                        contentUri = uri,
                    )
                }
            }
            val reconciliation = LocalLibraryReconciler.plan(scanned, snapshots)

            for (item in reconciliation.toCreate) {
                persistScannedItem(item, existingSource = null, existingTrack = null, now = now)
            }

            for (match in reconciliation.toUpdate) {
                val existingSource = existingSourcesById[match.existing.sourceId] ?: continue
                val existingTrack = existingTracksById[match.existing.trackId]
                persistScannedItem(match.scanned, existingSource, existingTrack, now)
            }

            for (sourceId in reconciliation.missingSourceIds) {
                dao.updateAvailability(sourceId, SourceAvailability.MISSING, now)
            }

            reconciliation
        }

        return LocalLibraryRefreshResult(
            discovered = plan.discovered,
            created = plan.toCreate.size,
            updated = plan.toUpdate.size,
            markedMissing = plan.missingSourceIds.size,
        )
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
