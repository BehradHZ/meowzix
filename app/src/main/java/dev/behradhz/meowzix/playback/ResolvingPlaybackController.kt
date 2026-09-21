package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.domain.playback.RemoteTrackPlaybackResolver
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class ResolvingPlaybackController @Inject constructor(
    private val delegate: AndroidPlaybackController,
    private val catalog: PlaybackCatalog,
    private val remoteResolver: RemoteTrackPlaybackResolver,
) : PlaybackController, QueueRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(delegate.state.value)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    override val queueState: StateFlow<QueueState> = delegate.queueState

    @Volatile
    private var resolvingRemote = false

    init {
        scope.launch {
            delegate.state.collect { playback ->
                if (!resolvingRemote) _state.value = playback
            }
        }
        scope.launch {
            delegate.queueState
                .map { queue -> queue.items.getOrNull(queue.currentIndex + 1)?.id }
                .distinctUntilChanged()
                .collect { nextTrackId ->
                    remoteResolver.cancelPrefetch()
                    if (nextTrackId != null) {
                        val local = runCatching {
                            catalog.availableLocalTracks().any { it.id == nextTrackId }
                        }.getOrDefault(false)
                        if (!local) remoteResolver.prefetch(nextTrackId)
                    }
                }
        }
    }

    override fun playTrack(trackId: UUID) = resolveAndPlayNow(trackId)

    override fun playNow(trackId: UUID) = resolveAndPlayNow(trackId)

    override fun playNext(trackId: UUID) = delegate.playNext(trackId)

    override fun addToQueue(trackId: UUID) = delegate.addToQueue(trackId)

    override fun replaceAndPlay(trackIds: List<UUID>, mode: PlaybackMode) {
        scope.launch {
            resolvingRemote = true
            _state.value = delegate.state.value.copy(status = PlaybackStatus.PREPARING, errorMessage = null)
            runCatching {
                val localIds = catalog.availableLocalTracks().mapTo(mutableSetOf()) { it.id }
                trackIds.filterNot(localIds::contains).forEach { remoteResolver.prepareForPlayback(it) }
                delegate.replaceAndPlay(trackIds, mode)
            }.onFailure { error ->
                _state.value = delegate.state.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = error.message ?: "Unable to prepare playlist.",
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

    override fun seekTo(positionMs: Long) = delegate.seekTo(positionMs)

    override fun skipToPrevious() = delegate.skipToPrevious()

    override fun skipToNext() = delegate.skipToNext()

    private fun resolveAndPlayNow(trackId: UUID) {
        scope.launch {
            if (resolvingRemote) return@launch
            val isAlreadyLocal = runCatching {
                catalog.availableLocalTracks().any { it.id == trackId }
            }.getOrDefault(false)
            if (isAlreadyLocal) {
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
                            ?: "Unable to download this Telegram track.",
                    )
                }
        }
    }
}
