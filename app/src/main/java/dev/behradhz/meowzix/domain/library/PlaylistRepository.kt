package dev.behradhz.meowzix.domain.library

import dev.behradhz.meowzix.core.model.Track
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class PlaylistSummary(val id: UUID, val title: String, val trackCount: Int)

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<PlaylistSummary>>
    fun observeTracks(playlistId: UUID): Flow<List<Track>>
    suspend fun create(title: String): UUID
    suspend fun delete(playlistId: UUID)
    suspend fun addTrack(playlistId: UUID, trackId: UUID)
    suspend fun removeTrack(playlistId: UUID, trackId: UUID)
    suspend fun moveTrack(playlistId: UUID, fromIndex: Int, toIndex: Int)
    suspend fun replaceTracks(playlistId: UUID, trackIds: List<UUID>)
}
