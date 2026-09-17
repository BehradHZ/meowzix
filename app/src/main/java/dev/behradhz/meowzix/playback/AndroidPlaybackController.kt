package dev.behradhz.meowzix.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidPlaybackController @Inject constructor(
    @ApplicationContext context: Context,
    private val catalog: PlaybackCatalog,
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState(status = PlaybackStatus.PREPARING))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val controllerFuture = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            updateState(controller, error.message ?: "Unable to play this track")
        }
    }

    init {
        scope.launch {
            runCatching { controllerFuture.await() }
                .onSuccess { connected ->
                    controller = connected
                    connected.addListener(listener)
                    updateState(connected)
                }
                .onFailure { error ->
                    _state.value = PlaybackState(
                        status = PlaybackStatus.ERROR,
                        errorMessage = error.message ?: "Unable to connect to playback",
                    )
                }
        }
        scope.launch {
            while (isActive) {
                delay(POSITION_UPDATE_INTERVAL_MS)
                controller?.let(::updateState)
            }
        }
    }

    override fun playTrack(trackId: UUID) {
        scope.launch {
            val tracks = runCatching { catalog.availableLocalTracks() }
                .getOrElse { error ->
                    showError(error.message ?: "Unable to load the playback queue")
                    return@launch
                }
            val startIndex = tracks.indexOfFirst { it.id == trackId }
            if (startIndex < 0) {
                showError("This track is no longer available")
                return@launch
            }
            withController { connected ->
                connected.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0)
                connected.prepare()
                connected.play()
            }
        }
    }

    override fun resume() = withController { connected ->
        if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
        connected.play()
    }

    override fun pause() = withController(MediaController::pause)

    override fun togglePlayPause() = withController { connected ->
        if (connected.isPlaying) connected.pause() else {
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        }
    }

    override fun seekTo(positionMs: Long) = withController { connected ->
        val upperBound = connected.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: Long.MAX_VALUE
        connected.seekTo(positionMs.coerceIn(0, upperBound))
    }

    override fun skipToPrevious() = withController(MediaController::seekToPrevious)

    override fun skipToNext() = withController(MediaController::seekToNext)

    private fun withController(action: (MediaController) -> Unit) {
        scope.launch {
            runCatching { controller ?: controllerFuture.await().also { controller = it } }
                .onSuccess(action)
                .onFailure { error -> showError(error.message ?: "Playback is unavailable") }
        }
    }

    private fun updateState(player: Player?, errorMessage: String? = player?.playerError?.message) {
        if (player == null) return
        val mediaItem = player.currentMediaItem
        val trackId = mediaItem?.mediaId?.let { mediaId ->
            runCatching { UUID.fromString(mediaId) }.getOrNull()
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 }
            ?: mediaItem?.mediaMetadata?.durationMs?.coerceAtLeast(0)
            ?: 0L
        _state.value = PlaybackState(
            status = when {
                errorMessage != null -> PlaybackStatus.ERROR
                mediaItem == null -> PlaybackStatus.IDLE
                player.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                player.playbackState == Player.STATE_IDLE -> PlaybackStatus.PREPARING
                player.isPlaying -> PlaybackStatus.PLAYING
                else -> PlaybackStatus.PAUSED
            },
            currentTrack = trackId?.let { id ->
                NowPlayingTrack(
                    id = id,
                    title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown Track" },
                    artist = mediaItem.mediaMetadata.artist?.toString(),
                    artworkRef = mediaItem.mediaMetadata.artworkUri?.toString(),
                )
            },
            queueIndex = player.currentMediaItemIndex,
            queueSize = player.mediaItemCount,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            repeatMode = player.repeatMode.toDomainRepeatMode(),
            canSkipPrevious = player.hasPreviousMediaItem(),
            canSkipNext = player.hasNextMediaItem(),
            errorMessage = errorMessage,
        )
    }

    private fun showError(message: String) {
        _state.value = _state.value.copy(status = PlaybackStatus.ERROR, errorMessage = message)
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}

private fun Int.toDomainRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}
