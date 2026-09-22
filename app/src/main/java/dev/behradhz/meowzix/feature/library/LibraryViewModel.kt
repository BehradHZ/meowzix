package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.LocalLibraryRefreshResult
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.domain.telegram.telegramPlaylistId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val availability: Map<UUID, LibraryTrackAvailability> = emptyMap(),
    val downloads: Map<UUID, OfflineDownload> = emptyMap(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val playlistArtwork: Map<UUID, String?> = emptyMap(),
    val selectedPlaylistId: UUID? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastRefresh: LocalLibraryRefreshResult? = null,
    val errorMessage: String? = null,
    val playback: PlaybackState = PlaybackState(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicLibraryRepository,
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
    private val downloadRepository: DownloadRepository,
    private val playlistRepository: PlaylistRepository,
    private val telegramRepository: TelegramRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibraryTracks()
                .catch { error -> reportLoadError(error, "Unable to load music") }
                .collect { tracks ->
                    _state.update {
                        it.copy(
                            tracks = tracks.map { row -> row.track },
                            availability = tracks.associate { row -> row.track.id to row.availability },
                            errorMessage = null,
                        )
                    }
                    // Artwork is deliberately not prefetched for the whole library. Visible rows
                    // and Now Playing request the high-quality image only when they need it.
                }
        }
        viewModelScope.launch {
            playlistRepository.observePlaylists()
                .catch { error -> reportLoadError(error, "Unable to load playlists") }
                .collect { playlists ->
                    _state.update { it.copy(playlists = playlists) }
                }
        }
        viewModelScope.launch {
            downloadRepository.observeDownloads()
                .catch { error -> reportLoadError(error, "Unable to load offline downloads") }
                .collect { downloads ->
                    _state.update {
                        it.copy(downloads = downloads.associateBy(OfflineDownload::trackId))
                    }
                }
        }
        viewModelScope.launch {
            telegramRepository.musicSourceState.collect { telegram ->
                val accountId = telegram.accountId
                val artwork = if (accountId == null) {
                    emptyMap()
                } else {
                    telegram.chats.associate { chat ->
                        telegramPlaylistId(accountId, chat.chatId) to chat.profilePhotoRef
                    }
                }
                _state.update { it.copy(playlistArtwork = artwork) }
            }
        }
        viewModelScope.launch {
            playbackController.state
                // Library only needs the identity/metadata of the active track. Position, buffer,
                // and status ticks are intentionally sliced out so they cannot invalidate the
                // entire library screen several times per second during playback.
                .map { playback -> PlaybackState(currentTrack = playback.currentTrack) }
                .distinctUntilChanged()
                .collect { playback ->
                    _state.update { it.copy(playback = playback) }
                }
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
            runCatching { repository.refreshLocalMusic() }
                .onSuccess { result ->
                    _state.update {
                        it.copy(isRefreshing = false, lastRefresh = result)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            errorMessage = error.message ?: "Unable to refresh local music",
                        )
                    }
                }
        }
    }

    fun playTrack(track: Track, queueTracks: List<Track> = _state.value.tracks) {
        val queue = queueTracks.distinctBy { it.id }
        if (queue.isEmpty()) {
            playbackController.playTrack(track.id)
        } else {
            queueRepository.replaceAndPlay(queue.map { it.id }, track.id, PlaybackMode.ORDERED)
        }
    }

    fun playCollection(tracks: List<Track>, mode: PlaybackMode) {
        val ids = tracks.distinctBy { it.id }.map { it.id }
        if (ids.isNotEmpty()) queueRepository.replaceAndPlay(ids, mode)
    }

    fun playNext(track: Track) = queueRepository.playNext(track.id)

    fun addToQueue(track: Track) = queueRepository.addToQueue(track.id)

    fun pinOffline(track: Track) = downloadRepository.pinOffline(track.id)

    fun setFavorite(track: Track) = viewModelScope.launch {
        runCatching { repository.setFavorite(track.id, !track.favorite) }
            .onFailure { reportLoadError(it, "Unable to update favorite") }
    }

    fun ensureArtwork(track: Track) {
        val ref = track.artworkRef
        if (ref.isNullOrBlank() || ref.contains("/artwork-preview/")) {
            repository.prefetchArtwork(listOf(track.id))
        }
    }

    fun createPlaylist() = viewModelScope.launch {
        runCatching { playlistRepository.create("Playlist ${_state.value.playlists.size + 1}") }
            .onSuccess(::selectPlaylist)
            .onFailure { reportLoadError(it, "Unable to create playlist") }
    }

    fun renamePlaylist(playlistId: UUID, title: String) = viewModelScope.launch {
        runCatching { playlistRepository.rename(playlistId, title) }
            .onFailure { reportLoadError(it, "Unable to rename playlist") }
    }

    fun addToPlaylist(track: Track, playlistId: UUID) = viewModelScope.launch {
        runCatching { playlistRepository.addTrack(playlistId, track.id) }
            .onFailure { reportLoadError(it, "Unable to update playlist") }
    }

    private var playlistTracksJob: Job? = null

    fun selectPlaylist(playlistId: UUID) {
        if (_state.value.selectedPlaylistId == playlistId && playlistTracksJob?.isActive == true) {
            return
        }
        playlistTracksJob?.cancel()
        _state.update {
            it.copy(
                selectedPlaylistId = playlistId,
                selectedPlaylistTracks = emptyList(),
            )
        }
        playlistTracksJob = viewModelScope.launch {
            playlistRepository.observeTracks(playlistId)
                .catch { error -> reportLoadError(error, "Unable to load playlist") }
                .collect { tracks ->
                    _state.update { it.copy(selectedPlaylistTracks = tracks) }
                }
        }
    }

    fun movePlaylistTrack(fromIndex: Int, toIndex: Int) {
        val id = _state.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            runCatching { playlistRepository.moveTrack(id, fromIndex, toIndex) }
                .onFailure { reportLoadError(it, "Unable to reorder playlist") }
        }
    }

    fun removePlaylistTrack(track: Track) {
        val id = _state.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            runCatching { playlistRepository.removeTrack(id, track.id) }
                .onFailure { reportLoadError(it, "Unable to update playlist") }
        }
    }

    fun playSelectedPlaylist(mode: PlaybackMode) {
        playCollection(_state.value.selectedPlaylistTracks, mode)
    }

    fun saveQueueToPlaylist() = viewModelScope.launch {
        val items = queueRepository.queueState.value.items
        if (items.isEmpty()) return@launch
        runCatching {
            val id = playlistRepository.create("Saved queue ${_state.value.playlists.size + 1}")
            playlistRepository.replaceTracks(id, items.map { it.id })
            id
        }.onSuccess(::selectPlaylist)
            .onFailure { reportLoadError(it, "Unable to save queue") }
    }

    private fun reportLoadError(error: Throwable, fallback: String) {
        _state.update {
            it.copy(errorMessage = error.message?.takeIf(String::isNotBlank) ?: fallback)
        }
    }
}
