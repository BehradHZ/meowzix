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
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.ProgressiveQueue
import dev.behradhz.meowzix.domain.playback.PureShuffleEngine
import dev.behradhz.meowzix.domain.playback.QueueActionFeedbackBus
import dev.behradhz.meowzix.domain.playback.QueueActionKind
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
    private val progressiveQueue: ProgressiveQueue,
    private val queueActionFeedbackBus: QueueActionFeedbackBus,
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
            val tracks = runCatching { catalog.availableTracks().distinctBy(PlayableTrack::id) }
                .getOrElse { error ->
                    showError(error.message ?: "Unable to load the playback queue")
                    return@launch
                }
            val startIndex = tracks.indexOfFirst { it.id == trackId }
            if (startIndex < 0) {
                showError("This track is no longer available")
                return@launch
            }
            val connected = runCatching { controller ?: controllerFuture.await().also { controller = it } }
                .getOrElse { error ->
                    showError(error.message ?: "Playback is unavailable")
                    return@launch
                }
            val window = progressiveQueue.start(
                orderedTracks = tracks,
                requestedStartIndex = startIndex,
                mode = PlaybackMode.ORDERED,
                repeat = RepeatMode.OFF,
            )
            connected.repeatMode = Player.REPEAT_MODE_OFF
            connected.setMediaItems(
                window.tracks.map { it.toMediaItem(PlaybackMode.ORDERED, RepeatMode.OFF) },
                window.startIndexInWindow,
                0,
            )
            connected.prepare()
            connected.play()
        }
    }

    override fun playAt(index: Int) {
        withController { connected ->
            val active = progressiveQueue.snapshot()
            if (active != null) {
                val plan = progressiveQueue.jumpTo(index) ?: return@withController
                val snapshot = progressiveQueue.snapshot() ?: return@withController
                connected.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(progressive = true)
                connected.setMediaItems(
                    plan.tracks.map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) },
                    plan.startIndexInWindow,
                    0,
                )
                connected.prepare()
                connected.play()
            } else if (index in 0 until connected.mediaItemCount) {
                connected.seekToDefaultPosition(index)
                if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
                connected.play()
            }
        }
    }

    override fun playNext(trackId: UUID) {
        scope.launch {
            val track = findTrack(trackId) ?: return@launch
            withConnectedController { connected ->
                val active = progressiveQueue.snapshot()
                if (active != null) {
                    if (progressiveQueue.insertNext(track)) {
                        // Never replace/prepare the active MediaItem for a manual queue edit. Media3
                        // can reconcile the bounded window in place without interrupting audio.
                        syncProgressiveWindowInPlace(connected)
                        queueActionFeedbackBus.emit(QueueActionKind.PLAY_NEXT, track.title)
                    }
                    return@withConnectedController
                }

                val currentIndex = connected.currentMediaItemIndex
                val existingIndex = connected.indexOfTrack(trackId)
                if (existingIndex != null) {
                    if (existingIndex == currentIndex) return@withConnectedController
                    val destination = if (existingIndex < currentIndex) currentIndex else currentIndex + 1
                    connected.moveMediaItem(existingIndex, destination.coerceIn(0, connected.mediaItemCount - 1))
                } else {
                    val insertionIndex = (currentIndex + 1).coerceIn(0, connected.mediaItemCount)
                    connected.addMediaItem(
                        insertionIndex,
                        track.toMediaItem(connected.queuePlaybackMode(), connected.queueRepeatMode()),
                    )
                }
                updateState(connected)
                queueActionFeedbackBus.emit(QueueActionKind.PLAY_NEXT, track.title)
            }
        }
    }

    override fun addToQueue(trackId: UUID) {
        scope.launch {
            val track = findTrack(trackId) ?: return@launch
            withConnectedController { connected ->
                val active = progressiveQueue.snapshot()
                if (active != null) {
                    val existingIndex = active.tracks.indexOfFirst { it.id == trackId }
                    val affectsMaterializedWindow =
                        existingIndex >= 0 && existingIndex < active.materializedEndExclusive
                    if (progressiveQueue.append(track)) {
                        if (affectsMaterializedWindow) {
                            // Repositioning an item that Media3 already materialized must reconcile
                            // that bounded window, but the currently playing item stays untouched.
                            syncProgressiveWindowInPlace(connected)
                        } else {
                            // A normal Add to queue targets the logical tail, usually far beyond the
                            // active Media3 window. Do not send any timeline command in that case:
                            // it can cause a tiny audible hiccup even though playback itself is not
                            // being replaced. Only materialize more items when the forward window is
                            // actually close to running out.
                            val forwardBatch = progressiveQueue.takeForwardBatchIfNeeded()
                            if (forwardBatch.isNotEmpty()) {
                                val snapshot = progressiveQueue.snapshot()
                                if (snapshot != null) {
                                    connected.addMediaItems(
                                        forwardBatch.map {
                                            it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode)
                                        },
                                    )
                                }
                            }
                            updateState(connected)
                        }
                        queueActionFeedbackBus.emit(QueueActionKind.ADD_TO_END, track.title)
                    }
                    return@withConnectedController
                }

                val existingIndex = connected.indexOfTrack(trackId)
                if (existingIndex != null) {
                    if (
                        existingIndex == connected.currentMediaItemIndex ||
                        existingIndex == connected.mediaItemCount - 1
                    ) {
                        return@withConnectedController
                    }
                    connected.moveMediaItem(existingIndex, connected.mediaItemCount - 1)
                } else {
                    connected.addMediaItem(
                        track.toMediaItem(connected.queuePlaybackMode(), connected.queueRepeatMode()),
                    )
                }
                updateState(connected)
                queueActionFeedbackBus.emit(QueueActionKind.ADD_TO_END, track.title)
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
                    val requestedById = requested.associateBy { it.id }
                    PureShuffleEngine.newCycle(requested.map { it.id }).let { cycle ->
                        shuffleSeed = cycle.seed
                        cycle.order.mapNotNull(requestedById::get)
                    }
                }
                PlaybackMode.SMART_SHUFFLE -> {
                    val ids = recommendationEngine.generate(
                        allowedTrackIds = requested.map { it.id },
                        timeBucket = currentTimeBucket(),
                    ).trackIds.distinct()
                    val requestedById = requested.associateBy { it.id }
                    val ranked = ids.mapNotNull(requestedById::get)
                    ranked + requested.filterNot { track -> ranked.any { it.id == track.id } }
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

            withConnectedController { connected ->
                val window = progressiveQueue.start(
                    orderedTracks = tracks,
                    requestedStartIndex = startIndex,
                    mode = mode,
                    repeat = RepeatMode.OFF,
                    seed = shuffleSeed,
                )
                connected.setMediaItems(
                    window.tracks.map { it.toMediaItem(mode, RepeatMode.OFF) },
                    window.startIndexInWindow,
                    0,
                )
                connected.repeatMode = Player.REPEAT_MODE_OFF
                connected.prepare()
                connected.play()
            }
        }
    }

    override fun removeAt(index: Int) {
        withController { connected ->
            val before = progressiveQueue.snapshot()
            if (before != null) {
                val removingCurrent = index == before.currentIndex
                if (progressiveQueue.removeAt(index)) {
                    if (progressiveQueue.snapshot() == null) {
                        connected.clearMediaItems()
                    } else {
                        rebuildProgressiveWindow(connected, preservePosition = !removingCurrent)
                    }
                }
            } else if (index in 0 until connected.mediaItemCount) {
                connected.removeMediaItem(index)
            }
        }
    }

    override fun move(fromIndex: Int, toIndex: Int) {
        withController { connected ->
            if (progressiveQueue.snapshot() != null) {
                if (progressiveQueue.move(fromIndex, toIndex)) syncProgressiveWindowInPlace(connected)
            } else if (fromIndex in 0 until connected.mediaItemCount && toIndex in 0 until connected.mediaItemCount) {
                connected.moveMediaItem(fromIndex, toIndex)
            }
        }
    }

    override fun clear() = withController { connected ->
        val playerCurrentIndex = connected.currentMediaItemIndex
        val hasCurrentItem = connected.currentMediaItem != null &&
            playerCurrentIndex in 0 until connected.mediaItemCount
        if (!hasCurrentItem) {
            progressiveQueue.clear()
            connected.clearMediaItems()
            return@withController
        }

        val hasLogicalQueue = progressiveQueue.snapshot() != null
        if (hasLogicalQueue) {
            progressiveQueue.updateCurrent(connected.currentMediaItem?.mediaId)
            if (!progressiveQueue.retainCurrentOnly()) progressiveQueue.clear()
        }

        if (playerCurrentIndex + 1 < connected.mediaItemCount) {
            connected.removeMediaItems(playerCurrentIndex + 1, connected.mediaItemCount)
        }
        if (playerCurrentIndex > 0) {
            connected.removeMediaItems(0, playerCurrentIndex)
        }
        connected.repeatMode = Player.REPEAT_MODE_OFF
        updateState(connected)
    }

    override fun setPlaybackMode(mode: PlaybackMode) {
        scope.launch {
            val connected = runCatching { controller ?: controllerFuture.await().also { controller = it } }
                .getOrElse { error ->
                    showError(error.message ?: "Playback is unavailable")
                    return@launch
                }
            val active = progressiveQueue.snapshot()
            if (active == null) {
                setLegacyPlaybackMode(connected, mode)
                return@launch
            }
            if (active.tracks.isEmpty()) return@launch
            val currentTrack = active.tracks[active.currentIndex]
            val future = active.tracks.drop(active.currentIndex + 1)
            var seed: Long? = null
            val desiredFuture = when (mode) {
                PlaybackMode.ORDERED -> {
                    val rank = runCatching { catalog.availableTracks().map { it.id } }
                        .getOrDefault(emptyList())
                        .withIndex()
                        .associate { it.value to it.index }
                    future.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
                }
                PlaybackMode.PURE_SHUFFLE -> {
                    val byId = future.associateBy { it.id }
                    PureShuffleEngine.newCycle(future.map { it.id }).let { cycle ->
                        seed = cycle.seed
                        cycle.order.mapNotNull(byId::get)
                    }
                }
                PlaybackMode.SMART_SHUFFLE -> {
                    val byId = future.associateBy { it.id }
                    val rankedIds = recommendationEngine.generate(
                        allowedTrackIds = future.map { it.id },
                        currentTrackId = currentTrack.id,
                        timeBucket = currentTimeBucket(),
                    ).trackIds.distinct()
                    val ranked = rankedIds.mapNotNull(byId::get)
                    ranked + future.filterNot { candidate -> ranked.any { it.id == candidate.id } }
                }
            }
            progressiveQueue.replaceFuture(desiredFuture, mode, seed)
            syncProgressiveFutureInPlace(connected)
        }
    }

    override fun setRepeatMode(mode: RepeatMode) = withController { connected ->
        val progressive = progressiveQueue.snapshot() != null
        progressiveQueue.updateRepeatMode(mode)
        val playbackMode = progressiveQueue.snapshot()?.playbackMode ?: connected.queuePlaybackMode()
        connected.repeatMode = mode.toPlayerRepeatMode(progressive = progressive)
        if (progressive) {
            // The logical queue is authoritative in progressive mode. Replacing the currently
            // playing MediaItem just to update metadata can force ExoPlayer to rebuffer briefly.
            updateState(connected)
        } else {
            updateQueuePolicyMetadata(connected, playbackMode, mode)
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

    override fun skipToPrevious() = withController { connected ->
        if (connected.hasPreviousMediaItem()) {
            connected.seekToPrevious()
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        }
    }

    override fun skipToNext() = withController { connected ->
        if (connected.hasNextMediaItem()) {
            connected.seekToNext()
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        scope.launch {
            runCatching { controller ?: controllerFuture.await().also { controller = it } }
                .onSuccess(action)
                .onFailure { error -> showError(error.message ?: "Playback is unavailable") }
        }
    }

    private suspend fun withConnectedController(action: (MediaController) -> Unit) {
        val connected = runCatching { controller ?: controllerFuture.await().also { controller = it } }
            .getOrElse { error ->
                showError(error.message ?: "Playback is unavailable")
                return
            }
        action(connected)
    }

    /**
     * Applies a progressive mode change without replacing the active MediaItem. Keeping the current
     * item attached to ExoPlayer avoids a prepare/buffer cycle and therefore keeps audio continuous.
     */
    private fun syncProgressiveFutureInPlace(connected: MediaController) {
        if (!progressiveQueue.updateCurrent(connected.currentMediaItem?.mediaId)) return
        val snapshot = progressiveQueue.snapshot() ?: return
        val playerCurrentIndex = connected.currentMediaItemIndex
        if (playerCurrentIndex !in 0 until connected.mediaItemCount) return

        val logicalFutureStart = snapshot.currentIndex + 1
        val logicalFutureEnd = snapshot.materializedEndExclusive
            .coerceIn(logicalFutureStart, snapshot.tracks.size)
        val desiredFuture = snapshot.tracks
            .subList(logicalFutureStart, logicalFutureEnd)
            .map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) }
        val playerFutureStart = playerCurrentIndex + 1

        if (playerFutureStart < connected.mediaItemCount) {
            connected.removeMediaItems(playerFutureStart, connected.mediaItemCount)
        }
        if (desiredFuture.isNotEmpty()) {
            connected.addMediaItems(desiredFuture)
        }
        connected.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(progressive = true)
        updateState(connected)
    }

    /** Reconciles a reordered progressive window without resetting the active MediaItem. */
    private fun syncProgressiveWindowInPlace(connected: MediaController) {
        val currentMediaId = connected.currentMediaItem?.mediaId ?: return
        if (!progressiveQueue.updateCurrent(currentMediaId)) return
        val plan = progressiveQueue.resetWindow()
        val snapshot = progressiveQueue.snapshot() ?: return
        plan.tracks.forEachIndexed { targetIndex, track ->
            val desiredId = track.id.toString()
            if (targetIndex < connected.mediaItemCount && connected.getMediaItemAt(targetIndex).mediaId == desiredId) {
                return@forEachIndexed
            }
            val existingIndex = (targetIndex + 1 until connected.mediaItemCount)
                .firstOrNull { index -> connected.getMediaItemAt(index).mediaId == desiredId }
            if (existingIndex != null) {
                connected.moveMediaItem(existingIndex, targetIndex)
            } else {
                connected.addMediaItem(targetIndex, track.toMediaItem(snapshot.playbackMode, snapshot.repeatMode))
            }
        }
        while (connected.mediaItemCount > plan.tracks.size) {
            connected.removeMediaItem(connected.mediaItemCount - 1)
        }
        connected.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(progressive = true)
        updateState(connected)
    }

    private fun rebuildProgressiveWindow(
        connected: MediaController,
        preservePosition: Boolean = true,
    ) {
        val currentMediaId = connected.currentMediaItem?.mediaId
        progressiveQueue.updateCurrent(currentMediaId)
        val snapshot = progressiveQueue.snapshot() ?: return
        val positionMs = if (preservePosition) connected.currentPosition.coerceAtLeast(0) else 0L
        val shouldPlay = connected.playWhenReady
        val plan = progressiveQueue.resetWindow()
        connected.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(progressive = true)
        connected.setMediaItems(
            plan.tracks.map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) },
            plan.startIndexInWindow,
            positionMs,
        )
        connected.prepare()
        if (shouldPlay) connected.play()
    }

    private fun setLegacyPlaybackMode(connected: MediaController, mode: PlaybackMode) {
        if (connected.mediaItemCount == 0) return
        val repeatMode = connected.queueRepeatMode()
        val currentIndex = connected.currentMediaItemIndex.coerceAtLeast(0)
        val currentId = connected.currentMediaItem?.mediaId
        val existingIds = (0 until connected.mediaItemCount).map { connected.getMediaItemAt(it).mediaId }
        scope.launch {
            val desiredFutureIds = when (mode) {
                PlaybackMode.ORDERED -> {
                    val order = runCatching { catalog.availableTracks().map { it.id.toString() } }.getOrDefault(emptyList())
                    val rank = order.withIndex().associate { it.value to it.index }
                    existingIds.drop(currentIndex + 1).sortedBy { rank[it] ?: Int.MAX_VALUE }
                }
                PlaybackMode.PURE_SHUFFLE -> existingIds.drop(currentIndex + 1).shuffled()
                PlaybackMode.SMART_SHUFFLE -> {
                    val allowed = existingIds.drop(currentIndex + 1)
                        .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                    recommendationEngine.generate(
                        allowedTrackIds = allowed,
                        currentTrackId = currentId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        timeBucket = currentTimeBucket(),
                    ).trackIds.map(UUID::toString).filterNot { it == currentId }
                }
            }
            desiredFutureIds.forEachIndexed { offset, mediaId ->
                val destination = currentIndex + 1 + offset
                val sourceIndex = (destination until connected.mediaItemCount)
                    .firstOrNull { connected.getMediaItemAt(it).mediaId == mediaId }
                    ?: return@forEachIndexed
                if (sourceIndex != destination) connected.moveMediaItem(sourceIndex, destination)
            }
            updateQueuePolicyMetadata(connected, mode, repeatMode)
        }
    }

    private fun updateState(player: Player?, errorMessage: String? = player?.playerError?.message) {
        if (player == null) return
        val mediaItem = player.currentMediaItem
        val trackId = mediaItem?.mediaId?.let { mediaId ->
            runCatching { UUID.fromString(mediaId) }.getOrNull()
        }
        progressiveQueue.updateCurrent(mediaItem?.mediaId)
        val logical = progressiveQueue.snapshot()
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 }
            ?: mediaItem?.mediaMetadata?.durationMs?.coerceAtLeast(0)
            ?: 0L
        val playbackMode = logical?.playbackMode ?: player.queuePlaybackMode()
        val repeatMode = logical?.repeatMode ?: player.queueRepeatMode()
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
            queueIndex = logical?.currentIndex ?: player.currentMediaItemIndex,
            queueSize = logical?.tracks?.size ?: player.mediaItemCount,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            playbackMode = playbackMode,
            repeatMode = repeatMode,
            canSkipPrevious = logical?.let { it.currentIndex > 0 } ?: player.hasPreviousMediaItem(),
            canSkipNext = logical?.let { it.currentIndex < it.tracks.lastIndex } ?: player.hasNextMediaItem(),
            errorMessage = errorMessage,
        )
        _queueState.value = logical?.let { snapshot ->
            QueueState(
                items = snapshot.tracks.map(PlayableTrack::toQueueItem),
                currentIndex = snapshot.currentIndex,
                playbackMode = snapshot.playbackMode,
                repeatMode = snapshot.repeatMode,
            )
        } ?: QueueState(
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
            playbackMode = playbackMode,
            repeatMode = repeatMode,
        )
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

private fun PlayableTrack.toQueueItem() = QueueItem(
    id = id,
    title = title,
    artist = artist,
    artworkRef = artworkRef,
)

private fun Player.queuePlaybackMode(): PlaybackMode =
    if (mediaItemCount == 0) PlaybackMode.ORDERED else getMediaItemAt(0).playbackMode()

private fun Player.queueRepeatMode(): RepeatMode =
    if (mediaItemCount == 0) RepeatMode.OFF else getMediaItemAt(0).repeatMode()

private fun Player.indexOfTrack(trackId: UUID): Int? =
    (0 until mediaItemCount)
        .firstOrNull { index -> getMediaItemAt(index).mediaId == trackId.toString() }

private fun Player.containsTrack(trackId: UUID): Boolean = indexOfTrack(trackId) != null

private fun updateQueuePolicyMetadata(player: MediaController, playbackMode: PlaybackMode, repeatMode: RepeatMode) {
    for (index in 0 until player.mediaItemCount) {
        val current = player.getMediaItemAt(index)
        player.replaceMediaItem(index, current.withQueuePolicy(playbackMode, repeatMode))
    }
}

private fun RepeatMode.toPlayerRepeatMode(progressive: Boolean): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && !progressive -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}

private fun currentTimeBucket() = ListeningEventSemantics.timeContext(Instant.now(), ZoneId.systemDefault()).bucket