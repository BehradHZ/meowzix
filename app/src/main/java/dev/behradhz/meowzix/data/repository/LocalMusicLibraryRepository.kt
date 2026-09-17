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
import dev.behradhz.meowzix.data.localmedia.MediaStoreScanner
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
    private val scanner: MediaStoreScanner,
) : MusicLibraryRepository {

    override fun observeTracks(): Flow<List<Track>> = dao.observeAvailableLocalTracks().map { rows ->
        rows.map(TrackEntity::toDomain)
    }

    override suspend fun refreshLocalMusic(): LocalLibraryRefreshResult {
        val scanned = scanner.scan()
        val now = Instant.now().toEpochMilli()
        var created = 0
        var updated = 0
        var missing = 0

        database.withTransaction {
            val existingByUri = dao.allLocalSources().associateBy { it.contentUri }
            val seenUris = HashSet<String>(scanned.size)

            scanned.forEach { item ->
                seenUris += item.contentUri
                val existingSource = existingByUri[item.contentUri]
                val existingTrack = existingSource?.let { dao.trackById(it.trackId) }
                val trackId = existingTrack?.id ?: UUID.randomUUID().toString()
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
                if (existingSource == null) created++ else updated++
            }

            existingByUri.values
                .asSequence()
                .filter { it.contentUri != null && it.contentUri !in seenUris }
                .forEach {
                    dao.updateAvailability(it.id, SourceAvailability.MISSING, now)
                    missing++
                }
        }

        return LocalLibraryRefreshResult(
            discovered = scanned.size,
            created = created,
            updated = updated,
            markedMissing = missing,
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
