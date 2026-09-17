package com.behradhz.meowzix.domain.library

import javax.inject.Inject

class RefreshLocalLibraryUseCase @Inject constructor(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke(): LocalLibraryRefreshResult = repository.refreshLocalMusic()
}
