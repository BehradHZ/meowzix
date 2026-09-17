package com.behradhz.meowzix.data.repository

import androidx.room.withTransaction
import com.behradhz.meowzix.core.model.SourceAvailability
import com.behradhz.meowzix.core.model.Track
import com.behradhz.meowzix.core.model.TrackSourceType
import com.behradhz.meowzix.data.db.MeowzixDatabase
import com.behradhz.meowzix.data.db.dao.LocalMediaSourceDao
import com.behradhz.meowzix.data.db.dao.TrackDao
import com.behradhz.meowzix.data.db.dao.TrackSourceDao
import com.behradhz.meowzix.data.db.entity.LocalMediaSourceEntity
import com.behradhz.meowzix.data.db.entity.TrackEntity
import com.behradhz.meowzix.data.db.entity.TrackSourceEntity
import com.behradhz.meowzix.data.db.toDomain
import com.behradhz.meowzix.data.localmedia.LocalLibraryReconciliationPlanner
import com.behradhz.meowzix.data.localmedia.LocalMusicScanner
import com.behradhz.meowzix.data.localmedia.ScannedLocalTrack
import com.behradhz.meowzix.domain.library.LocalLibraryRefreshResult
import com.behradhz.meowzix.domain.library.MusicLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultMusicLibraryRepository @Inject constructor(
    private val database: MeowzixDatabase,
    private val trackDao: TrackDao,
    private val trackSourceDao: TrackSourceDao,
    private val localMediaSourceDao: LocalMediaSourceDao,
    private val scanner: LocalMusicScanner,
    private val clock: Clock,
) : MusicLibraryRepository {

    override val tracks: Flow<List<Track>> = trackDao
        .observeVisibleTracks()
        .map { entities -> entities.map(TrackEntity::toDomain) }

    override suspend fun refreshLocalMusic(): LocalLibraryRefreshResult {
        // Scan before opening the DB transaction. A permission/query failure throws and therefore
        // cannot be mistaken for an authoritative empty library.
        val scanned = scanner.scan()
        val existing = localMediaSourceDao.getAll()
        val plan = LocalLibraryReconciliationPlanner.plan(scanned, existing)
        val existingByUri = existing.associateBy { it.contentUri }
        val now = clock.millis()

        var added = 0
        var updated = 0
        var removed = 0

        database.withTransaction {
            for (item in plan.discovered) {
                val existingLocal = existingByUri[item.contentUri]
                if (existingLocal == null) {
                    insertNewLocalTrack(item, now)
                    added += 1
                } else {
                    val didUpdate = updateExistingLocalTrack(existingLocal, item, now)
                    if (didUpdate) updated += 1
                }
            }

            for (stale in plan.staleSources) {
                val source = trackSourceDao.findById(stale.trackSourceId)
                trackSourceDao.deleteById(stale.trackSourceId)
                source?.let {
                    if (trackSourceDao.countForTrack(it.trackId) == 0) {
                        trackDao.deleteById(it.trackId)
                    }
                }
                removed += 1
            }
        }

        return LocalLibraryRefreshResult(
            scanned = scanned.size,
            added = added,
            updated = updated,
            removed = removed,
        )
    }

    private suspend fun insertNewLocalTrack(
        item: ScannedLocalTrack,
        now: Long,
    ) {
        val trackId = UUID.randomUUID().toString()
        val sourceId = UUID.randomUUID().toString()

        trackDao.upsert(item.toTrackEntity(trackId = trackId, createdAt = now, updatedAt = now))
        trackSourceDao.upsert(item.toTrackSourceEntity(sourceId = sourceId, trackId = trackId, now = now))
        localMediaSourceDao.upsert(item.toLocalMediaSourceEntity(sourceId))
    }

    private suspend fun updateExistingLocalTrack(
        existingLocal: LocalMediaSourceEntity,
        item: ScannedLocalTrack,
        now: Long,
    ): Boolean {
        val existingSource = trackSourceDao.findById(existingLocal.trackSourceId)
        val existingTrack = existingSource?.let { trackDao.findById(it.trackId) }

        // Foreign keys make this unlikely, but self-heal if a partially corrupted record exists.
        if (existingSource == null || existingTrack == null) {
            trackSourceDao.deleteById(existingLocal.trackSourceId)
            insertNewLocalTrack(item, now)
            return true
        }

        val candidateTrack = item.toTrackEntity(
            trackId = existingTrack.id,
            createdAt = existingTrack.createdAtEpochMs,
            updatedAt = existingTrack.updatedAtEpochMs,
            favorite = existingTrack.favorite,
            hidden = existingTrack.hidden,
        )
        val trackChanged = candidateTrack != existingTrack
        if (trackChanged) {
            trackDao.upsert(candidateTrack.copy(updatedAtEpochMs = now))
        }

        val candidateSource = item.toTrackSourceEntity(
            sourceId = existingSource.id,
            trackId = existingSource.trackId,
            now = now,
            createdAt = existingSource.createdAtEpochMs,
        )
        val sourceMeaningfullyChanged = candidateSource.copy(lastVerifiedAtEpochMs = existingSource.lastVerifiedAtEpochMs) != existingSource
        trackSourceDao.upsert(candidateSource)

        val candidateLocal = item.toLocalMediaSourceEntity(existingLocal.trackSourceId)
        val localChanged = candidateLocal != existingLocal
        if (localChanged) {
            localMediaSourceDao.upsert(candidateLocal)
        }

        return trackChanged || sourceMeaningfullyChanged || localChanged
    }

    private fun ScannedLocalTrack.toTrackEntity(
        trackId: String,
        createdAt: Long,
        updatedAt: Long,
        favorite: Boolean = false,
        hidden: Boolean = false,
    ): TrackEntity = TrackEntity(
        id = trackId,
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
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    )

    private fun ScannedLocalTrack.toTrackSourceEntity(
        sourceId: String,
        trackId: String,
        now: Long,
        createdAt: Long = now,
    ): TrackSourceEntity = TrackSourceEntity(
        id = sourceId,
        trackId = trackId,
        type = TrackSourceType.LOCAL_MEDIASTORE,
        availability = SourceAvailability.AVAILABLE_LOCAL,
        contentUri = contentUri,
        localPath = null,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes,
        contentHashSha256 = null,
        trainingEligible = true,
        createdAtEpochMs = createdAt,
        lastVerifiedAtEpochMs = now,
    )

    private fun ScannedLocalTrack.toLocalMediaSourceEntity(sourceId: String): LocalMediaSourceEntity =
        LocalMediaSourceEntity(
            trackSourceId = sourceId,
            mediaStoreId = mediaStoreId,
            contentUri = contentUri,
            relativePath = relativePath,
            displayName = displayName,
            dateModifiedEpochSeconds = dateModifiedEpochSeconds,
        )
}
