package dev.behradhz.meowzix.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.TrackEntity
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Paging-backed track stream for large library surfaces.
 *
 * Keep the existing full-library flow for aggregate views such as Artists/Albums and queue
 * construction. Track-list surfaces can consume this stream without materializing the complete
 * table or composing thousands of rows at once.
 */
@Singleton
class PagedLibraryTracks @Inject constructor(
    private val dao: LibraryDao,
) {
    fun flow(pageSize: Int = DEFAULT_PAGE_SIZE): Flow<PagingData<Track>> =
        Pager(
            config = PagingConfig(
                pageSize = pageSize,
                initialLoadSize = pageSize * 2,
                prefetchDistance = pageSize / 2,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = dao::pagingAvailableTracks,
        ).flow.map { pagingData ->
            pagingData.map(TrackEntity::toPagedTrack)
        }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 80
    }
}

private fun TrackEntity.toPagedTrack() = Track(
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
