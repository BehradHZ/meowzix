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
    @Inject lateinit var windowedQueue: WindowedPlaybackQueue

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var persistJob: Job? = null
    private var windowMaintenanceJob: Job? = null
    private var playbackRetryJob: Job? = null
    private var retryMediaId: String? = null
    private var retryCount = 0
    private var isRestoring = true
    private var isChangingShuffleCycle = false
    private var isMaintainingWindow = false
    private var currentFavorite = false
    private var lastPersistedQueueRevision = Long.MIN_VALUE

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
            if (
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_READY &&
                player.playerError == null
            ) {
                clearPlaybackRetry()
            }
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) && player.mediaItemCount == 0 && !isRestoring) {
                windowedQueue.clear()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                clearPlaybackRetry()
                windowedQueue.updateCurrent(player.currentMediaItem?.mediaId)
                scheduleWindowMaintenance()
                runCatching { audioVisualizer.analyze(player.currentMediaItem?.localConfiguration?.uri?.toString()) }
                refreshFavoriteState()
            } else if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                scheduleWindowMaintenance()
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && player.playbackState == Player.STATE_ENDED) {
                handlePlaybackEnded()
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
                    // Session restore is optional. A stale/corrupt snapshot must not terminate the
                    // media service, because the app shell creates a controller during startup.
                    isRestoring = false
                    windowedQueue.clear()
                    player.clearMediaItems()
                }
                runCatching {
                    audioVisualizer.analyze(player.currentMediaItem?.localConfiguration?.uri?.toString())
                }
                refreshFavoriteState()
                while (isActive) {
                    delay(POSITION_SAVE_INTERVAL_MS)
                    if (player.isPlaying) persistProgressSafely()
                }
            } finally {
                isRestoring = false
            }
        }
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        clearPlaybackRetry()
        windowMaintenanceJob?.cancel()
        player.removeListener(playerListener)
        audioVisualizer.release()
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun mediaButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(
            if (currentPlaybackMode() == PlaybackMode.PURE_SHUFFLE) CommandButton.ICON_SHUFFLE_ON
            else CommandButton.ICON_SHUFFLE_OFF,
        )
            .setDisplayName("Shuffle")
            .setSessionCommand(shuffleCommand)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
            .setDisplayName("Forward 15 seconds")
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
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

        // Never silently consume the queue on a source failure. A missing local file, a stalled
        // Telegram download, or a decoder error should leave the requested item selected so the UI
        // can show the failure and the user can retry or explicitly skip it.
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

    private fun scheduleWindowMaintenance() {
        if (isRestoring || !windowedQueue.isActive()) return
        if (windowMaintenanceJob?.isActive == true) return
        windowMaintenanceJob = serviceScope.launch { maintainPlayerWindow() }
    }

    private fun maintainPlayerWindow(forceRefill: Boolean = false) {
        if (isMaintainingWindow || !windowedQueue.isActive() || player.mediaItemCount == 0) return
        isMaintainingWindow = true
        try {
            windowedQueue.updateCurrent(player.currentMediaItem?.mediaId)
            var snapshot = windowedQueue.snapshot()

            if (windowedQueue.shouldBackfillHistory(player.currentMediaItemIndex) && player.mediaItemCount > 0) {
                val firstId = player.getMediaItemAt(0).mediaId
                val backfill = windowedQueue.previousBatchBefore(firstId)
                if (backfill.isNotEmpty()) {
                    player.addMediaItems(
                        0,
                        backfill.map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) },
                    )
                }
            }

            val trimCount = windowedQueue.trimBeforeCount(player.currentMediaItemIndex)
            if (trimCount > 0 && trimCount < player.mediaItemCount) {
                player.removeMediaItems(0, trimCount)
            }

            snapshot = windowedQueue.snapshot()
            if (
                forceRefill ||
                windowedQueue.shouldRefillAhead(player.currentMediaItemIndex, player.mediaItemCount)
            ) {
                val lastId = player.getMediaItemAt(player.mediaItemCount - 1).mediaId
                val refill = windowedQueue.nextBatchAfter(lastId)
                if (refill.isNotEmpty()) {
                    player.addMediaItems(
                        refill.map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) },
                    )
                }
            }
        } finally {
            isMaintainingWindow = false
        }
    }

    private fun handlePlaybackEnded() {
        if (!windowedQueue.isActive()) {
            startNextPureShuffleCycle()
            return
        }
        serviceScope.launch {
            windowedQueue.updateCurrent(player.currentMediaItem?.mediaId)
            val snapshot = windowedQueue.snapshot()
            if (snapshot.currentIndex in 0 until snapshot.tracks.lastIndex) {
                val shouldContinue = player.playWhenReady
                maintainPlayerWindow(forceRefill = true)
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    if (shouldContinue) player.play()
                }
                return@launch
            }
            if (snapshot.repeatMode != RepeatMode.ALL) return@launch
            if (snapshot.playbackMode == PlaybackMode.PURE_SHUFFLE) {
                startNextPureShuffleCycle()
            } else {
                val shouldContinue = player.playWhenReady
                windowedQueue.reset(
                    tracks = snapshot.tracks,
                    currentIndex = 0,
                    playbackMode = snapshot.playbackMode,
                    repeatMode = snapshot.repeatMode,
                    shuffleSeed = snapshot.shuffleSeed,
                )
                materializeWindow(positionMs = 0L, shouldPlay = shouldContinue)
                schedulePersist()
            }
        }
    }

    private fun toggleSystemShuffle() {
        if (player.mediaItemCount == 0) return
        if (!windowedQueue.isActive()) {
            toggleLegacySystemShuffle()
            return
        }
        val nextMode = if (currentPlaybackMode() == PlaybackMode.PURE_SHUFFLE) PlaybackMode.ORDERED else PlaybackMode.PURE_SHUFFLE
        serviceScope.launch {
            windowedQueue.updateCurrent(player.currentMediaItem?.mediaId)
            val snapshot = windowedQueue.snapshot()
            if (snapshot.currentIndex !in snapshot.tracks.indices) return@launch
            val currentId = snapshot.tracks[snapshot.currentIndex].id
            val prefix = snapshot.tracks.take(snapshot.currentIndex + 1)
            val future = snapshot.tracks.drop(snapshot.currentIndex + 1)
            var shuffleSeed: Long? = null
            val reorderedFuture = if (nextMode == PlaybackMode.PURE_SHUFFLE) {
                val byId = future.associateBy { it.id }
                val cycle = PureShuffleEngine.newCycle(future.map { it.id })
                shuffleSeed = cycle.seed
                cycle.order.mapNotNull(byId::get)
            } else {
                val orderedIds = runCatching { playbackCatalog.availableTracks().map { it.id } }.getOrDefault(emptyList())
                val rank = orderedIds.withIndex().associate { it.value to it.index }
                future.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            }
            val position = player.currentPosition.coerceAtLeast(0)
            val shouldPlay = player.playWhenReady
            windowedQueue.resetAtTrack(
                tracks = prefix + reorderedFuture,
                currentTrackId = currentId,
                playbackMode = nextMode,
                repeatMode = snapshot.repeatMode,
                shuffleSeed = shuffleSeed,
            )
            materializeWindow(position, shouldPlay)
            refreshMediaButtons()
            schedulePersist()
        }
    }

    private fun toggleLegacySystemShuffle() {
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
        if (windowedQueue.isActive()) windowedQueue.setRepeatMode(next)
        player.repeatMode = next.toPlayerRepeatMode(currentPlaybackMode(), windowedQueue.isActive())
        updateQueuePolicyMetadata(currentPlaybackMode(), next)
        refreshMediaButtons()
        schedulePersist()
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
                // Favorite state is secondary to playback; DB/read failures stay non-fatal.
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
        windowedQueue.snapshot().takeIf { windowedQueue.isActive() }?.playbackMode
            ?: player.takeIf { it.mediaItemCount > 0 }?.getMediaItemAt(0)?.playbackMode()
            ?: PlaybackMode.ORDERED

    private fun currentRepeatMode(): RepeatMode =
        windowedQueue.snapshot().takeIf { windowedQueue.isActive() }?.repeatMode
            ?: player.takeIf { it.mediaItemCount > 0 }?.getMediaItemAt(0)?.repeatMode()
            ?: when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }

    private suspend fun restoreSession() {
        val saved = stateStore.load()
        if (player.mediaItemCount == 0 && saved.items.isNotEmpty()) {
            val logicalTracks = saved.items.mapNotNull { it.toPlayableTrack() }
            if (logicalTracks.isNotEmpty()) {
                windowedQueue.reset(
                    tracks = logicalTracks,
                    currentIndex = saved.currentIndex,
                    playbackMode = saved.playbackMode,
                    repeatMode = saved.repeatMode,
                )
                player.repeatMode = saved.repeatMode.toPlayerRepeatMode(saved.playbackMode, windowed = true)
                val window = windowedQueue.materializedWindow()
                player.setMediaItems(
                    window.tracks.map { it.toMediaItem(saved.playbackMode, saved.repeatMode) },
                    window.currentIndex,
                    saved.positionMs,
                )
                lastPersistedQueueRevision = windowedQueue.snapshot().revision
            } else {
                player.repeatMode = saved.repeatMode.toPlayerRepeatMode(saved.playbackMode, windowed = false)
                player.setMediaItems(
                    saved.items.map { it.toMediaItem(saved.playbackMode, saved.repeatMode) },
                    saved.currentIndex,
                    saved.positionMs,
                )
            }
            player.prepare()
        }
        isRestoring = false
    }

    private fun materializeWindow(positionMs: Long, shouldPlay: Boolean) {
        val snapshot = windowedQueue.snapshot()
        val window = windowedQueue.materializedWindow()
        if (window.tracks.isEmpty()) {
            player.clearMediaItems()
            return
        }
        player.repeatMode = snapshot.repeatMode.toPlayerRepeatMode(snapshot.playbackMode, windowed = true)
        player.setMediaItems(
            window.tracks.map { it.toMediaItem(snapshot.playbackMode, snapshot.repeatMode) },
            window.currentIndex,
            positionMs.coerceAtLeast(0),
        )
        player.prepare()
        if (shouldPlay) player.play()
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
            val snapshot = windowedQueue.snapshot().takeIf { windowedQueue.isActive() }
            if (snapshot != null && snapshot.revision == lastPersistedQueueRevision) {
                persistProgressSafely()
            } else {
                persistNow()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // A resume snapshot is convenience data; persistence failures must not stop playback.
        }
    }

    private suspend fun persistProgressSafely() {
        try {
            stateStore.saveProgress(
                mediaId = player.currentMediaItem?.mediaId,
                positionMs = player.currentPosition.coerceAtLeast(0),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Frequent progress persistence is best-effort.
        }
    }

    private suspend fun persistNow() {
        val snapshot = windowedQueue.snapshot().takeIf { windowedQueue.isActive() }
        val items = snapshot?.tracks?.map { it.toPersistedPlaybackItem() }
            ?: (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toPersistedPlaybackItem() }
        val currentIndex = snapshot?.currentIndex?.coerceAtLeast(0)
            ?: player.currentMediaItemIndex.coerceAtLeast(0)
        stateStore.save(
            PersistedPlaybackSession(
                items = items,
                currentIndex = currentIndex,
                positionMs = player.currentPosition.coerceAtLeast(0),
                playbackMode = snapshot?.playbackMode ?: currentPlaybackMode(),
                repeatMode = snapshot?.repeatMode ?: currentRepeatMode(),
            ),
        )
        lastPersistedQueueRevision = snapshot?.revision ?: Long.MIN_VALUE
    }

    private fun startNextPureShuffleCycle() {
        if (isChangingShuffleCycle || player.mediaItemCount == 0) return
        if (windowedQueue.isActive()) {
            val snapshot = windowedQueue.snapshot()
            if (snapshot.playbackMode != PlaybackMode.PURE_SHUFFLE || snapshot.repeatMode != RepeatMode.ALL) return
            isChangingShuffleCycle = true
            serviceScope.launch {
                try {
                    val previousLastId = snapshot.tracks.getOrNull(snapshot.currentIndex)?.id
                    val byId = snapshot.tracks.associateBy { it.id }
                    val cycle = PureShuffleEngine.newCycle(
                        eligibleItems = snapshot.tracks.map { it.id },
                        previousLastItem = previousLastId,
                    )
                    val nextTracks = cycle.order.mapNotNull(byId::get)
                    val shouldContinue = player.playWhenReady
                    windowedQueue.reset(
                        tracks = nextTracks,
                        currentIndex = 0,
                        playbackMode = PlaybackMode.PURE_SHUFFLE,
                        repeatMode = snapshot.repeatMode,
                        shuffleSeed = cycle.seed,
                    )
                    materializeWindow(positionMs = 0L, shouldPlay = shouldContinue)
                    schedulePersist()
                } finally {
                    isChangingShuffleCycle = false
                }
            }
            return
        }

        val currentItems = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val playbackMode = currentItems.first().playbackMode()
        if (playbackMode != PlaybackMode.PURE_SHUFFLE) return
        isChangingShuffleCycle = true
        serviceScope.launch {
            try {
                val repeatMode = currentItems.first().repeatMode()
                val previousLastId = player.currentMediaItem?.mediaId
                val shouldContinue = repeatMode == RepeatMode.ALL && player.playWhenReady
                val catalogItems = runCatching { playbackCatalog.availableTracks() }
                    .getOrNull()
                    ?.map { it.toMediaItem(playbackMode, repeatMode) }
                    ?.takeIf { it.isNotEmpty() }
                val eligibleItems = catalogItems ?: currentItems
                val previousLastItem = eligibleItems.firstOrNull { it.mediaId == previousLastId }
                val nextCycle = PureShuffleEngine.newCycle(eligibleItems, previousLastItem).order
                player.repeatMode = repeatMode.toPlayerRepeatMode(playbackMode, windowed = false)
                player.setMediaItems(nextCycle, 0, 0)
                player.prepare()
                if (shouldContinue) player.play()
                schedulePersist()
            } finally {
                isChangingShuffleCycle = false
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

private fun RepeatMode.toPlayerRepeatMode(playbackMode: PlaybackMode, windowed: Boolean): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && !windowed && playbackMode != PlaybackMode.PURE_SHUFFLE -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}
