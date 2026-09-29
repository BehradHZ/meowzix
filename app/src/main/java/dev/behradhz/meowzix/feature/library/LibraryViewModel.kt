package dev.behradhz.meowzix.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.ArtworkRepairCoordinator
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.data.settings.PlaybackContextKeys
import dev.behradhz.meowzix.data.settings.PlaybackContextPolicyStore
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
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.domain.telegram.telegramPlaylistId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    /**
     * Compatibility slice for legacy callers: favorites plus the active track only. The primary
     * Library surface is paged from Room and must never depend on this as a complete library list.
     */
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
    private val queryRepository: LibraryQueryRepository,
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
    private val downloadRepository: DownloadRepository,
    private val playlistRepository: PlaylistRepository,
    private val telegramRepository: TelegramRepository,
    private val artworkRepairCoordinator: ArtworkRepairCoordinator,
    private val playbackContextPolicyStore: PlaybackContextPolicyStore,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    private var initialRefreshChecked = false
    private var favoriteTracksSnapshot: List<Track> = emptyList()
    private var activeTrackSnapshot: Track? = null

    init {
        // Do not subscribe to observeLibraryTracks() here. That materialized every Track and every
        // availability entry even while the V4 screen rendered only a Paging window. Keep only the
        // two tiny compatibility slices still required by shared/legacy UI code.
        viewModelScope.launch {
            queryRepository.favoriteTracks()
                .catch { error -> reportLoadError(error, "Unable to load favorites") }
                .collect { favorites ->
                    favoriteTracksSnapshot = favorites
                    _state.update { it.copy(tracks = compatibilityTracks(), errorMessage = null) }
                }
        }
        viewModelScope.launch {
            queryRepository.availability()
                .catch { error -> reportLoadError(error, "Unable to load availability") }
                .collect { availability ->
                    _state.update { it.copy(availability = availability) }
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
                    activeTrackSnapshot = playback.currentTrack?.id?.let { trackId ->
                        runCatching { queryRepository.track(trackId) }.getOrNull()
                    }
                    _state.update {
                        it.copy(
                            playback = playback,
                            tracks = compatibilityTracks(),
                        )
                    }
                }
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return

        // A library refresh must reconcile every active source, not just MediaStore. Telegram sync
        // is incremental, so this also recovers a new-message update that was missed while the app
        // process was starting or reconnecting.
        if (
            telegramRepository.authState.value.step == TelegramAuthStep.Ready &&
            telegramRepository.musicSourceState.value.selectedChatIds.isNotEmpty()
        ) {
            telegramRepository.syncSelectedSources()
        }

        viewModelScope.launch {
            if (!initialRefreshChecked) {
                initialRefreshChecked = true
                val shouldRefresh = runCatching { repository.shouldRefreshLocalMusic() }
                    .getOrDefault(true)
                if (!shouldRefresh) return@launch
            }
            refreshInternal()
        }
    }

    private suspend fun refreshInternal() {
        if (_state.value.isRefreshing) return
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

    fun playTrack(track: Track, queueTracks: List<Track> = listOf(track)) {
        val queue = queueTracks.distinctBy { it.id }
        if (queue.isEmpty()) {
            playbackController.playTrack(track.id)
            return
        }

        val contextKey = playbackContextFor(queueTracks)
        val rememberedMode = playbackContextPolicyStore.policy(contextKey).playbackMode
        val selectedIndex = queue.indexOfFirst { it.id == track.id }
        val tracksToPlay = when {
            selectedIndex < 0 -> listOf(track)
            rememberedMode == PlaybackMode.ORDERED -> queue.drop(selectedIndex)
            else -> queue
        }
        startContextQueue(
            tracks = tracksToPlay,
            startTrackId = track.id,
            contextKey = contextKey,
        )
    }

    fun playCollection(tracks: List<Track>, mode: PlaybackMode) {
        startContextQueue(
            tracks = tracks,
            explicitMode = mode,
            contextKey = PlaybackContextKeys.LIBRARY,
        )
    }

    fun playNext(track: Track) = queueRepository.playNext(track.id)

    fun addToQueue(track: Track) = queueRepository.addToQueue(track.id)

    fun pinOffline(track: Track) = downloadRepository.pinOffline(track.id)

    fun setFavorite(track: Track) = viewModelScope.launch {
        runCatching { repository.setFavorite(track.id, !track.favorite) }
            .onFailure { reportLoadError(it, "Unable to update favorite") }
    }

    fun ensureArtwork(track: Track) {
        artworkRepairCoordinator.prefetch(listOf(track.id))
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

    fun updatePlaylistMetadata(
        playlistId: UUID,
        title: String,
        description: String?,
        artworkRef: String?,
    ) = viewModelScope.launch {
        runCatching {
            playlistRepository.updateMetadata(playlistId, title, description, artworkRef)
        }.onFailure { reportLoadError(it, "Unable to update playlist") }
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
        val playlistId = _state.value.selectedPlaylistId ?: return
        startContextQueue(
            tracks = _state.value.selectedPlaylistTracks,
            explicitMode = mode,
            contextKey = PlaybackContextKeys.playlist(playlistId),
        )
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

    private fun startContextQueue(
        tracks: List<Track>,
        startTrackId: UUID? = null,
        explicitMode: PlaybackMode? = null,
        contextKey: String,
    ) {
        val ids = tracks.distinctBy { it.id }.map { it.id }
        if (ids.isEmpty()) return

        playbackContextPolicyStore.activate(contextKey)
        val storedPolicy = playbackContextPolicyStore.policy(contextKey)
        val playbackMode = explicitMode ?: storedPolicy.playbackMode
        if (explicitMode != null) {
            playbackContextPolicyStore.savePlaybackMode(contextKey, explicitMode)
        }

        prepareRepeatRestore(ids.toSet(), storedPolicy.repeatMode)
        if (startTrackId == null) {
            queueRepository.replaceAndPlay(ids, playbackMode)
        } else {
            queueRepository.replaceAndPlay(ids, startTrackId, playbackMode)
        }
    }

    private fun prepareRepeatRestore(expectedTrackIds: Set<UUID>, repeatMode: RepeatMode) {
        if (repeatMode == RepeatMode.OFF) return
        // Subscribe before replaceAndPlay so even a very fast queue replacement cannot race past
        // the repeat restoration listener.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            queueRepository.queueState
                .drop(1)
                .first { queue ->
                    queue.items.isNotEmpty() && queue.items.map { it.id }.toSet() == expectedTrackIds
                }
            queueRepository.setRepeatMode(repeatMode)
        }
    }

    private fun playbackContextFor(queueTracks: List<Track>): String {
        val playlistId = _state.value.selectedPlaylistId ?: return PlaybackContextKeys.LIBRARY
        val selectedPlaylistIds = _state.value.selectedPlaylistTracks.map { it.id }
        val queueIds = queueTracks.map { it.id }
        return if (selectedPlaylistIds.isNotEmpty() && queueIds == selectedPlaylistIds) {
            PlaybackContextKeys.playlist(playlistId)
        } else {
            PlaybackContextKeys.LIBRARY
        }
    }

    private fun compatibilityTracks(): List<Track> = buildList {
        addAll(favoriteTracksSnapshot)
        activeTrackSnapshot?.let(::add)
    }.distinctBy(Track::id)

    private fun reportLoadError(error: Throwable, fallback: String) {
        _state.update {
            it.copy(errorMessage = error.message?.takeIf(String::isNotBlank) ?: fallback)
        }
    }
}
