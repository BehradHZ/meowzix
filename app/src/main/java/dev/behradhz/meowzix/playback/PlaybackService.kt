package dev.behradhz.meowzix.playback

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
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
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.MainActivity
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.data.repository.LibraryQueryRepository
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction
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
import dev.behradhz.meowzix.widget.VinylWidgetProvider
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
    @Inject lateinit var libraryQueryRepository: LibraryQueryRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveSongTransitionRetry = LiveSongTransitionRetry(
        scope = serviceScope,
        retryDelayMs = LIVE_SONG_TRANSITION_RETRY_MS,
    ) { expectedMediaId ->
        // Only retry for the still-current track. Android may defer actual AOD rendering.
        if (liveSongCardEnabled && player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED &&
            player.currentMediaItem?.mediaId == expectedMediaId
        ) {
            updateLiveSongCard(forceRefresh = true)
        }
    }
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var playbackAudioAttributes: AudioAttributes
    private lateinit var audioManager: AudioManager
    private var resumeAfterExternalAudioEnabled = false
    private var liveSongCardEnabled = false
    private var liveSongCardStyle = LiveSongCardStyle.MINIMAL
    private var liveSongLeftAction = LiveSongSideAction.PREVIOUS
    private var liveSongRightAction = LiveSongSideAction.NEXT
    private var liveSongCard: NowPlayingLiveCard? = null
    private var resumePendingExternalAudio = false
    private var observedExternalPlayback = false
    private var otherAppPlayingMedia = false
    private var wasPlayingBeforeAudioFocusLoss = false
    private var externalResumeJob: Job? = null
    private val externalAudioObserver = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            // Non-privileged apps may see anonymized configs; the local player is paused when
            // a permanent focus-loss resume is pending, so only active external media counts.
            val activeOtherMedia = configs.any { config ->
                // Public API returns only active, anonymized external playback configurations.
                config.audioAttributes.usage in setOf(
                        android.media.AudioAttributes.USAGE_MEDIA,
                        android.media.AudioAttributes.USAGE_GAME,
                    )
            }
            otherAppPlayingMedia = activeOtherMedia
            if (!resumePendingExternalAudio) return
            if (activeOtherMedia) {
                observedExternalPlayback = true
                externalResumeJob?.cancel()
            } else if (observedExternalPlayback) {
                scheduleExternalAudioResume()
            }
        }
    }
    private var persistJob: Job? = null
    private var widgetRefreshJob: Job? = null
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
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: ControllerInfo,
            playerCommand: Int,
        ): Int {
            // MediaController.pause() can arrive while the focus loss has already paused the
            // underlying player; no second onPlayWhenReadyChanged(false) is guaranteed.
            if (playerCommand == Player.COMMAND_PLAY_PAUSE || playerCommand == Player.COMMAND_STOP) {
                cancelExternalResume()
                wasPlayingBeforeAudioFocusLoss = false
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onConnectAsync(
            session: MediaSession,
            controller: ControllerInfo,
        ): ListenableFuture<ConnectionResult> {
            val defaultResult = AcceptedResultBuilder(session, controller).build()
            // Android Auto/Automotive hosts must never receive the phone-only Telegram
            // forwarding action, even as an overflow media-session command.
            val isCarClient = session.isAutoCompanionController(controller) ||
                session.isAutomotiveController(controller)
            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .apply { if (!isCarClient) add(forwardCommand) }
                .add(shuffleCommand)
                .add(repeatCommand)
                .add(favoriteCommand)
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
                    .setMediaButtonPreferences(mediaButtons(includeForward = !isCarClient))
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
            val job = serviceScope.launch(Dispatchers.IO) {
                try {
                    val carClient = session.isAutoCompanionController(browser) ||
                        session.isAutomotiveController(browser)
                    val items = mediaLibrary.children(parentId, page, pageSize, forCar = carClient)
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
            future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            val job = serviceScope.launch(Dispatchers.IO) {
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
            future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            val job = serviceScope.launch(Dispatchers.IO) {
                try {
                    // Notify clients with an accurate, bounded result count. The actual paginated
                    // projection is fetched in onGetSearchResult; no unbounded library scan.
                    val count = libraryQueryRepository.search(query, limit = MAX_CAR_SEARCH_RESULTS).size
                    session.notifySearchResultChanged(browser, query, count, params)
                    future.set(LibraryResult.ofVoid(params))
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    throw cancelled
                } catch (_: Throwable) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_IO, params))
                }
            }
            future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            val job = serviceScope.launch(Dispatchers.IO) {
                try {
                    val offset = (page.coerceAtLeast(0).toLong() * pageSize.coerceAtLeast(1).toLong())
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val limit = pageSize.coerceIn(1, MeowzixMediaLibrary.MAX_PAGE_SIZE)
                    val ids = libraryQueryRepository.search(query, limit = MAX_CAR_SEARCH_RESULTS)
                        .drop(offset).take(limit).map { it.id }
                    future.set(LibraryResult.ofItemList(mediaLibrary.searchResults(ids), params))
                } catch (cancelled: CancellationException) {
                    future.cancel(false)
                    throw cancelled
                } catch (_: Throwable) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_IO, params))
                }
            }
            future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
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
            val job = serviceScope.launch(Dispatchers.IO) {
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
            future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
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
                ACTION_OPEN_TELEGRAM_FORWARD -> {
                    val result = when {
                        player.currentMediaItem == null -> SessionResult.RESULT_ERROR_INVALID_STATE
                        openTelegramForwardPicker() -> SessionResult.RESULT_SUCCESS
                        else -> SessionResult.RESULT_ERROR_UNKNOWN
                    }
                    return Futures.immediateFuture(SessionResult(result))
                }
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
            // Publish the new song before onEvents() runs its queue, audio-analysis,
            // favorite and persistence work. No polling delay on actual transitions.
            publishLiveSongTransition()
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
                currentFavorite = false
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
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
                )
            ) {
                updateLiveSongCard()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            runCatching { audioVisualizer.attachToAudioSession(audioSessionId) }
            runCatching { equalizer.attachToAudioSession(audioSessionId) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            when {
                playWhenReady -> {
                    cancelExternalResume()
                    wasPlayingBeforeAudioFocusLoss = true
                }
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
                    wasPlayingBeforeAudioFocusLoss && resumeAfterExternalAudioEnabled && !isRestoring -> {
                    resumePendingExternalAudio = true
                    observedExternalPlayback = otherAppPlayingMedia ||
                        audioManager.activePlaybackConfigurations.any {
                            it.audioAttributes.usage == android.media.AudioAttributes.USAGE_MEDIA
                        }
                    externalResumeJob?.cancel()
                }
                else -> {
                    // The user, controller, timer or end of queue stopped playback.
                    cancelExternalResume()
                    wasPlayingBeforeAudioFocusLoss = false
                }
            }
            // The incoming crossfade player does not own audio focus until handoff.
            // Pausing it during transient focus loss is NOT a user pause; the outgoing
            // Media3-managed player will restore the intended playback after focus gain.
            val crossfade = activeCrossfade
            if (!playWhenReady && crossfade != null &&
                !(crossfade.identitySwitched && crossfade.focusPausedIncoming)
            ) {
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
        liveSongCard = NowPlayingLiveCard(this, openPlayerIntent)
        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(openPlayerIntent)
            .setMediaButtonPreferences(mediaButtons())
            .build()

        // Observe the lightweight favorite-count invalidation so in-app likes update
        // the Live Update and Media3 favorite button immediately, not only on track changes.
        serviceScope.launch {
            libraryQueryRepository.invalidations().collect {
                if (player.currentMediaItem != null) refreshFavoriteState()
            }
        }

        serviceScope.launch {
            settingsRepository.playbackPreferenceSettings.collect { preferences ->
                if (liveSongCardEnabled != preferences.liveSongCardEnabled) {
                    liveSongCardEnabled = preferences.liveSongCardEnabled
                    if (!liveSongCardEnabled) liveSongTransitionRetry.cancel()
                    updateLiveSongCard()
                }
                if (liveSongCardStyle != preferences.liveSongCardStyle ||
                    liveSongLeftAction != preferences.liveSongLeftAction ||
                    liveSongRightAction != preferences.liveSongRightAction
                ) {
                    liveSongCardStyle = preferences.liveSongCardStyle
                    liveSongLeftAction = preferences.liveSongLeftAction
                    liveSongRightAction = preferences.liveSongRightAction
                    updateLiveSongCard()
                }
                val nextNormalization = preferences.loudnessNormalizationEnabled
                if (resumeAfterExternalAudioEnabled != preferences.resumeAfterExternalAudio) {
                    resumeAfterExternalAudioEnabled = preferences.resumeAfterExternalAudio
                    if (resumeAfterExternalAudioEnabled) {
                        audioManager.registerAudioPlaybackCallback(externalAudioObserver, Handler(Looper.getMainLooper()))
                        externalAudioObserver.onPlaybackConfigChanged(audioManager.activePlaybackConfigurations)
                    } else {
                        audioManager.unregisterAudioPlaybackCallback(externalAudioObserver)
                        cancelExternalResume()
                    }
                }
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
                    if (player.isPlaying) {
                        // Keep UI updates independent of possibly slow disk persistence.
                        if (liveSongCardEnabled) updateLiveSongCard()
                        persistPositionSafely()
                    }
                }
            } finally {
                isRestoring = false
            }
        }
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession = mediaSession

    override fun onDestroy() {
        liveSongTransitionRetry.cancel()
        liveSongCard?.cancel()
        if (resumeAfterExternalAudioEnabled) audioManager.unregisterAudioPlaybackCallback(externalAudioObserver)
        cancelExternalResume()
        clearPlaybackRetry()
        cancelCrossfade("service destroyed")
        player.removeListener(playerListener)
        audioVisualizer.release()
        equalizer.release()
        normalizationRefreshJob?.cancel()
        widgetRefreshJob?.cancel()
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cancelExternalResume() {
        externalResumeJob?.cancel()
        externalResumeJob = null
        resumePendingExternalAudio = false
        observedExternalPlayback = false
    }

    private fun scheduleExternalAudioResume() {
        if (!resumeAfterExternalAudioEnabled || !resumePendingExternalAudio) return
        externalResumeJob?.cancel()
        externalResumeJob = serviceScope.launch {
            // Audio playback callbacks can briefly report silence during video transitions.
            delay(1_300L)
            if (resumeAfterExternalAudioEnabled &&
                resumePendingExternalAudio &&
                observedExternalPlayback &&
                !otherAppPlayingMedia &&
                !player.playWhenReady &&
                player.currentMediaItem != null &&
                player.playbackState != Player.STATE_ENDED &&
                sleepTimer.state.value.mode == SleepTimerMode.OFF
            ) {
                cancelExternalResume()
                // Media3 still owns focus arbitration. This only expresses the opt-in intent;
                // Android may refuse focus while the service is no longer foreground.
                player.play()
            }
        }
    }

    private fun publishLiveSongTransition() {
        currentFavorite = false
        updateLiveSongCard()
        // A second deliberate post after 10 seconds, cancelled/replaced on every track
        // transition. Ordinary progress ticks continue to use snapshot deduplication.
        liveSongTransitionRetry.schedule(
            if (liveSongCardEnabled && player.playWhenReady) player.currentMediaItem?.mediaId else null
        )
    }

    private fun updateLiveSongCard(forceRefresh: Boolean = false) {
        liveSongCard?.update(
            player, liveSongCardEnabled, liveSongCardStyle, liveSongLeftAction,
            liveSongRightAction, currentFavorite, forceRefresh = forceRefresh,
        )
    }

    private fun refreshPlaybackWidget() {
        val state = PlaybackWidgetState.fromPlayer(player)
        widgetRefreshJob?.cancel()
        widgetRefreshJob = serviceScope.launch(Dispatchers.IO) {
            runCatching {
                PlaybackWidgetProvider.updateAll(this@PlaybackService.applicationContext, state)
                // A second optional widget. Frames run only while a vinyl widget exists
                // and Media3 reports active playback; no periodic alarm or wake lock.
                VinylWidgetProvider.updateAll(
                    this@PlaybackService.applicationContext,
                    state,
                    reduceMotion = settingsRepository.appearanceSettings.first().reduceMotion,
                )
            }
        }
    }

    private fun resolveRequestedMediaItems(mediaItems: List<MediaItem>): ListenableFuture<List<MediaItem>> {
        if (mediaItems.all { it.localConfiguration != null }) {
            return Futures.immediateFuture(mediaItems)
        }
        val future = SettableFuture.create<List<MediaItem>>()
        val job = serviceScope.launch(Dispatchers.IO) {
            try {
                val resolved = mediaItems.map { requested ->
                    if (requested.localConfiguration != null) {
                        requested
                    } else {
                        val trackId = mediaLibrary.parseTrackId(requested.mediaId)
                        val playable = if (trackId != null) {
                            playbackCatalog.playableTrack(trackId)
                        } else {
                            // Legacy car voice commands arrive through Media3 with a search query
                            // instead of a canonical mediaId. Resolve it through the *same* local,
                            // Persian-aware search and authoritative source resolver as the app.
                            val query = requested.requestMetadata.searchQuery
                                ?: throw IllegalArgumentException("Unsupported media id")
                            val matches = if (query.isBlank()) {
                                libraryQueryRepository.homeFallback(limit = CAR_VOICE_CANDIDATES)
                            } else {
                                libraryQueryRepository.search(query, limit = CAR_VOICE_CANDIDATES)
                            }
                            var firstPlayable: dev.behradhz.meowzix.domain.playback.PlayableTrack? = null
                            for (match in matches) {
                                firstPlayable = playbackCatalog.playableTrack(match.id)
                                if (firstPlayable != null) break
                            }
                            firstPlayable
                        }
                        playable?.toMediaItem(currentPlaybackMode(), currentRepeatMode())
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
        future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
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

    private fun mediaButtons(includeForward: Boolean = true): List<CommandButton> = listOfNotNull(
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
        if (includeForward) CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.ic_forward_paper_plane)
            .setDisplayName("Forward on Telegram")
            .setSessionCommand(forwardCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build() else null,
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
        if (!::mediaSession.isInitialized) return
        mediaSession.setMediaButtonPreferences(mediaButtons())
        // A global state refresh must not reintroduce phone-only actions for car hosts.
        mediaSession.connectedControllers
            .filter { mediaSession.isAutoCompanionController(it) || mediaSession.isAutomotiveController(it) }
            .forEach { mediaSession.setMediaButtonPreferences(it, mediaButtons(includeForward = false)) }
    }

    @Suppress("DEPRECATION")
    private fun openTelegramForwardPicker(): Boolean {
        if (player.currentMediaItem == null) return false
        // Launch the translucent picker directly. The old opaque gateway drew
        // a visible black fullscreen frame before displaying the real panel.
        // SystemUI/OEM firmware still controls the notification shade transition.
        val creatorOptions = if (Build.VERSION.SDK_INT >= 35) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                .toBundle()
        } else {
            null
        }
        val forwardIntent = PendingIntent.getActivity(
            this,
            1,
            TelegramForwardActivity.intent(
                this,
                player.currentMediaItem?.mediaId,
                player.currentMediaItem?.mediaMetadata?.title?.toString(),
                player.currentMediaItem?.mediaMetadata?.artist?.toString(),
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            creatorOptions,
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
        return runCatching {
            if (launchOptions == null) {
                forwardIntent.send()
            } else {
                forwardIntent.send(this, 0, null, null, null, null, launchOptions)
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "Unable to open Telegram forward picker", error)
            false
        }
    }

    private fun syncLogicalCurrentFromPlayer(): Boolean {
        if (progressiveQueue.snapshot() == null) return false
        return progressiveQueue.updateCurrentFromWindowIndex(
            player.currentMediaItemIndex, player.currentMediaItem?.mediaId
        ) || progressiveQueue.updateCurrent(player.currentMediaItem?.mediaId)
    }

    private fun expandProgressiveWindow() {
        if (isExpandingWindow || player.mediaItemCount == 0) return
        val snapshot = progressiveQueue.snapshot() ?: return
        if (!syncLogicalCurrentFromPlayer()) {
            // A transient Media3 timeline mismatch must never discard the logical queue.
            Log.w(TAG, "Media3 window/current item mismatch; preserving the queue")
            return
        }
        isExpandingWindow = true
        try {
            val removeCount = progressiveQueue.trimMaterializedHistory()
            if (removeCount > 0 && removeCount <= player.currentMediaItemIndex) {
                player.removeMediaItems(0, removeCount)
            }

            progressiveQueue.claimGeneratedRefill()?.let { batch ->
                serviceScope.launch {
                    val loaded = runCatching { playbackCatalog.availableTracks(batch.ids) }.getOrDefault(emptyList())
                    val byId = loaded.associateBy { it.id }
                    if (progressiveQueue.appendGeneratedBatch(batch, batch.ids.mapNotNull(byId::get))) {
                        expandProgressiveWindow()
                        schedulePersist()
                    }
                }
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

        if (runtime.focusPausedIncoming) return
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
        // MediaSession.setPlayer() does not invoke onMediaItemTransition on our
        // newly attached listener; publish and schedule the same one-shot retry.
        publishLiveSongTransition()
        runtime.focusBridge = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (activeCrossfade !== runtime) return
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                    suspendCrossfadeForAudioFocus(runtime)
                } else if (playWhenReady) {
                    resumeCrossfadeAfterAudioFocus(runtime)
                } else if (!playWhenReady) {
                    runtime.focusPausedIncoming = false
                    incoming.pause()
                    cancelCrossfade("outgoing audio-focus owner paused")
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (activeCrossfade !== runtime) return
                if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                    suspendCrossfadeForAudioFocus(runtime)
                } else if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                    resumeCrossfadeAfterAudioFocus(runtime)
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

    private fun suspendCrossfadeForAudioFocus(runtime: CrossfadeRuntime) {
        if (!runtime.focusPausedIncoming) {
            runtime.focusPausedIncoming = true
            runtime.focusPausedAtMs = SystemClock.elapsedRealtime()
            runtime.incoming.pause()
        }
    }

    private fun resumeCrossfadeAfterAudioFocus(runtime: CrossfadeRuntime) {
        if (!runtime.focusPausedIncoming ||
            !runtime.outgoing.playWhenReady ||
            runtime.outgoing.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
        ) return
        runtime.focusPausedIncoming = false
        runtime.focusPausedAtMs?.let { pausedAt ->
            runtime.startedAtElapsedRealtimeMs = runtime.startedAtElapsedRealtimeMs?.plus(
                SystemClock.elapsedRealtime() - pausedAt,
            )
        }
        runtime.focusPausedAtMs = null
        runtime.incoming.play()
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
            serviceScope.launch {
                val sourceIds = refreshed.sourceTrackIds.ifEmpty { refreshed.tracks.map { it.id } }
                val loaded = runCatching { playbackCatalog.availableTracks(sourceIds) }.getOrDefault(emptyList())
                val excluded = refreshed.tracks.indices
                    .filter { it > refreshed.currentIndex && refreshed.origins[it] == dev.behradhz.meowzix.domain.playback.QueueItemOrigin.MANUAL }
                    .mapTo(hashSetOf()) { refreshed.tracks[it].id }
                excluded += refreshed.tracks[refreshed.currentIndex].id
                val future = loaded.filterNot { it.id in excluded }
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
                progressiveQueue.replaceFuture(
                    desiredFuture.take(ProgressiveQueue.GENERATED_BATCH_SIZE), nextMode, seed,
                    sourceIds = sourceIds,
                    pendingIds = desiredFuture.drop(ProgressiveQueue.GENERATED_BATCH_SIZE).map { it.id },
                )
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
                // A track transition must never collect the entire 10k-track library just to
                // resolve a single favorite. The playback catalog is already UUID-indexed.
                val track = playbackCatalog.browseTrack(trackId) ?: return@launch
                val favorite = !track.favorite
                libraryRepository.setFavorite(trackId, favorite)
                if (player.currentMediaItem?.mediaId == trackId.toString()) {
                    currentFavorite = favorite
                    refreshMediaButtons()
                    updateLiveSongCard()
                }
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
            updateLiveSongCard()
            return
        }
        serviceScope.launch {
            val favorite = try {
                playbackCatalog.browseTrack(trackId)?.favorite == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            // An older lookup must not overwrite the favorite state of a newer track.
            if (player.currentMediaItem?.mediaId == trackId.toString()) {
                currentFavorite = favorite
                refreshMediaButtons()
                updateLiveSongCard()
            }
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
                requestedOrigins = saved.logicalOrigins,
                requestedOccurrenceIds = saved.logicalOccurrenceIds.mapNotNull {
                    runCatching { UUID.fromString(it) }.getOrNull()
                }.takeIf { it.size == saved.logicalMediaIds.size },
                requestedGroupIds = saved.logicalGroupIds.map { id ->
                    id?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                }.takeIf { it.size == saved.logicalMediaIds.size },
                requestedGroups = saved.logicalGroups.mapNotNull {
                    runCatching {
                        dev.behradhz.meowzix.domain.playback.QueueGroup(
                            UUID.fromString(it.id),
                            dev.behradhz.meowzix.domain.playback.QueueGroupKind.valueOf(it.kind),
                            it.title,
                        )
                    }.getOrNull()
                },
                sourceIds = saved.sourceMediaIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() },
                pendingIds = saved.pendingMediaIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() },
                generatedStartedCount = saved.generatedStarted,
                nextGenerationThreshold = saved.nextGenerationAt,
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
                logicalOrigins = logical?.origins.orEmpty(),
                logicalOccurrenceIds = logical?.occurrenceIds?.map(UUID::toString).orEmpty(),
                logicalGroupIds = logical?.groupIds?.map { it?.toString() }.orEmpty(),
                logicalGroups = logical?.groups?.values?.map {
                    dev.behradhz.meowzix.playback.persistence.PersistedQueueGroup(
                        it.id.toString(), it.kind.name, it.title
                    )
                }.orEmpty(),
                sourceMediaIds = logical?.sourceTrackIds?.map(UUID::toString).orEmpty(),
                pendingMediaIds = logical?.pendingGeneratedIds?.map(UUID::toString).orEmpty(),
                generatedStarted = logical?.generatedStarted ?: 0,
                nextGenerationAt = logical?.nextGenerationAt ?: 90,
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
                    val candidateIds = progressive.sourceTrackIds
                    val candidates = if (candidateIds.isNotEmpty()) {
                        runCatching { playbackCatalog.availableTracks(candidateIds) }.getOrDefault(emptyList())
                    } else progressive.tracks
                    if (candidates.isEmpty()) return@launch
                    var seed: Long? = progressive.shuffleSeed
                    val nextOrder = when (progressive.playbackMode) {
                        PlaybackMode.PURE_SHUFFLE -> {
                            PureShuffleEngine.newCycle(candidates.indices.toList()).let { cycle ->
                                seed = cycle.seed
                                val order = cycle.order.toMutableList()
                                if (previousLast != null && order.size > 1 &&
                                    candidates[order.first()].id == previousLast.id) {
                                    val swapAt = (1 until order.size).firstOrNull {
                                        candidates[order[it]].id != previousLast.id
                                    }
                                    if (swapAt != null) {
                                        val first = order[0]
                                        order[0] = order[swapAt]
                                        order[swapAt] = first
                                    }
                                }
                                order.map(candidates::get)
                            }
                        }
                        PlaybackMode.ORDERED, PlaybackMode.SMART_SHUFFLE -> candidates
                    }
                    val initial = if (candidateIds.isNotEmpty()) {
                        nextOrder.take(ProgressiveQueue.GENERATED_BATCH_SIZE)
                    } else nextOrder
                    val plan = progressiveQueue.start(
                        orderedTracks = initial,
                        requestedStartIndex = 0,
                        mode = progressive.playbackMode,
                        repeat = progressive.repeatMode,
                        seed = seed,
                        sourceIds = candidateIds,
                        pendingIds = if (candidateIds.isNotEmpty()) nextOrder.drop(initial.size).map { it.id }
                            else emptyList(),
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
        // STATE_ENDED is reached at the end of this exact queue. Capture the terminal item
        // before dispatching an asynchronous restart; currentMediaItem/currentIndex can
        // already have advanced or reset by the time the coroutine is scheduled.
        val previousLastItem = currentItems.last()
        isChangingQueueCycle = true
        serviceScope.launch {
            try {
                val repeatMode = currentItems.first().repeatMode()
                val shouldContinue = repeatMode == RepeatMode.ALL && player.playWhenReady
                if (repeatMode != RepeatMode.ALL) return@launch
                // A legacy/controller-provided queue is already the authoritative eligible set.
                // Expanding it through the whole library at the cycle boundary can block playback,
                // leak unrelated tracks into a selected collection, and violates Pure Shuffle's
                // collection isolation. Re-permute exactly the current canonical queue instead.
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
        var focusPausedIncoming: Boolean = false,
        var focusPausedAtMs: Long? = null,
    )

    private companion object {
        const val TAG = "MeowzixPlayback"
        const val MAX_CAR_SEARCH_RESULTS = 200
        const val CAR_VOICE_CANDIDATES = 20
        const val TDLIB_SCHEME = "meowzix-tdlib"
        const val ACTION_OPEN_TELEGRAM_FORWARD = "dev.behradhz.meowzix.action.OPEN_TELEGRAM_FORWARD"
        const val ACTION_TOGGLE_SHUFFLE = LiveSongSessionActions.SHUFFLE
        const val ACTION_CYCLE_REPEAT = LiveSongSessionActions.REPEAT
        const val ACTION_TOGGLE_FAVORITE = LiveSongSessionActions.FAVORITE
        const val MAX_REMOTE_PLAYBACK_RETRIES = 3
        const val REMOTE_PLAYBACK_RETRY_BASE_DELAY_MS = 600L
        const val FORWARD_INCREMENT_MS = 15_000L
        const val PERSIST_DEBOUNCE_MS = 250L
        const val POSITION_SAVE_INTERVAL_MS = 1_000L
        const val LIVE_SONG_TRANSITION_RETRY_MS = 10_000L
        const val SLEEP_TIMER_TICK_MS = 250L
        const val CROSSFADE_TICK_MS = 50L
    }
}

private fun RepeatMode.toPlayerRepeatMode(progressive: Boolean): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && !progressive -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}
