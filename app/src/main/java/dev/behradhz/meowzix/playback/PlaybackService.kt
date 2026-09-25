package dev.behradhz.meowzix.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.MainActivity
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.ProgressiveQueue
import dev.behradhz.meowzix.domain.playback.PureShuffleEngine
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackSession
import dev.behradhz.meowzix.playback.persistence.PlaybackStateStore
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(markerClass = [UnstableApi::class])
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var stateStore: PlaybackStateStore
    @Inject lateinit var playbackCatalog: PlaybackCatalog
    @Inject lateinit var audioVisualizer: AudioVisualizerRepository
    @Inject lateinit var libraryRepository: MusicLibraryRepository
    @Inject lateinit var progressiveQueue: ProgressiveQueue

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var persistJob: Job? = null
    private var playbackRetryJob: Job? = null
    private var retryMediaId: String? = null
    private var retryCount = 0
    private var isRestoring = true
    private var isChangingQueueCycle = false
    private var isExpandingWindow = false
    private var currentFavorite = false

    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val repeatCommand = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: ControllerInfo,
        ): ListenableFuture<ConnectionResult> {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(shuffleCommand)
                .add(repeatCommand)
                .add(favoriteCommand)
                .build()
            return Futures.immediateFuture(
                AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(sessionCommands)
                    .build(),
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_SHUFFLE -> toggleSystemShuffle()
                ACTION_CYCLE_REPEAT -> cycleSystemRepeat()
                ACTION_TOGGLE_FAVORITE -> toggleSystemFavorite()
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) && player.mediaItemCount == 0) {
                progressiveQueue.clear()
            }
            if (
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_READY &&
                player.playerError == null
            ) {
                clearPlaybackRetry()
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_ENDED) {
                startNextQueueCycle()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                clearPlaybackRetry()
                progressiveQueue.updateCurrent(player.currentMediaItem?.mediaId)
                expandProgressiveWindow()
                runCatching { audioVisualizer.analyze(player.currentMediaItem?.localConfiguration?.uri?.toString()) }
                refreshFavoriteState()
            } else if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                expandProgressiveWindow()
            }
            if (
                events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) {
                refreshMediaButtons()
                schedulePersist()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            runCatching { audioVisualizer.attachToAudioSession(audioSessionId) }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackFailure(error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(MeowzixDataSourceFactory(this))
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekForwardIncrementMs(FORWARD_INCREMENT_MS)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { it.addListener(playerListener) }

        val openPlayerIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOW_PLAYING
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(openPlayerIntent)
            .setMediaButtonPreferences(mediaButtons())
            .build()

        serviceScope.launch {
            try {
                try {
                    restoreSession()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    isRestoring = false
                    progressiveQueue.clear()
                    player.clearMediaItems()
                }
                runCatching {
                    audioVisualizer.analyze(player.currentMediaItem?.localConfiguration?.uri?.toString())
                }
                refreshFavoriteState()
                while (isActive) {
                    delay(POSITION_SAVE_INTERVAL_MS)
                    if (player.isPlaying) persistPositionSafely()
                }
            } finally {
                isRestoring = false
            }
        }
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        clearPlaybackRetry()
        player.removeListener(playerListener)
        audioVisualizer.release()
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun mediaButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Previous")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Next")
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        CommandButton.Builder(
            if (currentPlaybackMode() == PlaybackMode.PURE_SHUFFLE) CommandButton.ICON_SHUFFLE_ON
            else CommandButton.ICON_SHUFFLE_OFF,
        )
            .setDisplayName("Shuffle")
            .setSessionCommand(shuffleCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(
            when (currentRepeatMode()) {
                RepeatMode.OFF -> CommandButton.ICON_REPEAT_OFF
                RepeatMode.ONE -> CommandButton.ICON_REPEAT_ONE
                RepeatMode.ALL -> CommandButton.ICON_REPEAT_ALL
            },
        )
            .setDisplayName("Repeat")
            .setSessionCommand(repeatCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(
            if (currentFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setDisplayName(if (currentFavorite) "Remove favorite" else "Favorite")
            .setSessionCommand(favoriteCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )

    private fun refreshMediaButtons() {
        if (::mediaSession.isInitialized) mediaSession.setMediaButtonPreferences(mediaButtons())
    }

    private fun expandProgressiveWindow() {
        if (isExpandingWindow || player.mediaItemCount == 0) return
        val snapshot = progressiveQueue.snapshot() ?: return
        if (!progressiveQueue.updateCurrent(player.currentMediaItem?.mediaId)) {
            progressiveQueue.clear()
            return
        }
        isExpandingWindow = true
        try {
            val removeCount = progressiveQueue.trimMaterializedHistory()
            if (removeCount > 0 && removeCount <= player.currentMediaItemIndex) {
                player.removeMediaItems(0, removeCount)
            }

            val forward = progressiveQueue.takeForwardBatchIfNeeded()
            if (forward.isNotEmpty()) {
                val policy = progressiveQueue.snapshot() ?: snapshot
                player.addMediaItems(forward.map { it.toMediaItem(policy.playbackMode, policy.repeatMode) })
            }

            val backward = progressiveQueue.takeBackwardBatchIfNeeded()
            if (backward.isNotEmpty()) {
                val policy = progressiveQueue.snapshot() ?: snapshot
                player.addMediaItems(0, backward.map { it.toMediaItem(policy.playbackMode, policy.repeatMode) })
            }
        } finally {
            isExpandingWindow = false
        }
    }

    private fun handlePlaybackFailure(error: PlaybackException) {
        val currentItem = player.currentMediaItem
        val mediaId = currentItem?.mediaId
        val isTelegramStream = currentItem?.localConfiguration?.uri?.scheme == TDLIB_SCHEME

        if (isTelegramStream && mediaId != null) {
            if (retryMediaId != mediaId) {
                clearPlaybackRetry()
                retryMediaId = mediaId
            }
            if (retryCount < MAX_REMOTE_PLAYBACK_RETRIES) {
                retryCount += 1
                val attempt = retryCount
                val shouldResume = player.playWhenReady
                playbackRetryJob?.cancel()
                Log.w(
                    TAG,
                    "Telegram playback failed for $mediaId; retrying attempt $attempt/$MAX_REMOTE_PLAYBACK_RETRIES",
                    error,
                )
                playbackRetryJob = serviceScope.launch {
                    delay(REMOTE_PLAYBACK_RETRY_BASE_DELAY_MS * attempt)
                    if (player.currentMediaItem?.mediaId != mediaId) return@launch
                    val stillWantsPlayback = player.playWhenReady
                    player.prepare()
                    if (shouldResume && stillWantsPlayback) player.play()
                }
                schedulePersist()
                return
            }
        }

        Log.w(TAG, "Playback failed; keeping the current queue item selected", error)
        clearPlaybackRetry()
        player.pause()
        schedulePersist()
    }

    private fun clearPlaybackRetry() {
        playbackRetryJob?.cancel()
        playbackRetryJob = null
        retryMediaId = null
        retryCount = 0
    }

    private fun toggleSystemShuffle() {
        if (player.mediaItemCount == 0) return
        val active = progressiveQueue.snapshot()
        if (active != null && progressiveQueue.updateCurrent(player.currentMediaItem?.mediaId)) {
            val nextMode = if (active.playbackMode == PlaybackMode.PURE_SHUFFLE) {
                PlaybackMode.ORDERED
            } else {
                PlaybackMode.PURE_SHUFFLE
            }
            val refreshed = progressiveQueue.snapshot() ?: return
            val future = refreshed.tracks.drop(refreshed.currentIndex + 1)
            serviceScope.launch {
                var seed: Long? = null
                val desiredFuture = if (nextMode == PlaybackMode.PURE_SHUFFLE) {
                    val byId = future.associateBy { it.id }
                    PureShuffleEngine.newCycle(future.map { it.id }).let { cycle ->
                        seed = cycle.seed
                        cycle.order.mapNotNull(byId::get)
                    }
                } else {
                    val order = runCatching { playbackCatalog.availableTracks().map { it.id } }.getOrDefault(emptyList())
                    val rank = order.withIndex().associate { it.value to it.index }
                    future.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
                }
                progressiveQueue.replaceFuture(desiredFuture, nextMode, seed)
                syncProgressiveFutureInPlace()
                refreshMediaButtons()
            }
            return
        }

        val nextMode = if (currentPlaybackMode() == PlaybackMode.PURE_SHUFFLE) PlaybackMode.ORDERED else PlaybackMode.PURE_SHUFFLE
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val currentId = player.currentMediaItem?.mediaId
        serviceScope.launch {
            val desiredFutureIds = when (nextMode) {
                PlaybackMode.PURE_SHUFFLE -> (currentIndex + 1 until player.mediaItemCount)
                    .map { player.getMediaItemAt(it).mediaId }
                    .shuffled()
                else -> {
                    val orderedIds = runCatching { playbackCatalog.availableTracks().map { it.id.toString() } }.getOrDefault(emptyList())
                    val rank = orderedIds.withIndex().associate { it.value to it.index }
                    (currentIndex + 1 until player.mediaItemCount)
                        .map { player.getMediaItemAt(it).mediaId }
                        .sortedBy { rank[it] ?: Int.MAX_VALUE }
                }
            }
            desiredFutureIds.forEachIndexed { offset, desiredMediaId ->
                val destination = currentIndex + 1 + offset
                val sourceIndex = (destination until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).mediaId == desiredMediaId }
                    ?: return@forEachIndexed
                if (sourceIndex != destination) player.moveMediaItem(sourceIndex, destination)
            }
            updateQueuePolicyMetadata(nextMode, currentRepeatMode())
            if (currentId != player.currentMediaItem?.mediaId) {
                player.seekTo(currentIndex.coerceAtMost(player.mediaItemCount - 1), player.currentPosition)
            }
            refreshMediaButtons()
        }
    }

    private fun cycleSystemRepeat() {
        val next = when (currentRepeatMode()) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        val progressive = progressiveQueue.snapshot() != null
        if (progressive) {
            progressiveQueue.updateRepeatMode(next)
            player.repeatMode = next.toPlayerRepeatMode(progressive = true)
            // Progressive queue state is authoritative. Do not replace the active MediaItem merely
            // to update policy metadata, because that can cause an audible rebuffering step.
            schedulePersist()
        } else {
            player.repeatMode = next.toPlayerRepeatMode(progressive = false)
            updateQueuePolicyMetadata(currentPlaybackMode(), next)
        }
        refreshMediaButtons()
    }

    private fun toggleSystemFavorite() {
        val trackId = player.currentMediaItem?.mediaId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        serviceScope.launch {
            try {
                val track = libraryRepository.observeTracks().first().firstOrNull { it.id == trackId }
                    ?: return@launch
                val favorite = !track.favorite
                libraryRepository.setFavorite(trackId, favorite)
                currentFavorite = favorite
                refreshMediaButtons()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            }
        }
    }

    private fun refreshFavoriteState() {
        val trackId = player.currentMediaItem?.mediaId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (trackId == null) {
            currentFavorite = false
            refreshMediaButtons()
            return
        }
        serviceScope.launch {
            currentFavorite = try {
                libraryRepository.observeTracks().first().firstOrNull { it.id == trackId }?.favorite == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            refreshMediaButtons()
        }
    }

    private fun updateQueuePolicyMetadata(playbackMode: PlaybackMode, repeatMode: RepeatMode) {
        for (index in 0 until player.mediaItemCount) {
            player.replaceMediaItem(index, player.getMediaItemAt(index).withQueuePolicy(playbackMode, repeatMode))
        }
    }

    private fun currentPlaybackMode(): PlaybackMode =
        progressiveQueue.snapshot()?.playbackMode
            ?: player.takeIf { it.mediaItemCount > 0 }?.getMediaItemAt(0)?.playbackMode()
            ?: PlaybackMode.ORDERED

    private fun currentRepeatMode(): RepeatMode =
        progressiveQueue.snapshot()?.repeatMode
            ?: player.takeIf { it.mediaItemCount > 0 }?.getMediaItemAt(0)?.repeatMode()
            ?: when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }

    private suspend fun restoreSession() {
        val saved = stateStore.load()
        if (player.mediaItemCount != 0 || saved.items.isEmpty()) {
            isRestoring = false
            return
        }

        val logicalIds = saved.logicalMediaIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        val restoredProgressive = if (logicalIds.isNotEmpty()) {
            val available = runCatching { playbackCatalog.availableTracks().associateBy { it.id } }.getOrNull()
            available != null && progressiveQueue.restore(
                orderedTrackIds = logicalIds,
                availableTracks = available,
                requestedCurrentIndex = saved.logicalCurrentIndex,
                requestedMaterializedStartIndex = saved.materializedStartIndex,
                requestedMaterializedEndExclusive = saved.materializedEndExclusive,
                mode = saved.playbackMode,
                repeat = saved.repeatMode,
                seed = saved.shuffleSeed,
            )
        } else {
            false
        }

        if (restoredProgressive) {
            progressiveQueue.updateCurrent(saved.items.getOrNull(saved.currentIndex)?.mediaId)
            val plan = progressiveQueue.resetWindow()
            player.repeatMode = saved.repeatMode.toPlayerRepeatMode(progressive = true)
            player.setMediaItems(
                plan.tracks.map { it.toMediaItem(saved.playbackMode, saved.repeatMode) },
                plan.startIndexInWindow,
                saved.positionMs,
            )
            player.prepare()
        } else {
            progressiveQueue.clear()
            player.repeatMode = saved.repeatMode.toPlayerRepeatMode(progressive = false)
            player.setMediaItems(
                saved.items.map { it.toMediaItem(saved.playbackMode, saved.repeatMode) },
                saved.currentIndex,
                saved.positionMs,
            )
            player.prepare()
        }
        isRestoring = false
    }

    private fun schedulePersist() {
        if (isRestoring) return
        persistJob?.cancel()
        persistJob = serviceScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistSafely()
        }
    }

    private suspend fun persistSafely() {
        try {
            persistNow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }

    private suspend fun persistPositionSafely() {
        try {
            stateStore.savePosition(
                mediaId = player.currentMediaItem?.mediaId,
                positionMs = player.currentPosition.coerceAtLeast(0),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }

    private suspend fun persistNow() {
        val items = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toPersistedPlaybackItem() }
        val logical = progressiveQueue.snapshot()
        stateStore.save(
            PersistedPlaybackSession(
                items = items,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0),
                playbackMode = currentPlaybackMode(),
                repeatMode = currentRepeatMode(),
                logicalMediaIds = logical?.tracks?.map { it.id.toString() }.orEmpty(),
                logicalCurrentIndex = logical?.currentIndex ?: -1,
                materializedStartIndex = logical?.materializedStartIndex ?: 0,
                materializedEndExclusive = logical?.materializedEndExclusive ?: items.size,
                shuffleSeed = logical?.shuffleSeed,
            ),
        )
    }

    /**
     * Replaces only the materialized future items after a progressive mode change. The current
     * MediaItem remains attached to ExoPlayer, so changing shuffle never calls setMediaItems or
     * prepare on the track that is already playing.
     */
    private fun syncProgressiveFutureInPlace() {
        if (!progressiveQueue.updateCurrent(player.currentMediaItem?.mediaId)) return
        val snapshot = progressiveQueue.snapshot() ?: return
        val playerCurrentIndex = player.currentMediaItemIndex
        if (playerCurrentIndex !in 0 until player.mediaItemCount) return

        val logicalFutureStart = snapshot.currentIndex + 1
        val logicalFutureEnd = snapshot.materializedEndExclusive
            .coerceIn(logicalFutureStart, snapshot.tracks.size)
        val desiredFuture = snapshot.tracks
            .subList(logicalFutureStart, logicalFutureEnd)
            .map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) }
        val playerFutureStart = playerCurrentIndex + 1

        if (playerFutureStart < player.mediaItemCount) {
            player.removeMediaItems(playerFutureStart, player.mediaItemCount)
        }
        if (desiredFuture.isNotEmpty()) {
            player.addMediaItems(desiredFuture)
        }
        player.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(progressive = true)
        schedulePersist()
    }

    private fun rebuildProgressiveWindow() {
        val snapshot = progressiveQueue.snapshot() ?: return
        val currentId = player.currentMediaItem?.mediaId
        progressiveQueue.updateCurrent(currentId)
        val position = player.currentPosition.coerceAtLeast(0)
        val shouldResume = player.playWhenReady
        val plan = progressiveQueue.resetWindow()
        val refreshed = progressiveQueue.snapshot() ?: snapshot
        player.repeatMode = refreshed.repeatMode.toPlayerRepeatMode(progressive = true)
        player.setMediaItems(
            plan.tracks.map { it.toMediaItem(refreshed.playbackMode, refreshed.repeatMode) },
            plan.startIndexInWindow,
            position,
        )
        player.prepare()
        if (shouldResume) player.play()
        schedulePersist()
    }

    private fun startNextQueueCycle() {
        if (isChangingQueueCycle || player.mediaItemCount == 0) return
        val progressive = progressiveQueue.snapshot()
        if (progressive != null) {
            val shouldPrepareNext = progressive.repeatMode == RepeatMode.ALL ||
                (progressive.playbackMode == PlaybackMode.PURE_SHUFFLE && progressive.repeatMode == RepeatMode.OFF)
            if (!shouldPrepareNext) return
            isChangingQueueCycle = true
            serviceScope.launch {
                try {
                    val shouldContinue = progressive.repeatMode == RepeatMode.ALL && player.playWhenReady
                    val previousLast = progressive.tracks.getOrNull(progressive.currentIndex)
                    var seed: Long? = progressive.shuffleSeed
                    val nextOrder = when (progressive.playbackMode) {
                        PlaybackMode.PURE_SHUFFLE -> {
                            val byId = progressive.tracks.associateBy { it.id }
                            PureShuffleEngine.newCycle(
                                progressive.tracks.map { it.id },
                                previousLast?.id,
                            ).let { cycle ->
                                seed = cycle.seed
                                cycle.order.mapNotNull(byId::get)
                            }
                        }
                        PlaybackMode.ORDERED, PlaybackMode.SMART_SHUFFLE -> progressive.tracks
                    }
                    val plan = progressiveQueue.start(
                        orderedTracks = nextOrder,
                        requestedStartIndex = 0,
                        mode = progressive.playbackMode,
                        repeat = progressive.repeatMode,
                        seed = seed,
                    )
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.setMediaItems(
                        plan.tracks.map { it.toMediaItem(progressive.playbackMode, progressive.repeatMode) },
                        plan.startIndexInWindow,
                        0,
                    )
                    player.prepare()
                    if (shouldContinue) player.play()
                    schedulePersist()
                } finally {
                    isChangingQueueCycle = false
                }
            }
            return
        }

        startNextLegacyPureShuffleCycle()
    }

    private fun startNextLegacyPureShuffleCycle() {
        if (isChangingQueueCycle || player.mediaItemCount == 0) return
        val currentItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val playbackMode = currentItems.first().playbackMode()
        if (playbackMode != PlaybackMode.PURE_SHUFFLE) return
        isChangingQueueCycle = true
        serviceScope.launch {
            try {
                val repeatMode = currentItems.first().repeatMode()
                val previousLastId = player.currentMediaItem?.mediaId
                val shouldContinue = repeatMode == RepeatMode.ALL && player.playWhenReady
                if (repeatMode != RepeatMode.ALL) return@launch
                val catalogItems = runCatching { playbackCatalog.availableTracks() }
                    .getOrNull()
                    ?.map { it.toMediaItem(playbackMode, repeatMode) }
                    ?.takeIf { it.isNotEmpty() }
                val eligibleItems = catalogItems ?: currentItems
                val previousLastItem = eligibleItems.firstOrNull { it.mediaId == previousLastId }
                val nextCycle = PureShuffleEngine.newCycle(eligibleItems, previousLastItem).order
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.setMediaItems(nextCycle, 0, 0)
                player.prepare()
                if (shouldContinue) player.play()
                schedulePersist()
            } finally {
                isChangingQueueCycle = false
            }
        }
    }

    private companion object {
        const val TAG = "MeowzixPlayback"
        const val TDLIB_SCHEME = "meowzix-tdlib"
        const val ACTION_TOGGLE_SHUFFLE = "dev.behradhz.meowzix.action.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "dev.behradhz.meowzix.action.CYCLE_REPEAT"
        const val ACTION_TOGGLE_FAVORITE = "dev.behradhz.meowzix.action.TOGGLE_FAVORITE"
        const val MAX_REMOTE_PLAYBACK_RETRIES = 3
        const val REMOTE_PLAYBACK_RETRY_BASE_DELAY_MS = 600L
        const val FORWARD_INCREMENT_MS = 15_000L
        const val PERSIST_DEBOUNCE_MS = 250L
        const val POSITION_SAVE_INTERVAL_MS = 1_000L
    }
}

private fun RepeatMode.toPlayerRepeatMode(progressive: Boolean): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && !progressive -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}
