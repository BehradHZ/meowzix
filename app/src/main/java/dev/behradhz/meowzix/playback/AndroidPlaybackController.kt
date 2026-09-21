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
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.PureShuffleEngine
import dev.behradhz.meowzix.domain.playback.QueueItem
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.QueueState
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
) : PlaybackController, QueueRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState(status = PlaybackStatus.PREPARING))
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _queueState = MutableStateFlow(QueueState())
    override val queueState: StateFlow<QueueState> = _queueState.asStateFlow()
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

    override fun playTrack(trackId: UUID) = playNow(trackId)

    override fun playNow(trackId: UUID) {
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
                connected.repeatMode = Player.REPEAT_MODE_OFF
                connected.setMediaItems(
                    tracks.map { it.toMediaItem(PlaybackMode.ORDERED, RepeatMode.OFF) },
                    startIndex,
                    0,
                )
                connected.prepare()
                connected.play()
            }
        }
    }

    override fun playNext(trackId: UUID) {
        scope.launch {
            val track = findTrack(trackId) ?: return@launch
            withController { connected ->
                if (
                    connected.queuePlaybackMode() == PlaybackMode.PURE_SHUFFLE &&
                    connected.containsTrack(trackId)
                ) return@withController
                val insertionIndex = (connected.currentMediaItemIndex + 1)
                    .coerceIn(0, connected.mediaItemCount)
                connected.addMediaItem(
                    insertionIndex,
                    track.toMediaItem(connected.queuePlaybackMode(), connected.queueRepeatMode()),
                )
            }
        }
    }

    override fun addToQueue(trackId: UUID) {
        scope.launch {
            val track = findTrack(trackId) ?: return@launch
            withController { connected ->
                if (
                    connected.queuePlaybackMode() == PlaybackMode.PURE_SHUFFLE &&
                    connected.containsTrack(trackId)
                ) return@withController
                connected.addMediaItem(
                    track.toMediaItem(connected.queuePlaybackMode(), connected.queueRepeatMode()),
                )
            }
        }
    }

    override fun replaceAndPlay(trackIds: List<UUID>, mode: PlaybackMode) {
        scope.launch {
            val byId = runCatching { catalog.availableLocalTracks().associateBy { it.id } }
                .getOrElse { error ->
                    showError(error.message ?: "Unable to load playlist")
                    return@launch
                }
            val requested = trackIds.distinct().mapNotNull(byId::get)
            val tracks = when (mode) {
                PlaybackMode.ORDERED -> requested
                PlaybackMode.PURE_SHUFFLE -> PureShuffleEngine.newCycle(requested).order
            }
            if (tracks.isEmpty()) return@launch showError("No playlist tracks are available")
            withController { connected ->
                connected.setMediaItems(tracks.map { it.toMediaItem(mode, RepeatMode.OFF) }, 0, 0)
                connected.repeatMode = RepeatMode.OFF.toPlayerRepeatMode(mode)
                connected.prepare()
                connected.play()
            }
        }
    }

    override fun removeAt(index: Int) = withController { connected ->
        if (index in 0 until connected.mediaItemCount) connected.removeMediaItem(index)
    }

    override fun move(fromIndex: Int, toIndex: Int) = withController { connected ->
        if (fromIndex in 0 until connected.mediaItemCount && toIndex in 0 until connected.mediaItemCount) {
            connected.moveMediaItem(fromIndex, toIndex)
        }
    }

    override fun clear() = withController(MediaController::clearMediaItems)

    override fun setPlaybackMode(mode: PlaybackMode) {
        scope.launch {
            val tracks = runCatching { catalog.availableLocalTracks() }
                .getOrElse { error ->
                    showError(error.message ?: "Unable to load the playback queue")
                    return@launch
                }
            withController { connected ->
                val repeatMode = connected.queueRepeatMode()
                val currentId = connected.currentMediaItem?.mediaId
                val position = connected.currentPosition.coerceAtLeast(0)
                val wasPlaying = connected.isPlaying
                val orderedTracks = when (mode) {
                    PlaybackMode.ORDERED -> tracks
                    PlaybackMode.PURE_SHUFFLE -> {
                        val shuffled = PureShuffleEngine.newCycle(tracks).order
                        val currentIndex = shuffled.indexOfFirst { it.id.toString() == currentId }
                        if (currentIndex > 0) {
                            shuffled.drop(currentIndex) + shuffled.take(currentIndex)
                        } else {
                            shuffled
                        }
                    }
                }
                if (orderedTracks.isEmpty()) {
                    connected.clearMediaItems()
                    return@withController
                }
                val startIndex = when (mode) {
                    PlaybackMode.PURE_SHUFFLE -> 0
                    PlaybackMode.ORDERED -> orderedTracks.indexOfFirst { it.id.toString() == currentId }
                        .coerceAtLeast(0)
                }
                connected.repeatMode = repeatMode.toPlayerRepeatMode(mode)
                connected.setMediaItems(
                    orderedTracks.map { it.toMediaItem(mode, repeatMode) },
                    startIndex,
                    position,
                )
                connected.prepare()
                if (wasPlaying) connected.play()
            }
        }
    }

    override fun setRepeatMode(mode: RepeatMode) = withController { connected ->
        val playbackMode = connected.queuePlaybackMode()
        replaceQueuePolicy(connected, playbackMode, mode)
        connected.repeatMode = mode.toPlayerRepeatMode(playbackMode)
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
            playbackMode = player.queuePlaybackMode(),
            repeatMode = player.queueRepeatMode(),
            canSkipPrevious = player.hasPreviousMediaItem(),
            canSkipNext = player.hasNextMediaItem(),
            errorMessage = errorMessage,
        )
        _queueState.value = QueueState(
            items = (0 until player.mediaItemCount).mapNotNull { index ->
                val item = player.getMediaItemAt(index)
                runCatching { UUID.fromString(item.mediaId) }.getOrNull()?.let { id ->
                    QueueItem(
                        id = id,
                        title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown Track" },
                        artist = item.mediaMetadata.artist?.toString(),
                        artworkRef = item.mediaMetadata.artworkUri?.toString(),
                    )
                }
            },
            currentIndex = player.currentMediaItemIndex,
            playbackMode = player.queuePlaybackMode(),
            repeatMode = player.queueRepeatMode(),
        )
    }

    private suspend fun findTrack(trackId: UUID) = runCatching { catalog.availableLocalTracks() }
        .onFailure { error -> showError(error.message ?: "Unable to load the playback queue") }
        .getOrNull()
        ?.firstOrNull { it.id == trackId }
        .also { if (it == null) showError("This track is no longer available") }

    private fun showError(message: String) {
        _state.value = _state.value.copy(status = PlaybackStatus.ERROR, errorMessage = message)
    }

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
    }
}

private fun Player.queuePlaybackMode(): PlaybackMode =
    if (mediaItemCount == 0) PlaybackMode.ORDERED else getMediaItemAt(0).playbackMode()

private fun Player.queueRepeatMode(): RepeatMode =
    if (mediaItemCount == 0) RepeatMode.OFF else getMediaItemAt(0).repeatMode()

private fun Player.containsTrack(trackId: UUID): Boolean =
    (0 until mediaItemCount).any { index -> getMediaItemAt(index).mediaId == trackId.toString() }

private fun replaceQueuePolicy(player: MediaController, playbackMode: PlaybackMode, repeatMode: RepeatMode) {
    if (player.mediaItemCount == 0) return
    val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
    val position = player.currentPosition.coerceAtLeast(0)
    val wasPlaying = player.isPlaying
    val items = (0 until player.mediaItemCount)
        .map { index -> player.getMediaItemAt(index).withQueuePolicy(playbackMode, repeatMode) }
    player.setMediaItems(items, currentIndex.coerceIn(items.indices), position)
    player.prepare()
    if (wasPlaying) player.play()
}

private fun RepeatMode.toPlayerRepeatMode(playbackMode: PlaybackMode): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && playbackMode == PlaybackMode.ORDERED -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}
