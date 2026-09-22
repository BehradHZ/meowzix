package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.history.ListeningEventType
import dev.behradhz.meowzix.domain.history.ListeningHistoryRepository
import dev.behradhz.meowzix.domain.history.PlaybackInitiator
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.domain.playback.RemoteTrackPlaybackResolver
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class ResolvingPlaybackController @Inject constructor(
    private val delegate: AndroidPlaybackController,
    private val catalog: PlaybackCatalog,
    private val remoteResolver: RemoteTrackPlaybackResolver,
    private val history: ListeningHistoryRepository,
    private val settingsRepository: SettingsRepository,
) : PlaybackController, QueueRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(delegate.state.value)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override val queueState: StateFlow<QueueState> = delegate.queueState

    @Volatile private var resolvingRemote = false
    @Volatile private var activeHistoryId: UUID? = null
    @Volatile private var historyTrackId: UUID? = null
    @Volatile private var lastPlaybackState = PlaybackState()
    @Volatile private var nextInitiator: PlaybackInitiator? = null
    @Volatile private var intentionalSkip = false

    init {
        scope.launch {
            delegate.state.collect { playback ->
                recordHistoryTransition(playback)
                if (!resolvingRemote) _state.value = playback
            }
        }
        scope.launch {
            delegate.queueState
                .map { queue -> queue.items.getOrNull(queue.currentIndex + 1)?.id }
                .distinctUntilChanged()
                .collect { nextTrackId ->
                    remoteResolver.cancelPrefetch()
                    if (nextTrackId != null && !isLocal(nextTrackId)) {
                        remoteResolver.prefetch(nextTrackId)
                    }
                }
        }
        scope.launch {
            delegate.state
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { currentTrackId ->
                    if (currentTrackId != null && !isLocal(currentTrackId)) {
                        runCatching { remoteResolver.prepareForPlayback(currentTrackId) }
                    }
                }
        }
    }

    override fun playTrack(trackId: UUID) {
        nextInitiator = PlaybackInitiator.USER
        intentionalSkip = historyTrackId != null
        resolveAndPlayNow(trackId)
    }

    override fun playNow(trackId: UUID) {
        nextInitiator = PlaybackInitiator.USER
        intentionalSkip = historyTrackId != null
        resolveAndPlayNow(trackId)
    }

    override fun playNext(trackId: UUID) = delegate.playNext(trackId)

    override fun addToQueue(trackId: UUID) = delegate.addToQueue(trackId)

    override fun replaceAndPlay(trackIds: List<UUID>, mode: PlaybackMode) {
        val start = trackIds.firstOrNull()
        if (start == null) return
        replaceAndPlay(trackIds, start, mode)
    }

    override fun replaceAndPlay(trackIds: List<UUID>, startTrackId: UUID, mode: PlaybackMode) {
        nextInitiator = PlaybackInitiator.USER
        intentionalSkip = historyTrackId != null
        scope.launch {
            resolvingRemote = true
            _state.value = delegate.state.value.copy(status = PlaybackStatus.PREPARING, errorMessage = null)
            runCatching {
                if (!isLocal(startTrackId)) remoteResolver.prepareForPlayback(startTrackId)
                delegate.replaceAndPlay(trackIds, startTrackId, mode)
            }.onFailure { error ->
                _state.value = delegate.state.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = error.message ?: "Unable to prepare queue.",
                )
            }
            resolvingRemote = false
        }
    }

    override fun removeAt(index: Int) = delegate.removeAt(index)

    override fun move(fromIndex: Int, toIndex: Int) = delegate.move(fromIndex, toIndex)

    override fun clear() = delegate.clear()

    override fun setPlaybackMode(mode: PlaybackMode) = delegate.setPlaybackMode(mode)

    override fun setRepeatMode(mode: RepeatMode) = delegate.setRepeatMode(mode)

    override fun resume() = delegate.resume()

    override fun pause() = delegate.pause()

    override fun togglePlayPause() = delegate.togglePlayPause()

    override fun seekTo(positionMs: Long) {
        activeHistoryId?.let { playbackId ->
            scope.launch {
                historySafely { history.recordSeek(playbackId, positionMs, lastPlaybackState.durationMs) }
            }
        }
        delegate.seekTo(positionMs)
    }

    override fun skipToPrevious() {
        intentionalSkip = true
        delegate.skipToPrevious()
    }

    override fun skipToNext() {
        intentionalSkip = true
        scope.launch {
            val queue = delegate.queueState.value
            val nextTrackId = queue.items.getOrNull(queue.currentIndex + 1)?.id
            if (nextTrackId != null && !isLocal(nextTrackId)) {
                runCatching { remoteResolver.prepareForPlayback(nextTrackId) }
            }
            delegate.skipToNext()
        }
    }

    private suspend fun recordHistoryTransition(playback: PlaybackState) {
        val newTrackId = playback.currentTrack?.id
        if (newTrackId != historyTrackId) {
            activeHistoryId?.let { playbackId ->
                historySafely {
                    history.finalizePlayback(
                        playbackId,
                        lastPlaybackState.positionMs,
                        lastPlaybackState.durationMs,
                        intentionalSkip,
                    )
                }
            }
            activeHistoryId = null
            historyTrackId = newTrackId
            intentionalSkip = false
            if (newTrackId != null) {
                val initiator = nextInitiator ?: when (playback.playbackMode) {
                    PlaybackMode.PURE_SHUFFLE -> PlaybackInitiator.PURE_SHUFFLE
                    PlaybackMode.SMART_SHUFFLE -> PlaybackInitiator.SMART_SHUFFLE
                    PlaybackMode.ORDERED -> PlaybackInitiator.QUEUE
                }
                if (historyEnabledSafely()) {
                    activeHistoryId = historySafely {
                        history.startPlayback(newTrackId, initiator, playback.playbackMode)
                    }
                }
                nextInitiator = null
            }
        }
        val playbackId = activeHistoryId
        if (
            playbackId != null && playback.durationMs > 0L &&
            ListeningEventSemantics.outcome(
                playback.positionMs,
                playback.durationMs,
                false,
            ) == ListeningEventType.PLAY_COMPLETED
        ) {
            historySafely {
                history.finalizePlayback(playbackId, playback.positionMs, playback.durationMs, false)
            }
            activeHistoryId = null
        }
        lastPlaybackState = playback
    }

    private suspend fun historyEnabledSafely(): Boolean = try {
        settingsRepository.networkPlaybackSettings.first().listeningHistoryEnabled
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }

    private suspend fun <T> historySafely(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private fun resolveAndPlayNow(trackId: UUID) {
        scope.launch {
            if (resolvingRemote) return@launch
            if (isLocal(trackId)) {
                delegate.playNow(trackId)
                return@launch
            }

            resolvingRemote = true
            _state.value = delegate.state.value.copy(
                status = PlaybackStatus.DOWNLOADING,
                errorMessage = null,
            )
            runCatching { remoteResolver.prepareForPlayback(trackId) }
                .onSuccess { prepared ->
                    resolvingRemote = false
                    if (prepared == null) {
                        _state.value = delegate.state.value.copy(
                            status = PlaybackStatus.ERROR,
                            errorMessage = "This Telegram track is no longer available.",
                        )
                    } else {
                        delegate.playNow(trackId)
                    }
                }
                .onFailure { error ->
                    resolvingRemote = false
                    _state.value = delegate.state.value.copy(
                        status = PlaybackStatus.ERROR,
                        errorMessage = error.message?.takeIf(String::isNotBlank)
                            ?: "Unable to buffer this Telegram track.",
                    )
                }
        }
    }

    private suspend fun isLocal(trackId: UUID): Boolean = runCatching {
        catalog.isTrackLocallyPlayable(trackId)
    }.getOrDefault(false)
}
