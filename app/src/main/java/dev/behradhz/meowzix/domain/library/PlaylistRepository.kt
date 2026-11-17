package dev.behradhz.meowzix.domain.library

import dev.behradhz.meowzix.core.model.Track
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Virtual, read-only playlist reflecting visible device MediaStore music. */
val LOCAL_PLAYLIST_ID: UUID = UUID.nameUUIDFromBytes("meowzix:local-media-playlist".toByteArray(Charsets.UTF_8))

data class PlaylistSummary(
    val id: UUID,
    val title: String,
    val trackCount: Int,
    val description: String? = null,
    val artworkRef: String? = null,
    val updatedAt: Instant = Instant.EPOCH,
    val telegramManaged: Boolean = false,
    val titleOverridden: Boolean = false,
    val descriptionOverridden: Boolean = false,
    val artworkOverridden: Boolean = false,
    val newTrackHighlightEnabled: Boolean = true,
    val localManaged: Boolean = false,
) {
    val modified: Boolean get() = titleOverridden || descriptionOverridden || artworkOverridden
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<PlaylistSummary>>
    fun observeTracks(playlistId: UUID): Flow<List<Track>>
    fun observeUnheardNewTracks(): Flow<Map<UUID, Set<UUID>>> = flowOf(emptyMap())
    suspend fun setNewTrackHighlightEnabled(playlistId: UUID, enabled: Boolean) {}
    /** Mark an explicitly interacted-with song as no longer new, across Telegram chats. */
    suspend fun markNewTrackSeen(trackId: UUID) {}
    suspend fun create(title: String, description: String? = null, artworkRef: String? = null): UUID
    suspend fun rename(playlistId: UUID, title: String)
    suspend fun updateMetadata(playlistId: UUID, title: String, description: String?, artworkRef: String?)
    suspend fun restoreTelegramMetadata(playlistId: UUID, sourceTitle: String)
    suspend fun delete(playlistId: UUID)
    suspend fun addTrack(playlistId: UUID, trackId: UUID)
    suspend fun removeTrack(playlistId: UUID, trackId: UUID)
    suspend fun moveTrack(playlistId: UUID, fromIndex: Int, toIndex: Int)
    suspend fun replaceTracks(playlistId: UUID, trackIds: List<UUID>)
}
