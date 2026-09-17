package com.behradhz.meowzix.domain.library

import com.behradhz.meowzix.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicLibraryRepository {
    val tracks: Flow<List<Track>>

    /** Refreshes the authoritative device-local MediaStore source. */
    suspend fun refreshLocalMusic(): LocalLibraryRefreshResult
}
