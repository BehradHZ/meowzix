package dev.behradhz.meowzix.domain.library

import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Independent choices: offline deletes only Meowzix-owned copies; Library removal
 * hides the logical song and never deletes user files or Telegram messages.
 */
class RemoveTrackFromLocations @Inject constructor(
    private val library: MusicLibraryRepository,
    private val downloads: DownloadRepository,
) {
    suspend operator fun invoke(trackId: UUID, offline: Boolean, fromLibrary: Boolean) {
        executeTrackRemoval(
            offline = offline,
            fromLibrary = fromLibrary,
            removeOffline = { downloads.removeOfflineCopy(trackId) },
            removeFromLibrary = { library.removeFromLibrary(trackId) },
        )
    }
}

/** Offline first: a failed file cleanup must not silently hide the song as well. */
internal suspend fun executeTrackRemoval(
    offline: Boolean,
    fromLibrary: Boolean,
    removeOffline: suspend () -> Unit,
    removeFromLibrary: suspend () -> Unit,
) {
    require(offline || fromLibrary) { "Choose Offline, Library, or both." }
    if (offline) removeOffline()
    if (fromLibrary) removeFromLibrary()
}
