package dev.behradhz.meowzix.domain.library

import dev.behradhz.meowzix.core.model.Track
import kotlinx.coroutines.flow.Flow

data class LocalLibraryRefreshResult(
    val discovered: Int,
    val created: Int,
    val updated: Int,
    val markedMissing: Int,
)

interface MusicLibraryRepository {
    fun observeTracks(): Flow<List<Track>>
    suspend fun refreshLocalMusic(): LocalLibraryRefreshResult
}
