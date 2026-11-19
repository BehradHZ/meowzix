package dev.behradhz.meowzix.domain.library

import dev.behradhz.meowzix.core.model.Track
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

/** Small, reactive projection for Queue rows; playback metadata comes from QueueItem. */
data class QueueTrackStatus(
    val favorite: Boolean,
    val availability: LibraryTrackAvailability,
)

interface MusicLibraryRepository {
    fun observeTracks(): Flow<List<Track>>
    fun observeLibraryTracks(): Flow<List<LibraryTrack>>
    /** Only tracks with an available device MediaStore source; not Telegram downloads/cache. */
    fun observeLocalTracks(): Flow<List<Track>> = flowOf(emptyList())
    fun observeLocalTrackCount(): Flow<Int> = flowOf(0)
    /** Observe Favorite and availability only for the IDs currently in Queue. */
    fun observeQueueTrackStatuses(trackIds: List<UUID>): Flow<Map<UUID, QueueTrackStatus>>
    /** Only the playing track is observed; the entire library is not needed by the player. */
    fun observeTrack(trackId: UUID): Flow<Track?>
    /** One-shot lookup for actions on a track that is not currently playing. */
    suspend fun trackById(trackId: UUID): Track?
    suspend fun shouldRefreshLocalMusic(): Boolean = true
    suspend fun refreshLocalMusic(): LocalLibraryRefreshResult
    suspend fun unmergeSource(sourceId: UUID): UUID
    suspend fun setFavorite(trackId: UUID, favorite: Boolean)
    suspend fun addToLibrary(trackId: UUID)
    /** Hide a song from Library without deleting local music or its source records. */
    suspend fun removeFromLibrary(trackId: UUID)
    /** Hide selected logical tracks without deleting physical audio or playlist membership. */
    suspend fun removeTracksFromLibrary(trackIds: List<UUID>) {
        trackIds.distinct().forEach { removeFromLibrary(it) }
    }
    suspend fun addTracksToLibrary(trackIds: List<UUID>) {
        trackIds.distinct().forEach { addToLibrary(it) }
    }
    suspend fun visibleTrackIds(trackIds: List<UUID>): Set<UUID> = emptySet()

    /** Opportunistically fills missing low-resolution cover art without downloading audio files. */
    fun prefetchArtwork(trackIds: List<UUID>) = Unit
}
