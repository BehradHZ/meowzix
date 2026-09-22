package dev.behradhz.meowzix.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
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
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class AndroidPlaybackController @Inject constructor(
    @ApplicationContext context: Context,
    private val catalog: PlaybackCatalog,
    private val recommendationEngine: RecommendationEngine,
    private val windowedQueue: WindowedPlaybackQueue,
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
    private var lastPublishedQueueRevision = Long.MIN_VALUE

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            windowedQueue.updateCurrent(player.currentMediaItem?.mediaId)
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
                    windowedQueue.updateCurrent(connected.currentMediaItem?.mediaId)
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
            val tracks = runCatching { catalog.availableTracks() }
                .getOrElse { error ->
                    showError(error.message ?: "Unable to load the playback queue")
                    return@launch
                }
            val startIndex = tracks.indexOfFirst { it.id == trackId }
            if (startIndex < 0) {
                showError("This track is no longer available")
                return@launch
            }
            val connected = controllerOrNull() ?: return@launch
            windowedQueue.reset(
                tracks = tracks,
                currentIndex = startIndex,
                playbackMode = PlaybackMode.ORDERED,
                repeatMode = RepeatMode.OFF,
            )
            materializeCurrentWindow(connected, positionMs = 0L, shouldPlay = true)
        }
    }

    override fun playNext(trackId: UUID) {
        scope.launch {
            val track = findTrack(trackId) ?: return@launch
            val connected = controllerOrNull() ?: return@launch
            bootstrapQueueIfNeeded(connected)
            windowedQueue.updateCurrent(connected.currentMediaItem?.mediaId)
            if (windowedQueue.insertNext(track)) {
                rematerializePreservingCurrent(connected)
            }
        }
    }

    override fun addToQueue(trackId: UUID) {
        scope.launch {
            val track = findTrack(trackId) ?: return@launch
            val connected = controllerOrNull() ?: return@launch
            bootstrapQueueIfNeeded(connected)
            windowedQueue.updateCurrent(connected.currentMediaItem?.mediaId)
            if (windowedQueue.append(track)) {
                ensureAheadLoaded(connected)
                publishQueueState(connected)
            }
        }
    }

    override fun replaceAndPlay(trackIds: List<UUID>, mode: PlaybackMode) {
        replaceAndPlayInternal(trackIds, null, mode)
    }

    override fun replaceAndPlay(trackIds: List<UUID>, startTrackId: UUID, mode: PlaybackMode) {
        replaceAndPlayInternal(trackIds, startTrackId, mode)
    }

    private fun replaceAndPlayInternal(
        trackIds: List<UUID>,
        startTrackId: UUID?,
        mode: PlaybackMode,
    ) {
        scope.launch {
            val byId = runCatching { catalog.availableTracks().associateBy { it.id } }
                .getOrElse { error ->
                    showError(error.message ?: "Unable to load playlist")
                    return@launch
                }
            val requested = trackIds.distinct().mapNotNull(byId::get)
            var shuffleSeed: Long? = null
            var tracks = when (mode) {
                PlaybackMode.ORDERED -> requested
                PlaybackMode.PURE_SHUFFLE -> {
                    val cycle = PureShuffleEngine.newCycle(requested.map { it.id })
                    shuffleSeed = cycle.seed
                    cycle.order.mapNotNull(byId::get)
                }
                PlaybackMode.SMART_SHUFFLE -> {
                    val ids = recommendationEngine.generate(
                        allowedTrackIds = requested.map { it.id },
                        timeBucket = currentTimeBucket(),
                    ).trackIds.distinct()
                    ids.mapNotNull(byId::get)
                }
            }
            if (tracks.isEmpty()) return@launch showError("No playlist tracks are available")

            if (startTrackId != null && mode != PlaybackMode.ORDERED) {
                val selectedIndex = tracks.indexOfFirst { it.id == startTrackId }
                if (selectedIndex > 0) tracks = tracks.drop(selectedIndex) + tracks.take(selectedIndex)
            }
            val startIndex = when {
                startTrackId == null -> 0
                mode == PlaybackMode.ORDERED -> tracks.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
                else -> 0
            }

            val connected = controllerOrNull() ?: return@launch
            windowedQueue.reset(
                tracks = tracks,
                currentIndex = startIndex,
                playbackMode = mode,
                repeatMode = RepeatMode.OFF,
                shuffleSeed = shuffleSeed,
            )
            materializeCurrentWindow(connected, positionMs = 0L, shouldPlay = true)
        }
    }

    override fun removeAt(index: Int) {
        scope.launch {
            val connected = controllerOrNull() ?: return@launch
            bootstrapQueueIfNeeded(connected)
            windowedQueue.updateCurrent(connected.currentMediaItem?.mediaId)
            val previousCurrentId = connected.currentMediaItem?.mediaId
            if (windowedQueue.removeAt(index)) {
                val currentStillExists = previousCurrentId?.let { id ->
                    runCatching { UUID.fromString(id) }.getOrNull()?.let(windowedQueue::contains)
                } == true
                rematerializePreservingCurrent(connected, preservePosition = currentStillExists)
            }
        }
    }

    override fun move(fromIndex: Int, toIndex: Int) {
        scope.launch {
            val connected = controllerOrNull() ?: return@launch
            bootstrapQueueIfNeeded(connected)
            windowedQueue.updateCurrent(connected.currentMediaItem?.mediaId)
            if (windowedQueue.move(fromIndex, toIndex)) {
                rematerializePreservingCurrent(connected)
            }
        }
    }

    override fun clear() {
        windowedQueue.clear()
        lastPublishedQueueRevision = Long.MIN_VALUE
        withController { connected ->
            connected.clearMediaItems()
            publishQueueState(connected)
        }
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        scope.launch {
            val connected = controllerOrNull() ?: return@launch
            if (connected.mediaItemCount == 0) return@launch
            bootstrapQueueIfNeeded(connected)
            windowedQueue.updateCurrent(connected.currentMediaItem?.mediaId)
            val snapshot = windowedQueue.snapshot()
            if (snapshot.currentIndex !in snapshot.tracks.indices) return@launch
            val currentId = snapshot.tracks[snapshot.currentIndex].id
            val prefix = snapshot.tracks.take(snapshot.currentIndex + 1)
            val future = snapshot.tracks.drop(snapshot.currentIndex + 1)
            var shuffleSeed: Long? = null
            val desiredFuture = when (mode) {
                PlaybackMode.ORDERED -> {
                    val canonicalOrder = runCatching { catalog.availableTracks().map { it.id } }.getOrDefault(emptyList())
                    val rank = canonicalOrder.withIndex().associate { it.value to it.index }
                    future.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
                }
                PlaybackMode.PURE_SHUFFLE -> {
                    val byId = future.associateBy { it.id }
                    val cycle = PureShuffleEngine.newCycle(future.map { it.id })
                    shuffleSeed = cycle.seed
                    cycle.order.mapNotNull(byId::get)
                }
                PlaybackMode.SMART_SHUFFLE -> {
                    val byId = future.associateBy { it.id }
                    recommendationEngine.generate(
                        allowedTrackIds = future.map { it.id },
                        currentTrackId = currentId,
                        timeBucket = currentTimeBucket(),
                    ).trackIds.distinct().mapNotNull(byId::get)
                }
            }
            windowedQueue.resetAtTrack(
                tracks = prefix + desiredFuture,
                currentTrackId = currentId,
                playbackMode = mode,
                repeatMode = snapshot.repeatMode,
                shuffleSeed = shuffleSeed,
            )
            connected.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(mode)
            rematerializePreservingCurrent(connected)
        }
    }

    override fun setRepeatMode(mode: RepeatMode) = withController { connected ->
        bootstrapQueueIfNeeded(connected)
        windowedQueue.setRepeatMode(mode)
        val playbackMode = windowedQueue.snapshot().playbackMode
        connected.repeatMode = mode.toPlayerRepeatMode(playbackMode)
        updateQueuePolicyMetadata(connected, playbackMode, mode)
        publishQueueState(connected)
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

    override fun skipToPrevious() = withController { connected ->
        if (connected.hasPreviousMediaItem()) {
            connected.seekToPrevious()
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        }
    }

    override fun skipToNext() = withController { connected ->
        ensureAheadLoaded(connected)
        if (connected.hasNextMediaItem()) {
            connected.seekToNext()
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        }
    }

    private suspend fun controllerOrNull(): MediaController? = runCatching {
        controller ?: controllerFuture.await().also { controller = it }
    }.onFailure { error ->
        showError(error.message ?: "Playback is unavailable")
    }.getOrNull()

    private fun withController(action: (MediaController) -> Unit) {
        scope.launch {
            controllerOrNull()?.let(action)
        }
    }

    private fun materializeCurrentWindow(
        connected: MediaController,
        positionMs: Long,
        shouldPlay: Boolean,
    ) {
        val snapshot = windowedQueue.snapshot()
        val window = windowedQueue.materializedWindow()
        if (window.tracks.isEmpty()) {
            connected.clearMediaItems()
            publishQueueState(connected)
            return
        }
        connected.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(snapshot.playbackMode)
        connected.setMediaItems(
            window.tracks.map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) },
            window.currentIndex,
            positionMs.coerceAtLeast(0),
        )
        connected.prepare()
        if (shouldPlay) connected.play()
        publishQueueState(connected)
    }

    private fun rematerializePreservingCurrent(
        connected: MediaController,
        preservePosition: Boolean = true,
    ) {
        val currentId = connected.currentMediaItem?.mediaId
        windowedQueue.updateCurrent(currentId)
        val snapshot = windowedQueue.snapshot()
        val selectedId = snapshot.tracks.getOrNull(snapshot.currentIndex)?.id?.toString()
        val keepPosition = preservePosition && currentId != null && currentId == selectedId
        val position = if (keepPosition) connected.currentPosition.coerceAtLeast(0) else 0L
        val shouldPlay = connected.playWhenReady
        materializeCurrentWindow(connected, position, shouldPlay)
    }

    private fun ensureAheadLoaded(connected: MediaController) {
        if (!windowedQueue.isActive() || connected.mediaItemCount == 0) return
        windowedQueue.updateCurrent(connected.currentMediaItem?.mediaId)
        if (!windowedQueue.shouldRefillAhead(connected.currentMediaItemIndex, connected.mediaItemCount)) return
        val lastId = connected.getMediaItemAt(connected.mediaItemCount - 1).mediaId
        val snapshot = windowedQueue.snapshot()
        val refill = windowedQueue.nextBatchAfter(lastId)
        if (refill.isNotEmpty()) {
            connected.addMediaItems(refill.map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) })
        }
    }

    private fun bootstrapQueueIfNeeded(player: Player) {
        if (windowedQueue.isActive() || player.mediaItemCount == 0) return
        val tracks = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).toPersistedPlaybackItem()?.toPlayableTrack()
        }
        if (tracks.isEmpty()) return
        val currentId = player.currentMediaItem?.mediaId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        windowedQueue.resetAtTrack(
            tracks = tracks,
            currentTrackId = currentId,
            playbackMode = player.queuePlaybackMode(),
            repeatMode = player.queueRepeatMode(),
        )
    }

    private fun updateState(player: Player?, errorMessage: String? = player?.playerError?.message) {
        if (player == null) return
        windowedQueue.updateCurrent(player.currentMediaItem?.mediaId)
        val mediaItem = player.currentMediaItem
        val trackId = mediaItem?.mediaId?.let { mediaId ->
            runCatching { UUID.fromString(mediaId) }.getOrNull()
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 }
            ?: mediaItem?.mediaMetadata?.durationMs?.coerceAtLeast(0)
            ?: 0L
        val snapshot = windowedQueue.snapshot().takeIf { windowedQueue.isActive() }
        val logicalIndex = snapshot?.currentIndex ?: player.currentMediaItemIndex
        val logicalSize = snapshot?.tracks?.size ?: player.mediaItemCount
        val playbackMode = snapshot?.playbackMode ?: player.queuePlaybackMode()
        val repeatMode = snapshot?.repeatMode ?: player.queueRepeatMode()
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
            queueIndex = logicalIndex,
            queueSize = logicalSize,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            playbackMode = playbackMode,
            repeatMode = repeatMode,
            canSkipPrevious = logicalIndex > 0 || player.hasPreviousMediaItem(),
            canSkipNext = logicalIndex in 0 until logicalSize - 1 || player.hasNextMediaItem(),
            errorMessage = errorMessage,
        )
        publishQueueState(player)
    }

    private fun publishQueueState(player: Player) {
        val snapshot = windowedQueue.snapshot().takeIf { windowedQueue.isActive() }
        if (snapshot == null) {
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
            lastPublishedQueueRevision = Long.MIN_VALUE
            return
        }
        val items = if (snapshot.revision != lastPublishedQueueRevision) {
            snapshot.tracks.map { track ->
                QueueItem(
                    id = track.id,
                    title = track.title.ifBlank { "Unknown Track" },
                    artist = track.artist,
                    artworkRef = track.artworkRef,
                )
            }
        } else {
            _queueState.value.items
        }
        _queueState.value = QueueState(
            items = items,
            currentIndex = snapshot.currentIndex,
            playbackMode = snapshot.playbackMode,
            repeatMode = snapshot.repeatMode,
        )
        lastPublishedQueueRevision = snapshot.revision
    }

    private suspend fun findTrack(trackId: UUID) = runCatching { catalog.availableTracks() }
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

private fun updateQueuePolicyMetadata(player: MediaController, playbackMode: PlaybackMode, repeatMode: RepeatMode) {
    for (index in 0 until player.mediaItemCount) {
        val current = player.getMediaItemAt(index)
        player.replaceMediaItem(index, current.withQueuePolicy(playbackMode, repeatMode))
    }
}

private fun RepeatMode.toPlayerRepeatMode(playbackMode: PlaybackMode): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && playbackMode != PlaybackMode.PURE_SHUFFLE -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}

private fun currentTimeBucket() = ListeningEventSemantics.timeContext(Instant.now(), ZoneId.systemDefault()).bucket
