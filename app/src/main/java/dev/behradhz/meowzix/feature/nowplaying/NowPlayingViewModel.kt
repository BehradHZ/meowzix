package dev.behradhz.meowzix.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.data.repository.ArtworkRepairCoordinator
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.settings.PlaybackContextPolicyStore
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.library.RemoveTrackFromLocations
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueActionFeedbackBus
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.PlaybackPreferenceSettings
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
import dev.behradhz.meowzix.playback.SleepTimerManager
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardFolder
import dev.behradhz.meowzix.domain.telegram.TelegramForwardOptions
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import dev.behradhz.meowzix.domain.telegram.TelegramSendEnqueueResult
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TelegramForwardUiState(
    val isOpen: Boolean = false,
    /** Snapshot of the selected track; auto-advance must not silently change the send target. */
    val track: NowPlayingTrack? = null,
    /** Canonical IDs in their chosen order, including bulk Forward from Library. */
    val trackIds: List<UUID> = emptyList(),
    val query: String = "",
    val chats: List<TelegramChatSummary> = emptyList(),
    val folders: List<TelegramForwardFolder> = emptyList(),
    val foldersLoading: Boolean = false,
    val foldersError: String? = null,
    val folderRefreshGeneration: Int = 0,
    val isSearching: Boolean = false,
    val isSending: Boolean = false,
    /** Counts newly accepted sends for this picker session; never counts retries/errors. */
    val successfulForwardCount: Int = 0,
    val errorMessage: String? = null,
    val defaults: TelegramForwardSettings = TelegramForwardSettings(),
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val queueRepository: QueueRepository,
    private val audioVisualizerRepository: AudioVisualizerRepository,
    private val libraryRepository: MusicLibraryRepository,
    private val trackRemoval: RemoveTrackFromLocations,
    private val telegramForwardRepository: TelegramForwardRepository,
    private val settingsRepository: SettingsRepository,
    private val artworkRepairCoordinator: ArtworkRepairCoordinator,
    private val playbackContextPolicyStore: PlaybackContextPolicyStore,
    private val sleepTimerManager: SleepTimerManager,
    queueActionFeedbackBus: QueueActionFeedbackBus,
) : ViewModel() {
    val queueActionFeedback = queueActionFeedbackBus.events
    private val _trackRemovalError = MutableStateFlow<String?>(null)
    val trackRemovalError: StateFlow<String?> = _trackRemovalError.asStateFlow()

    /** Remove only user-selected locations; never delete the device original or Telegram message. */
    fun removeTrackFromLocations(trackId: UUID, offline: Boolean, fromLibrary: Boolean) {
        if (!offline && !fromLibrary) return
        viewModelScope.launch {
            try {
                trackRemoval(trackId, offline, fromLibrary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _trackRemovalError.value = error.message ?: "Unable to remove song"
            }
        }
    }

    fun dismissTrackRemovalError() {
        _trackRemovalError.value = null
    }

    private val currentTrackId = playbackController.state
        .map { playback -> playback.currentTrack?.id }
        .distinctUntilChanged()

    // Metadata is already part of the Media3/Queue snapshots. Only observe the
    // playing row for Favorite and late artwork repair; never load the full library.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentLibraryTrack = currentTrackId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else libraryRepository.observeTrack(id).catch { emit(null) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state = combine(
        playbackController.state,
        currentLibraryTrack,
    ) { playback, liveTrack ->
        val current = playback.currentTrack ?: return@combine playback
        if (liveTrack == null || liveTrack.id != current.id) return@combine playback
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

    // PlaybackController already owns metadata, ordering and occurrence IDs.
    // A second, library-backed copy of every queued item is unnecessary.
    val queueState = queueRepository.queueState

    val spectrum = audioVisualizerRepository.spectrum
    val sleepTimerState = sleepTimerManager.state
    val sleepTimerPreferences = settingsRepository.playbackPreferenceSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackPreferenceSettings())
    val appearanceSettings = settingsRepository.appearanceSettings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppearanceSettings(),
        )

    val isFavorite = combine(
        currentTrackId,
        currentLibraryTrack,
    ) { id, selected ->
        id != null && selected?.id == id && selected.favorite
    }.distinctUntilChanged().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    private val _forwardState = MutableStateFlow(TelegramForwardUiState())
    val forwardState: StateFlow<TelegramForwardUiState> = _forwardState.asStateFlow()
    private var forwardSearchJob: Job? = null
    private var forwardFoldersJob: Job? = null
    private var forwardTrackId: UUID? = null
    private var forwardTrackIds: List<UUID> = emptyList()
    private var forwardPickerRequest = 0L

    init {
        viewModelScope.launch { sleepTimerManager.restore() }
        viewModelScope.launch {
            combine(
                playbackController.state
                    .map { it.queueIndex to it.currentTrack?.id }
                    .distinctUntilChanged(),
                queueState,
            ) { (playbackIndex, currentTrackId), queue ->
                val resolvedIndex = when {
                    playbackIndex in queue.items.indices -> playbackIndex
                    queue.currentIndex in queue.items.indices -> queue.currentIndex
                    currentTrackId != null -> queue.items.indexOfFirst { it.id == currentTrackId }
                    else -> -1
                }

                buildList {
                    queue.items.getOrNull(resolvedIndex - 1)?.id?.let(::add)
                    queue.items.getOrNull(resolvedIndex)?.id?.let(::add)
                    currentTrackId?.let(::add)
                    queue.items.getOrNull(resolvedIndex + 1)?.id?.let(::add)
                }.distinct()
            }
                .distinctUntilChanged()
                .collect { trackIds ->
                    if (trackIds.isNotEmpty()) {
                        artworkRepairCoordinator.prefetch(trackIds)
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

    fun refreshVisualizer() = audioVisualizerRepository.refresh()

    fun setVisualizerCaptureEnabled(enabled: Boolean) = audioVisualizerRepository.setCaptureEnabled(enabled)

    fun setSleepTimerMinutes(minutes: Int) = viewModelScope.launch {
        val bounded = minutes.coerceIn(5, 180)
        settingsRepository.saveSleepTimerPreset(SleepTimerPresetMode.DURATION, bounded)
        val fade = settingsRepository.playbackPreferenceSettings.first().sleepTimerFadeEnabled
        sleepTimerManager.setDuration(bounded * 60_000L, if (fade) SleepTimerManager.DEFAULT_FADE_MS else 0L)
    }

    fun setSleepTimerEndOfTrack() = viewModelScope.launch {
        val mediaId = state.value.currentTrack?.id?.toString() ?: return@launch
        settingsRepository.saveSleepTimerPreset(SleepTimerPresetMode.END_OF_TRACK, 30)
        val fade = settingsRepository.playbackPreferenceSettings.first().sleepTimerFadeEnabled
        sleepTimerManager.stopAtEndOfTrack(mediaId, if (fade) SleepTimerManager.DEFAULT_FADE_MS else 0L)
    }

    /** The toggle resumes the last saved mode/duration with a fresh deadline. */
    fun setSleepTimerEnabled(enabled: Boolean) = viewModelScope.launch {
        if (!enabled) {
            sleepTimerManager.cancel()
            return@launch
        }
        val prefs = settingsRepository.playbackPreferenceSettings.first()
        val fadeMs = if (prefs.sleepTimerFadeEnabled) SleepTimerManager.DEFAULT_FADE_MS else 0L
        when (prefs.sleepTimerLastMode) {
            SleepTimerPresetMode.DURATION ->
                sleepTimerManager.setDuration(prefs.sleepTimerLastMinutes.coerceIn(5, 180) * 60_000L, fadeMs)
            SleepTimerPresetMode.END_OF_TRACK -> {
                val mediaId = state.value.currentTrack?.id?.toString() ?: return@launch
                sleepTimerManager.stopAtEndOfTrack(mediaId, fadeMs)
            }
        }
    }

    fun extendSleepTimer() = viewModelScope.launch { sleepTimerManager.extend(15 * 60_000L) }
    fun shortenSleepTimer() = viewModelScope.launch { sleepTimerManager.extend(-15 * 60_000L) }
    fun cancelSleepTimer() = setSleepTimerEnabled(false)

    private var savePlaybackSpeedJob: Job? = null

    fun setPlaybackSpeed(speed: Float) {
        val bounded = speed.coerceIn(0.5f, 5f)
        playbackController.setPlaybackSpeed(bounded)
        // The UI and Media3 update immediately. Coalesce rapid dial movements to
        // avoid hundreds of persistent DataStore writes per gesture.
        savePlaybackSpeedJob?.cancel()
        savePlaybackSpeedJob = viewModelScope.launch {
            delay(200L)
            settingsRepository.setPlaybackSpeed(bounded)
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun previous() = playbackController.skipToPrevious()

    fun playQueueItemAt(index: Int) {
        if (index in queueState.value.items.indices) queueRepository.playAt(index)
    }

    fun next() = playbackController.skipToNext()

    fun toggleFavorite() {
        val id = state.value.currentTrack?.id ?: return
        viewModelScope.launch {
            libraryRepository.setFavorite(id, !isFavorite.value)
        }
    }

    fun togglePlaybackMode() {
        val nextMode = when (state.value.playbackMode) {
            PlaybackMode.ORDERED -> PlaybackMode.PURE_SHUFFLE
            PlaybackMode.PURE_SHUFFLE -> PlaybackMode.SMART_SHUFFLE
            PlaybackMode.SMART_SHUFFLE -> PlaybackMode.ORDERED
        }
        queueRepository.setPlaybackMode(nextMode)
        playbackContextPolicyStore.savePlaybackModeForActiveContext(nextMode)
    }

    fun cycleRepeatMode() {
        val nextMode = when (state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
        queueRepository.setRepeatMode(nextMode)
        playbackContextPolicyStore.saveRepeatModeForActiveContext(nextMode)
    }

    fun openForwardPicker() {
        val id = state.value.currentTrack?.id ?: return
        openForwardPickerForTrack(id)
    }

    fun openForwardPickerForTrack(trackId: UUID, fallbackTrack: NowPlayingTrack? = null) {
        val request = ++forwardPickerRequest
        val selectedTrack = state.value.currentTrack?.takeIf { it.id == trackId }
            ?: fallbackTrack?.takeIf { it.id == trackId }
        if (selectedTrack != null) {
            showForwardPicker(selectedTrack)
            return
        }
        // Arbitrary Queue/Library tracks are fetched only when Forward is requested.
        viewModelScope.launch {
            val track = try {
                libraryRepository.trackById(trackId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (forwardPickerRequest != request) return@launch
            showForwardPicker(NowPlayingTrack(track.id, track.title, track.artist, track.artworkRef))
        }
    }

    /** A single picker session forwards every distinct selected track, without text sharing. */
    fun openForwardPickerForTracks(tracks: List<Track>) {
        val selected = tracks.distinctBy(Track::id)
        val first = selected.firstOrNull() ?: return
        forwardPickerRequest++
        showForwardPicker(
            selectedTrack = NowPlayingTrack(
                first.id,
                if (selected.size == 1) first.title else "${selected.size} selected tracks",
                if (selected.size == 1) first.artist else null,
                first.artworkRef,
            ),
            trackIds = selected.map(Track::id),
        )
    }

    private fun showForwardPicker(
        selectedTrack: NowPlayingTrack,
        trackIds: List<UUID> = listOf(selectedTrack.id),
    ) {
        forwardSearchJob?.cancel()
        forwardFoldersJob?.cancel()
        val cachedFolders = telegramForwardRepository.cachedForwardFolders()
        forwardTrackId = selectedTrack.id
        forwardTrackIds = trackIds.distinct()
        _forwardState.update {
            it.copy(
                isOpen = true,
                track = selectedTrack,
                trackIds = forwardTrackIds,
                query = "",
                chats = emptyList(),
                folders = cachedFolders.orEmpty(),
                foldersLoading = cachedFolders == null,
                foldersError = if (cachedFolders?.isEmpty() == true) "No Telegram folders found" else null,
                isSearching = true,
                isSending = false,
                successfulForwardCount = 0,
                errorMessage = null,
            )
        }
        // Publish folder navigation before starting the recent-chat query.
        if (cachedFolders == null) loadForwardFolders(forceRefresh = false)
        searchForwardChats("")
    }

    /** Explicit Retry is the only action that bypasses a valid in-memory snapshot. */
    fun reloadForwardFolders() = loadForwardFolders(forceRefresh = true)

    private fun loadForwardFolders(forceRefresh: Boolean) {
        val trackId = _forwardState.value.track?.id ?: return
        forwardFoldersJob?.cancel()
        _forwardState.update {
            if (it.isOpen && it.track?.id == trackId) it.copy(
                foldersLoading = true,
                foldersError = null,
            ) else it
        }
        forwardFoldersJob = viewModelScope.launch {
            try {
                val folders = if (forceRefresh) {
                    telegramForwardRepository.refreshForwardFolders()
                } else {
                    telegramForwardRepository.loadForwardFolders()
                }
                _forwardState.update {
                    if (it.isOpen && it.track?.id == trackId) it.copy(
                        folders = folders,
                        foldersLoading = false,
                        folderRefreshGeneration = it.folderRefreshGeneration + if (forceRefresh) 1 else 0,
                        foldersError = if (folders.isEmpty()) "Folders not available yet" else null,
                    ) else it
                }
                if (forceRefresh) searchForwardChats("")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _forwardState.update {
                    if (it.isOpen && it.track?.id == trackId) it.copy(
                        foldersLoading = false,
                        foldersError = "Couldn't load Telegram folders",
                    ) else it
                }
            }
        }
    }

    fun dismissForwardPicker() {
        forwardPickerRequest++
        forwardSearchJob?.cancel()
        forwardFoldersJob?.cancel()
        forwardTrackId = null
        forwardTrackIds = emptyList()
        _forwardState.update {
            it.copy(
                isOpen = false,
                track = null,
                trackIds = emptyList(),
                query = "",
                chats = emptyList(),
                folders = emptyList(),
                foldersLoading = false,
                foldersError = null,
                isSearching = false,
                isSending = false,
                successfulForwardCount = 0,
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
                    if (_forwardState.value.isOpen && _forwardState.value.query == query) {
                        _forwardState.update { it.copy(chats = chats, isSearching = false) }
                    }
                }
                .onFailure { error ->
                    if (_forwardState.value.isOpen && _forwardState.value.query == query) {
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
        val trackId = forwardTrackId ?: return
        val ids = forwardTrackIds.toList().ifEmpty { listOf(trackId) }
        if (_forwardState.value.isSending) return
        val request = forwardPickerRequest
        val picker = _forwardState.value
        val targetTitle = picker.chats.firstOrNull { it.chatId == targetChatId }?.title
            ?: telegramForwardRepository.cachedForwardChat(targetChatId)?.title
        _forwardState.update { it.copy(errorMessage = null, isSending = true) }
        viewModelScope.launch {
            try {
                if (rememberDefaults) {
                    settingsRepository.setTelegramForwardDefaults(
                        includeSourceAttribution = options.includeSourceAttribution,
                        keepCaption = options.keepCaption,
                    )
                }
                var queued = 0
                var alreadyPresent = 0
                var failed = 0
                var lastError: String? = null
                for (id in ids) {
                    // Do not enqueue additional tracks after the user closes this picker.
                    if (request != forwardPickerRequest) break
                    try {
                        when (telegramForwardRepository.enqueueTrack(
                            trackId = id,
                            targetChatId = targetChatId,
                            targetTitle = targetTitle,
                            options = options,
                        )) {
                            is TelegramSendEnqueueResult.Queued -> queued++
                            is TelegramSendEnqueueResult.AlreadyQueued,
                            is TelegramSendEnqueueResult.AlreadyInChat -> alreadyPresent++
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failed++
                        lastError = error.message
                    }
                }
                val message = when {
                    failed > 0 -> "$queued queued; $failed failed. ${lastError ?: "Try again."}"
                    alreadyPresent > 0 && queued == 0 ->
                        "All selected tracks are already queued or present in this chat."
                    alreadyPresent > 0 -> "$queued queued; $alreadyPresent already queued or present."
                    else -> null
                }
                _forwardState.update {
                    if (request == forwardPickerRequest && it.isOpen && it.track?.id == trackId &&
                        it.trackIds == ids
                    ) {
                        it.copy(
                            errorMessage = message,
                            isSending = false,
                            successfulForwardCount = it.successfulForwardCount + queued,
                        )
                    } else it
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _forwardState.update {
                    if (request == forwardPickerRequest && it.isOpen) it.copy(
                        isSending = false,
                        errorMessage = error.message ?: "Unable to queue this Telegram send.",
                    ) else it
                }
            }
        }
    }
}
