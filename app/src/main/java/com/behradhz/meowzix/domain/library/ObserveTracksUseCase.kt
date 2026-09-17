package com.behradhz.meowzix.domain.library

import com.behradhz.meowzix.core.model.Track
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTracksUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(): Flow<List<Track>> = repository.tracks
}
