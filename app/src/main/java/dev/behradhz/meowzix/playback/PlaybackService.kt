package dev.behradhz.meowzix.playback

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.MainActivity
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.CrossfadePolicy
import dev.behradhz.meowzix.domain.playback.EqualizerRepository
import dev.behradhz.meowzix.domain.playback.LoudnessNormalizationRepository
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import dev.behradhz.meowzix.domain.playback.PlaybackGainCoordinator
import dev.behradhz.meowzix.domain.playback.PlaybackGainState
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.ProgressiveQueue
import dev.behradhz.meowzix.domain.playback.PureShuffleEngine
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.playback.SleepTimerMode
import dev.behradhz.meowzix.domain.playback.SleepTimerPolicy
import dev.behradhz.meowzix.domain.playback.SleepTimerTerminationReason
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.feature.telegram.TelegramForwardActivity
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackSession
import dev.behradhz.meowzix.playback.persistence.PlaybackStateStore
import dev.behradhz.meowzix.widget.PlaybackWidgetProvider
import dev.behradhz.meowzix.widget.PlaybackWidgetState
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
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var stateStore: PlaybackStateStore
    @Inject lateinit var playbackCatalog: PlaybackCatalog
    @Inject lateinit var audioVisualizer: AudioVisualizerRepository
    @Inject lateinit var libraryRepository: MusicLibraryRepository
    @Inject lateinit var progressiveQueue: ProgressiveQueue
    @Inject lateinit var sleepTimer: SleepTimerManager
    @Inject lateinit var equalizer: EqualizerRepository
    @Inject lateinit var loudnessNormalization: LoudnessNormalizationRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var mediaLibrary: MeowzixMediaLibrary

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var playbackAudioAttributes: AudioAttributes
    private var persistJob: Job? = null
    private var playbackRetryJob: Job? = null
    private var retryMediaId: String? = null
    private var retryCount = 0
    private var isRestoring = true
    private var isChangingQueueCycle = false
    private var isExpandingWindow = false
    private var currentFavorite = false
    private var normalizationEnabled = false
    private var localAudioAnalysisEnabled = true
    private var normalizationGainDb = 0f
    private var equalizerPeakBoostDb = 0f
    private var sleepTimerGain = 1f
    private var normalizationRefreshJob: Job? = null
    private var crossfadeDurationSeconds = 0
    private var activeCrossfade: CrossfadeRuntime? = null

    private val forwardCommand = SessionCommand(ACTION_OPEN_TELEGRAM_FORWARD, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    private val repeatCommand = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
    private val favoriteCommand = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)

    private val sessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: ControllerInfo,
        ): ListenableFuture<ConnectionResult> {
            val sessionCommands = ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(forwardCommand)
                .add(shuffleCommand)
                .add(repeatCommand)
                .add(favoriteCommand)
                .build()
            val defaultResult = AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .build()
            val playerCommands = defaultResult.availablePlayerCommands.buildUpon()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SET_MEDIA_ITEM,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                )
                .build()
            return Futures.immediateFuture(
                AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(playerCommands)
                    .build(),
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(mediaLibrary.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val items = mediaLibrary.children(parentId, page, pageSize)
                    future.set(
                        if (items == null) {
                            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE, params)
                        } else {
                            LibraryResult.ofItemList(items, params)
                        },
                    )
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    throw cancelled
                } catch (_: Throwable) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_IO, params))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val item = mediaLibrary.item(mediaId)
                    future.set(
                        if (item == null) {
                            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        } else {
                            LibraryResult.ofItem(item, null)
                        },
                    )
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    throw cancelled
                } catch (_: Throwable) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_IO))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = resolveRequestedMediaItems(mediaItems)

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val resolvedFuture = resolveRequestedMediaItems(mediaItems)
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val resolved = resolvedFuture.get()
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            resolved,
                            startIndex,
                            startPositionMs,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    throw cancelled
                } catch (error: Throwable) {
                    future.setException(error)
                }
            }
            return future
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val saved = stateStore.load()
                    if (saved.items.isEmpty()) {
                        future.setException(IllegalStateException("No saved playback session"))
                        return@launch
                    }
                    val currentIndex = saved.currentIndex.coerceIn(saved.items.indices)
                    val restoredItems = saved.items.map { it.toMediaItem(saved.playbackMode, saved.repeatMode) }
                    val resumption = if (isForPlayback) {
                        MediaSession.MediaItemsWithStartPosition(
                            restoredItems,
                            currentIndex,
                            saved.positionMs.coerceAtLeast(0),
                        )
                    } else {
                        MediaSession.MediaItemsWithStartPosition(
                            listOf(restoredItems[currentIndex]),
                            0,
                            C.TIME_UNSET,
                        )
                    }
                    future.set(resumption)
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    throw cancelled
                } catch (error: Throwable) {
                    future.setException(error)
                }
            }
            return future
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_OPEN_TELEGRAM_FORWARD -> openTelegramForwardPicker()
                ACTION_TOGGLE_SHUFFLE -> toggleSystemShuffle()
                ACTION_CYCLE_REPEAT -> cycleSystemRepeat()
                ACTION_TOGGLE_FAVORITE -> toggleSystemFavorite()
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (activeCrossfade != null) cancelCrossfade("active media item changed")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && activeCrossfade != null) {
                cancelCrossfade("active player seek")
            }
        }

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
                syncLogicalCurrentFromPlayer()
                expandProgressiveWindow()
                runCatching { audioVisualizer.analyze(player.currentMediaItem?.localConfiguration?.uri?.toString()) }
                refreshFavoriteState()
                refreshNormalizationGain()
                if (sleepTimer.state.value.mode == SleepTimerMode.END_OF_TRACK) {
                    serviceScope.launch { sleepTimer.rearmEndOfTrack(player.currentMediaItem?.mediaId) }
                }
            } else if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                expandProgressiveWindow()
            }
            if (events.contains(Player.EVENT_TRACKS_CHANGED)) {
                refreshNormalizationGain()
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
            if (
                events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                refreshPlaybackWidget()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            runCatching { audioVisualizer.attachToAudioSession(audioSessionId) }
            runCatching { equalizer.attachToAudioSession(audioSessionId) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && activeCrossfade != null) {
                cancelCrossfade("active player paused")
            }
            if (!playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                sleepTimer.state.value.mode == SleepTimerMode.END_OF_TRACK
            ) {
                serviceScope.launch { completeSleepTimer(SleepTimerTerminationReason.END_OF_TRACK_REACHED) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackFailure(error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        playbackAudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = buildPlayer(handleAudioFocus = true)
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
        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(openPlayerIntent)
            .setMediaButtonPreferences(mediaButtons())
            .build()

        serviceScope.launch {
            settingsRepository.playbackPreferenceSettings.collect { preferences ->
                val nextNormalization = preferences.loudnessNormalizationEnabled
                val nextCrossfadeSeconds = CrossfadePolicy.sanitizeSeconds(preferences.crossfadeDurationSeconds)
                if (
                    activeCrossfade != null &&
                    (normalizationEnabled != nextNormalization || crossfadeDurationSeconds != nextCrossfadeSeconds)
                ) {
                    cancelCrossfade("playback enhancement setting changed")
                }
                normalizationEnabled = nextNormalization
                crossfadeDurationSeconds = nextCrossfadeSeconds
                if (!normalizationEnabled) {
                    normalizationRefreshJob?.cancel()
                    normalizationGainDb = 0f
                    applyGain()
                } else refreshNormalizationGain()
            }
        }
        serviceScope.launch {
            settingsRepository.recommendationPreferenceSettings.collect { preferences ->
                localAudioAnalysisEnabled = preferences.audioAnalysisEnabled
                if (normalizationEnabled) refreshNormalizationGain()
            }
        }
        serviceScope.launch {
            equalizer.state.collect { state ->
                if (state.enabled && activeCrossfade != null) {
                    cancelCrossfade("equalizer enabled")
                }
                equalizerPeakBoostDb = if (state.enabled) {
                    state.bands.maxOfOrNull { it.levelDb.coerceAtLeast(0f) } ?: 0f
                } else 0f
                applyGain()
            }
        }
        serviceScope.launch {
            sleepTimer.state.collect { timer ->
                if (timer.active && activeCrossfade != null) {
                    cancelCrossfade("sleep timer armed")
                }
                player.setPauseAtEndOfMediaItems(timer.mode == SleepTimerMode.END_OF_TRACK)
                if (timer.mode == SleepTimerMode.OFF) {
                    sleepTimerGain = 1f
                    applyGain()
                }
                if (timer.mode == SleepTimerMode.END_OF_TRACK && timer.armedMediaId == null) {
                    sleepTimer.rearmEndOfTrack(player.currentMediaItem?.mediaId)
                }
            }
        }
        serviceScope.launch {
            sleepTimer.restore()
            while (isActive) {
                val timer = sleepTimer.refresh()
                sleepTimerGain = when (timer.mode) {
                    SleepTimerMode.DURATION -> SleepTimerPolicy.fadeGain(timer)
                    SleepTimerMode.END_OF_TRACK -> SleepTimerPolicy.endOfTrackFadeGain(
                        timer,
                        player.currentMediaItem?.mediaId,
                        player.currentPosition.coerceAtLeast(0L),
                        player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
                    )
                    SleepTimerMode.OFF -> 1f
                }
                applyGain()
                if (timer.mode == SleepTimerMode.DURATION && timer.remainingMs <= 0L) {
                    completeSleepTimer(SleepTimerTerminationReason.DURATION_EXPIRED)
                }
                delay(SLEEP_TIMER_TICK_MS)
            }
        }

        serviceScope.launch {
            while (isActive) {
                tickCrossfade()
                delay(CROSSFADE_TICK_MS)
            }
        }

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
                refreshNormalizationGain()
                while (isActive) {
                    delay(POSITION_SAVE_INTERVAL_MS)
                    if (player.isPlaying) persistPositionSafely()
                }
            } finally {
                isRestoring = false
            }
        }
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession = mediaSession

    override fun onDestroy() {
        clearPlaybackRetry()
        cancelCrossfade("service destroyed")
        player.removeListener(playerListener)
        audioVisualizer.release()
        equalizer.release()
        normalizationRefreshJob?.cancel()
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refreshPlaybackWidget() {
        val state = PlaybackWidgetState.fromPlayer(player)
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                PlaybackWidgetProvider.updateAll(this@PlaybackService.applicationContext, state)
            }
        }
    }

    private fun resolveRequestedMediaItems(mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        if (mediaItems.all { it.localConfiguration != null }) {
            return Futures.immediateFuture(mediaItems)
        }
        val future = SettableFuture.create<List<MediaItem>>()
        serviceScope.launch(Dispatchers.IO) {
            try {
                val resolved = mediaItems.map { requested ->
                    if (requested.localConfiguration != null) {
                        requested
                    } else {
                        val trackId = mediaLibrary.parseTrackId(requested.mediaId)
                            ?: throw IllegalArgumentException("Unsupported media id")
                        playbackCatalog.playableTrack(trackId)
                            ?.toMediaItem(currentPlaybackMode(), currentRepeatMode())
                            ?: throw IllegalStateException("Track is currently unavailable")
                    }
                }
                future.set(resolved)
            } catch (cancelled: CancellationException) {
                future.cancel(false)
                throw cancelled
            } catch (error: Throwable) {
                future.setException(error)
            }
        }
        return future
    }

    private fun buildPlayer(handleAudioFocus: Boolean): ExoPlayer {
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(MeowzixDataSourceFactory(this))
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekForwardIncrementMs(FORWARD_INCREMENT_MS)
            .setAudioAttributes(playbackAudioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
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
        CommandButton.Builder(CommandButton.ICON_SHARE)
            .setDisplayName("Forward on Telegram")
            .setSessionCommand(forwardCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
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

    @Suppress("DEPRECATION")
    private fun openTelegramForwardPicker() {
        if (player.currentMediaItem == null) return
        val forwardIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, TelegramForwardActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val launchOptions = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    if (Build.VERSION.SDK_INT >= 36) {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    } else {
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    },
                )
                .toBundle()
        } else {
            null
        }
        runCatching {
            if (launchOptions == null) {
                forwardIntent.send()
            } else {
                forwardIntent.send(this, 0, null, null, null, null, launchOptions)
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to open Telegram forward picker", error)
        }
    }

    private fun syncLogicalCurrentFromPlayer(): Boolean {
        if (progressiveQueue.snapshot() == null) return false
        return progressiveQueue.updateCurrentFromWindowIndex(player.currentMediaItemIndex) ||
            progressiveQueue.updateCurrent(player.currentMediaItem?.mediaId)
    }

    private fun expandProgressiveWindow() {
        if (isExpandingWindow || player.mediaItemCount == 0) return
        val snapshot = progressiveQueue.snapshot() ?: return
        if (!syncLogicalCurrentFromPlayer()) {
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
        if (activeCrossfade != null) cancelCrossfade("active player error")
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
        if (sleepTimer.state.value.mode == SleepTimerMode.END_OF_TRACK) {
            serviceScope.launch { completeSleepTimer(SleepTimerTerminationReason.END_OF_TRACK_PLAYBACK_ERROR) }
        }
        schedulePersist()
    }

    private fun clearPlaybackRetry() {
        playbackRetryJob?.cancel()
        playbackRetryJob = null
        retryMediaId = null
        retryCount = 0
    }

    private fun applyGain() {
        player.volume = PlaybackGainCoordinator.resolve(
            PlaybackGainState(
                userBaseVolume = 1f,
                normalizationGainDb = normalizationGainDb,
                equalizerPeakBoostDb = equalizerPeakBoostDb,
                sleepTimerGain = sleepTimerGain,
                crossfadeGain = activeCrossfadeGain(),
            ),
        ).playerVolume
    }

    private fun refreshNormalizationGain() {
        normalizationRefreshJob?.cancel()
        if (!normalizationEnabled) {
            normalizationGainDb = 0f
            applyGain()
            return
        }
        val mediaId = player.currentMediaItem?.mediaId
        val trackId = mediaId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (trackId == null) {
            normalizationGainDb = 0f
            applyGain()
            return
        }
        val replayGain = selectedAudioMetadata()?.let(ReplayGainMetadataParser::parse)
        if (replayGain != null) {
            normalizationGainDb = replayGain.suggestedGainDb
            applyGain()
            normalizationRefreshJob = serviceScope.launch(Dispatchers.IO) {
                runCatching { loudnessNormalization.persistReplayGain(trackId, replayGain) }
            }
            return
        }
        if (!localAudioAnalysisEnabled) {
            normalizationGainDb = 0f
            applyGain()
            return
        }
        normalizationRefreshJob = serviceScope.launch {
            val analysis = runCatching { loudnessNormalization.fallbackAnalysis(trackId) }.getOrNull()
            if (player.currentMediaItem?.mediaId == mediaId && normalizationEnabled) {
                normalizationGainDb = analysis?.suggestedGainDb ?: 0f
                applyGain()
            }
        }
    }

    private fun selectedAudioMetadata(target: ExoPlayer = player): Metadata? {
        target.currentTracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEach
            for (index in 0 until group.length) {
                if (group.isTrackSelected(index)) return group.getTrackFormat(index).metadata
            }
        }
        return null
    }

    private suspend fun completeSleepTimer(reason: SleepTimerTerminationReason) {
        if (activeCrossfade != null) cancelCrossfade("sleep timer completed")
        val mediaId = player.currentMediaItem?.mediaId
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: player.currentMediaItem?.mediaMetadata?.durationMs?.coerceAtLeast(0L)
            ?: 0L
        player.pause()
        sleepTimer.complete(reason, mediaId, position, duration)
        sleepTimerGain = 1f
        applyGain()
        schedulePersist()
    }

    private fun activeCrossfadeGain(): Float {
        val runtime = activeCrossfade ?: return 1f
        val startedAt = runtime.startedAtElapsedRealtimeMs ?: return 1f
        if (runtime.overlapDurationMs <= 0L) return 1f
        val progress = (
            (SystemClock.elapsedRealtime() - startedAt).toFloat() /
                runtime.overlapDurationMs.toFloat()
        ).coerceIn(0f, 1f)
        val gains = CrossfadePolicy.gains(progress)
        return when (player) {
            runtime.outgoing -> gains.outgoing
            runtime.incoming -> gains.incoming
            else -> 1f
        }
    }

    private fun tickCrossfade() {
        if (isRestoring || crossfadeDurationSeconds == 0) {
            if (crossfadeDurationSeconds == 0 && activeCrossfade != null) {
                cancelCrossfade("crossfade disabled")
            }
            return
        }

        val runtime = activeCrossfade
        if (runtime == null) {
            maybePrepareCrossfade()
            return
        }

        val tracked = if (runtime.identitySwitched) runtime.incoming else runtime.outgoing
        if (player !== tracked) {
            cancelCrossfade("authoritative player changed")
            return
        }
        if (queueMediaIds(tracked) != runtime.queueMediaIds) {
            cancelCrossfade("queue changed during overlap")
            return
        }
        val expectedMediaId = if (runtime.identitySwitched) runtime.incomingMediaId else runtime.outgoingMediaId
        if (tracked.currentMediaItem?.mediaId != expectedMediaId) {
            cancelCrossfade("active item changed during overlap")
            return
        }
        if (runtime.incoming.playerError != null) {
            cancelCrossfade("incoming preload failed")
            return
        }

        val startedAt = runtime.startedAtElapsedRealtimeMs
        if (startedAt == null) {
            if (!runtime.outgoing.isPlaying) {
                cancelCrossfade("outgoing playback paused before overlap")
                return
            }
            val outgoingRemaining = currentRemainingMs(runtime.outgoing)
            if (runtime.incoming.playbackState == Player.STATE_READY) {
                resolveIncomingReplayGain(runtime)
                val thresholdMs =
                    CrossfadePolicy.sanitizeSeconds(crossfadeDurationSeconds) * 1_000L
                if (outgoingRemaining <= thresholdMs) {
                    val incomingDuration = resolvedDurationMs(runtime.incoming)
                        .takeIf { it > 0L }
                        ?: runtime.incomingDurationMs
                    val overlapMs = CrossfadePolicy.effectiveOverlapMs(
                        crossfadeDurationSeconds,
                        outgoingRemaining,
                        incomingDuration,
                    )
                    if (overlapMs <= 0L) {
                        cancelCrossfade("insufficient overlap window")
                        return
                    }
                    runtime.overlapDurationMs = overlapMs
                    runtime.incoming.volume = 0f
                    runtime.incoming.play()
                    runtime.startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
                }
            } else if (
                outgoingRemaining <=
                CrossfadePolicy.MIN_REAL_OVERLAP_MS + CrossfadePolicy.TRACK_GUARD_MS
            ) {
                cancelCrossfade("incoming player was not ready in time")
            }
            return
        }

        val progress = (
            (SystemClock.elapsedRealtime() - startedAt).toFloat() /
                runtime.overlapDurationMs.coerceAtLeast(1L).toFloat()
        ).coerceIn(0f, 1f)
        applyCrossfadeVolumes(runtime, progress)

        if (!runtime.identitySwitched && CrossfadePolicy.switchIdentity(progress)) {
            if (!switchCrossfadeIdentity(runtime)) {
                cancelCrossfade("media session player handoff failed")
                return
            }
        }
        if (progress >= 1f) finishCrossfade(runtime)
    }

    private fun maybePrepareCrossfade() {
        val active = player
        if (!active.isPlaying || active.mediaItemCount < 2) return
        if (sleepTimer.state.value.active || equalizer.state.value.enabled) return
        if (currentRepeatMode() == RepeatMode.ONE) return

        val currentIndex = active.currentMediaItemIndex
        val nextIndex = currentIndex + 1
        if (currentIndex !in 0 until active.mediaItemCount || nextIndex !in 0 until active.mediaItemCount) return

        val outgoingItem = active.getMediaItemAt(currentIndex)
        val incomingItem = active.getMediaItemAt(nextIndex)
        if (!isCrossfadeLocal(outgoingItem) || !isCrossfadeLocal(incomingItem)) return

        val outgoingDuration = resolvedDurationMs(active)
        val incomingDuration = incomingItem.mediaMetadata.durationMs?.coerceAtLeast(0L) ?: 0L
        val capability = CrossfadePolicy.capability(
            configuredSeconds = crossfadeDurationSeconds,
            currentLocallyReadable = true,
            nextLocallyReadable = true,
            equalizerEnabled = false,
            sleepTimerActive = false,
            repeatMode = currentRepeatMode(),
            currentDurationMs = outgoingDuration,
            nextDurationMs = incomingDuration,
        )
        if (!capability.supported) return

        val requestedMs = CrossfadePolicy.sanitizeSeconds(crossfadeDurationSeconds) * 1_000L
        val remainingMs = currentRemainingMs(active)
        if (remainingMs > requestedMs + CrossfadePolicy.PRELOAD_LEAD_MS) return
        if (remainingMs <= CrossfadePolicy.MIN_REAL_OVERLAP_MS) return

        val queueItems = (0 until active.mediaItemCount).map(active::getMediaItemAt)
        val incoming = buildPlayer(handleAudioFocus = false)
        val runtime = CrossfadeRuntime(
            outgoing = active,
            incoming = incoming,
            outgoingMediaId = outgoingItem.mediaId,
            incomingMediaId = incomingItem.mediaId,
            incomingTrackId = runCatching { UUID.fromString(incomingItem.mediaId) }.getOrNull(),
            incomingDurationMs = incomingDuration,
            queueMediaIds = queueItems.map { it.mediaId },
            outgoingNormalizationGainDb = normalizationGainDb,
        )
        activeCrossfade = runtime
        incoming.repeatMode = active.repeatMode
        incoming.volume = 0f
        incoming.setMediaItems(queueItems, nextIndex, 0L)
        incoming.prepare()
        resolveIncomingFallbackNormalization(runtime)
    }

    private fun resolveIncomingFallbackNormalization(runtime: CrossfadeRuntime) {
        if (!normalizationEnabled || !localAudioAnalysisEnabled) return
        val trackId = runtime.incomingTrackId ?: return
        serviceScope.launch {
            val analysis = runCatching { loudnessNormalization.fallbackAnalysis(trackId) }.getOrNull()
            if (activeCrossfade === runtime && !runtime.replayGainResolved && normalizationEnabled) {
                runtime.incomingNormalizationGainDb = analysis?.suggestedGainDb ?: 0f
            }
        }
    }

    private fun resolveIncomingReplayGain(runtime: CrossfadeRuntime) {
        if (!normalizationEnabled || runtime.replayGainResolved) return
        val analysis = selectedAudioMetadata(runtime.incoming)?.let(ReplayGainMetadataParser::parse) ?: return
        runtime.replayGainResolved = true
        runtime.incomingNormalizationGainDb = analysis.suggestedGainDb
        runtime.incomingTrackId?.let { trackId ->
            serviceScope.launch(Dispatchers.IO) {
                runCatching { loudnessNormalization.persistReplayGain(trackId, analysis) }
            }
        }
    }

    private fun applyCrossfadeVolumes(runtime: CrossfadeRuntime, progress: Float) {
        val gains = CrossfadePolicy.gains(progress)
        val outgoingNormalization = if (normalizationEnabled) runtime.outgoingNormalizationGainDb else 0f
        val incomingNormalization = if (normalizationEnabled) runtime.incomingNormalizationGainDb else 0f
        runtime.outgoing.volume = PlaybackGainCoordinator.resolve(
            PlaybackGainState(
                normalizationGainDb = outgoingNormalization,
                crossfadeGain = gains.outgoing,
            ),
        ).playerVolume
        runtime.incoming.volume = PlaybackGainCoordinator.resolve(
            PlaybackGainState(
                normalizationGainDb = incomingNormalization,
                crossfadeGain = gains.incoming,
            ),
        ).playerVolume
    }

    private fun switchCrossfadeIdentity(runtime: CrossfadeRuntime): Boolean {
        if (activeCrossfade !== runtime || runtime.identitySwitched) return activeCrossfade === runtime
        val outgoing = runtime.outgoing
        val incoming = runtime.incoming

        outgoing.removeListener(playerListener)
        val switched = runCatching {
            mediaSession.setPlayer(incoming)
        }.isSuccess
        if (!switched) {
            outgoing.addListener(playerListener)
            return false
        }

        player = incoming
        incoming.addListener(playerListener)
        runtime.identitySwitched = true
        runtime.focusBridge = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && activeCrossfade === runtime) {
                    incoming.pause()
                    cancelCrossfade("outgoing audio-focus owner paused")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (activeCrossfade === runtime) {
                    incoming.pause()
                    cancelCrossfade("outgoing audio-focus owner failed")
                }
            }
        }.also(outgoing::addListener)

        normalizationGainDb = if (normalizationEnabled) runtime.incomingNormalizationGainDb else 0f
        runCatching { audioVisualizer.attachToAudioSession(incoming.audioSessionId) }
        runCatching { equalizer.attachToAudioSession(incoming.audioSessionId) }
        runCatching { audioVisualizer.analyze(incoming.currentMediaItem?.localConfiguration?.uri?.toString()) }
        syncLogicalCurrentFromPlayer()
        refreshFavoriteState()
        refreshMediaButtons()
        schedulePersist()
        return true
    }

    private fun finishCrossfade(runtime: CrossfadeRuntime) {
        if (activeCrossfade !== runtime) return
        if (!runtime.identitySwitched && !switchCrossfadeIdentity(runtime)) {
            cancelCrossfade("unable to finish identity handoff")
            return
        }
        handoffAudioFocusAndReleaseOutgoing(runtime)
        activeCrossfade = null
        applyGain()
        expandProgressiveWindow()
        refreshNormalizationGain()
        schedulePersist()
    }

    private fun cancelCrossfade(reason: String) {
        val runtime = activeCrossfade ?: return
        activeCrossfade = null
        Log.d(TAG, "Crossfade fallback: " + reason)
        if (runtime.identitySwitched) {
            handoffAudioFocusAndReleaseOutgoing(runtime)
            player = runtime.incoming
        } else {
            runtime.incoming.pause()
            runtime.incoming.release()
            player = runtime.outgoing
        }
        applyGain()
    }

    private fun handoffAudioFocusAndReleaseOutgoing(runtime: CrossfadeRuntime) {
        runtime.focusBridge?.let(runtime.outgoing::removeListener)
        runtime.focusBridge = null
        runCatching { runtime.incoming.setAudioAttributes(playbackAudioAttributes, true) }
        runCatching { runtime.incoming.setHandleAudioBecomingNoisy(true) }
        runtime.outgoing.pause()
        runtime.outgoing.release()
    }

    private fun queueMediaIds(target: ExoPlayer): List<String> =
        (0 until target.mediaItemCount).map { target.getMediaItemAt(it).mediaId }

    private fun isCrossfadeLocal(item: androidx.media3.common.MediaItem): Boolean = when (
        item.localConfiguration?.uri?.scheme?.lowercase()
    ) {
        "file", "content", "android.resource" -> true
        else -> false
    }

    private fun resolvedDurationMs(target: ExoPlayer): Long =
        target.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: target.currentMediaItem?.mediaMetadata?.durationMs?.coerceAtLeast(0L)
            ?: 0L

    private fun currentRemainingMs(target: ExoPlayer): Long =
        (resolvedDurationMs(target) - target.currentPosition.coerceAtLeast(0L)).coerceAtLeast(0L)

    private fun toggleSystemShuffle() {
        if (activeCrossfade != null) cancelCrossfade("shuffle changed")
        if (player.mediaItemCount == 0) return
        val active = progressiveQueue.snapshot()
        if (active != null && syncLogicalCurrentFromPlayer()) {
            val refreshed = progressiveQueue.snapshot() ?: return
            val nextMode = if (refreshed.playbackMode == PlaybackMode.PURE_SHUFFLE) {
                PlaybackMode.ORDERED
            } else {
                PlaybackMode.PURE_SHUFFLE
            }
            val future = refreshed.tracks.drop(refreshed.currentIndex + 1)
            serviceScope.launch {
                var seed: Long? = null
                val desiredFuture = if (nextMode == PlaybackMode.PURE_SHUFFLE) {
                    PureShuffleEngine.newCycle(future.indices.toList()).let { cycle ->
                        seed = cycle.seed
                        cycle.order.map(future::get)
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
        if (activeCrossfade != null) cancelCrossfade("repeat changed")
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
            if (!progressiveQueue.updateCurrentFromWindowIndex(saved.currentIndex)) {
                progressiveQueue.updateCurrent(saved.items.getOrNull(saved.currentIndex)?.mediaId)
            }
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
                            PureShuffleEngine.newCycle(progressive.tracks.indices.toList()).let { cycle ->
                                seed = cycle.seed
                                val order = cycle.order.toMutableList()
                                if (previousLast != null && order.size > 1) {
                                    val firstTrack = progressive.tracks[order.first()]
                                    if (firstTrack.id == previousLast.id) {
                                        val swapAt = (1 until order.size).firstOrNull { index ->
                                            progressive.tracks[order[index]].id != previousLast.id
                                        }
                                        if (swapAt != null) {
                                            val first = order[0]
                                            order[0] = order[swapAt]
                                            order[swapAt] = first
                                        }
                                    }
                                }
                                order.map(progressive.tracks::get)
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
                // A legacy/controller-provided queue is already the authoritative eligible set.
                // Expanding it through the whole library at the cycle boundary can block playback,
                // leak unrelated tracks into a selected collection, and violates Pure Shuffle's
                // collection isolation. Re-permute exactly the current canonical queue instead.
                val previousLastItem = currentItems.firstOrNull { it.mediaId == previousLastId }
                val nextCycle = PureShuffleEngine.newCycle(currentItems, previousLastItem).order
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

    private data class CrossfadeRuntime(
        val outgoing: ExoPlayer,
        val incoming: ExoPlayer,
        val outgoingMediaId: String,
        val incomingMediaId: String,
        val incomingTrackId: UUID?,
        val incomingDurationMs: Long,
        val queueMediaIds: List<String>,
        val outgoingNormalizationGainDb: Float,
        var incomingNormalizationGainDb: Float = 0f,
        var replayGainResolved: Boolean = false,
        var overlapDurationMs: Long = 0L,
        var startedAtElapsedRealtimeMs: Long? = null,
        var identitySwitched: Boolean = false,
        var focusBridge: Player.Listener? = null,
    )

    private companion object {
        const val TAG = "MeowzixPlayback"
        const val TDLIB_SCHEME = "meowzix-tdlib"
        const val ACTION_OPEN_TELEGRAM_FORWARD = "dev.behradhz.meowzix.action.OPEN_TELEGRAM_FORWARD"
        const val ACTION_TOGGLE_SHUFFLE = "dev.behradhz.meowzix.action.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "dev.behradhz.meowzix.action.CYCLE_REPEAT"
        const val ACTION_TOGGLE_FAVORITE = "dev.behradhz.meowzix.action.TOGGLE_FAVORITE"
        const val MAX_REMOTE_PLAYBACK_RETRIES = 3
        const val REMOTE_PLAYBACK_RETRY_BASE_DELAY_MS = 600L
        const val FORWARD_INCREMENT_MS = 15_000L
        const val PERSIST_DEBOUNCE_MS = 250L
        const val POSITION_SAVE_INTERVAL_MS = 1_000L
        const val SLEEP_TIMER_TICK_MS = 250L
        const val CROSSFADE_TICK_MS = 50L
    }
}

private fun RepeatMode.toPlayerRepeatMode(progressive: Boolean): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && !progressive -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}
