package dev.behradhz.meowzix.domain.library

import dev.behradhz.meowzix.core.model.Track
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class LocalLibraryRefreshResult(
    val discovered: Int,
    val created: Int,
    val updated: Int,
    val markedMissing: Int,
)

enum class LibraryTrackAvailability { OFFLINE, CLOUD, UNAVAILABLE }

data class LibraryTrack(
    val track: Track,
    val availability: LibraryTrackAvailability,
)

interface MusicLibraryRepository {
    fun observeTracks(): Flow<List<Track>>
    fun observeLibraryTracks(): Flow<List<LibraryTrack>>
    suspend fun shouldRefreshLocalMusic(): Boolean = true
    suspend fun refreshLocalMusic(): LocalLibraryRefreshResult
    suspend fun unmergeSource(sourceId: UUID): UUID
    suspend fun setFavorite(trackId: UUID, favorite: Boolean)

    /** Opportunistically fills missing low-resolution cover art without downloading audio files. */
    fun prefetchArtwork(trackIds: List<UUID>) = Unit
}
