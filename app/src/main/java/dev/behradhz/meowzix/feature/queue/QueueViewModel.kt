package dev.behradhz.meowzix.feature.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.QueueRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueAuxState(
    val tracks: Map<UUID, Track> = emptyMap(),
    val availability: Map<UUID, LibraryTrackAvailability> = emptyMap(),
    val downloads: Map<UUID, OfflineDownload> = emptyMap(),
    val playlists: List<PlaylistSummary> = emptyList(),
)

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
    private val libraryRepository: MusicLibraryRepository,
    private val downloads: DownloadRepository,
    private val playlists: PlaylistRepository,
) : ViewModel() {
    val state = queueRepository.queueState
    private val _aux = MutableStateFlow(QueueAuxState())
    val aux: StateFlow<QueueAuxState> = _aux.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.observeLibraryTracks().collect { rows ->
                _aux.update { current ->
                    current.copy(
                        tracks = rows.associate { it.track.id to it.track },
                        availability = rows.associate { it.track.id to it.availability },
                    )
                }
            }
        }
        viewModelScope.launch {
            downloads.observeDownloads().collect { rows ->
                _aux.update { it.copy(downloads = rows.associateBy(OfflineDownload::trackId)) }
            }
        }
        viewModelScope.launch {
            playlists.observePlaylists().collect { rows -> _aux.update { it.copy(playlists = rows) } }
        }
    }

    fun play(index: Int) = queueRepository.playAt(index)
    fun move(fromIndex: Int, toIndex: Int) = queueRepository.move(fromIndex, toIndex)
    fun remove(index: Int) = queueRepository.removeAt(index)
    fun clear() = queueRepository.clear()
    fun playNext(trackId: UUID) = queueRepository.playNext(trackId)
    fun addToQueue(trackId: UUID) = queueRepository.addToQueue(trackId)
    fun pinOffline(trackId: UUID) = downloads.pinOffline(trackId)
    fun toggleFavorite(trackId: UUID) {
        val track = _aux.value.tracks[trackId] ?: return
        viewModelScope.launch { libraryRepository.setFavorite(trackId, !track.favorite) }
    }
    fun addToPlaylist(trackId: UUID, playlistId: UUID) = viewModelScope.launch {
        playlists.addTrack(playlistId, trackId)
    }
}
