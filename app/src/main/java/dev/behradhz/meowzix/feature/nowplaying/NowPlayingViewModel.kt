package dev.behradhz.meowzix.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardOptions
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TelegramForwardUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val chats: List<TelegramChatSummary> = emptyList(),
    val isSearching: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val defaults: TelegramForwardSettings = TelegramForwardSettings(),
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
    private val audioVisualizerRepository: AudioVisualizerRepository,
    private val libraryRepository: MusicLibraryRepository,
    private val telegramForwardRepository: TelegramForwardRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val libraryTracks = libraryRepository.observeTracks()
        .catch { emit(emptyList()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val state = combine(
        playbackController.state,
        libraryTracks,
    ) { playback, tracks ->
        val current = playback.currentTrack ?: return@combine playback
        val liveTrack = tracks.firstOrNull { it.id == current.id } ?: return@combine playback
        playback.copy(
            currentTrack = current.copy(
                title = liveTrack.title,
                artist = liveTrack.artist,
                artworkRef = liveTrack.artworkRef ?: current.artworkRef,
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playbackController.state.value,
    )

    val queueState = queueRepository.queueState
    val spectrum = audioVisualizerRepository.spectrum

    val isFavorite = combine(
        state,
        libraryTracks,
    ) { playback, tracks ->
        val id = playback.currentTrack?.id
        id != null && tracks.firstOrNull { it.id == id }?.favorite == true
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    private val _forwardState = MutableStateFlow(TelegramForwardUiState())
    val forwardState: StateFlow<TelegramForwardUiState> = _forwardState.asStateFlow()
    private var forwardSearchJob: Job? = null

    init {
        viewModelScope.launch {
            playbackController.state
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { trackId ->
                    if (trackId != null) {
                        libraryRepository.prefetchArtwork(listOf(trackId))
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.telegramForwardSettings
                .catch { emit(TelegramForwardSettings()) }
                .collect { defaults ->
                    _forwardState.update { it.copy(defaults = defaults) }
                }
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun previous() = playbackController.skipToPrevious()

    fun next() = playbackController.skipToNext()

    fun toggleFavorite() {
        val id = state.value.currentTrack?.id ?: return
        viewModelScope.launch {
            libraryRepository.setFavorite(id, !isFavorite.value)
        }
    }

    fun togglePlaybackMode() = queueRepository.setPlaybackMode(
        when (state.value.playbackMode) {
            PlaybackMode.ORDERED -> PlaybackMode.PURE_SHUFFLE
            PlaybackMode.PURE_SHUFFLE -> PlaybackMode.SMART_SHUFFLE
            PlaybackMode.SMART_SHUFFLE -> PlaybackMode.ORDERED
        },
    )

    fun cycleRepeatMode() = queueRepository.setRepeatMode(
        when (state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        },
    )

    fun openForwardPicker() {
        if (state.value.currentTrack == null) return
        _forwardState.update {
            it.copy(
                isOpen = true,
                query = "",
                chats = emptyList(),
                isSearching = true,
                isSending = false,
                errorMessage = null,
            )
        }
        searchForwardChats("")
    }

    fun dismissForwardPicker() {
        forwardSearchJob?.cancel()
        _forwardState.update {
            it.copy(
                isOpen = false,
                query = "",
                chats = emptyList(),
                isSearching = false,
                isSending = false,
                errorMessage = null,
            )
        }
    }

    fun searchForwardChats(query: String) {
        _forwardState.update { it.copy(query = query, isSearching = true, errorMessage = null) }
        forwardSearchJob?.cancel()
        forwardSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(120)
            runCatching { telegramForwardRepository.searchChats(query) }
                .onSuccess { chats ->
                    if (_forwardState.value.query == query) {
                        _forwardState.update { it.copy(chats = chats, isSearching = false) }
                    }
                }
                .onFailure { error ->
                    if (_forwardState.value.query == query) {
                        _forwardState.update {
                            it.copy(
                                chats = emptyList(),
                                isSearching = false,
                                errorMessage = error.message ?: "Unable to search Telegram chats.",
                            )
                        }
                    }
                }
        }
    }

    fun forwardCurrentTrack(
        targetChatId: Long,
        options: TelegramForwardOptions,
        rememberDefaults: Boolean,
    ) {
        val trackId = state.value.currentTrack?.id ?: return
        if (_forwardState.value.isSending) return
        viewModelScope.launch {
            _forwardState.update { it.copy(isSending = true, errorMessage = null) }
            runCatching {
                if (rememberDefaults) {
                    settingsRepository.setTelegramForwardDefaults(
                        includeSourceAttribution = options.includeSourceAttribution,
                        keepCaption = options.keepCaption,
                    )
                }
                telegramForwardRepository.forwardTrack(trackId, targetChatId, options)
            }.onSuccess {
                dismissForwardPicker()
            }.onFailure { error ->
                _forwardState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.message ?: "Unable to forward this track.",
                    )
                }
            }
        }
    }
}
