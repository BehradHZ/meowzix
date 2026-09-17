package dev.behradhz.meowzix.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackSession
import dev.behradhz.meowzix.playback.persistence.PlaybackStateStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var stateStore: PlaybackStateStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var persistJob: Job? = null
    private var isRestoring = true

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                schedulePersist()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val nextIndex = player.currentMediaItemIndex + 1
            if (nextIndex in 0 until player.mediaItemCount) {
                player.seekTo(nextIndex, 0)
                player.prepare()
                player.play()
            } else {
                player.pause()
                player.stop()
            }
            schedulePersist()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { it.addListener(playerListener) }
        mediaSession = MediaSession.Builder(this, player).build()

        serviceScope.launch {
            restoreSession()
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) persistNow()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun restoreSession() {
        val saved = stateStore.load()
        if (player.mediaItemCount == 0 && saved.items.isNotEmpty()) {
            player.repeatMode = saved.repeatMode.toPlayerRepeatMode()
            player.setMediaItems(saved.items.map { it.toMediaItem() }, saved.currentIndex, saved.positionMs)
            player.prepare()
        }
        isRestoring = false
    }

    private fun schedulePersist() {
        if (isRestoring) return
        persistJob?.cancel()
        persistJob = serviceScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        val items = (0 until player.mediaItemCount)
            .mapNotNull { index -> player.getMediaItemAt(index).toPersistedPlaybackItem() }
        if (items.isEmpty()) return
        stateStore.save(
            PersistedPlaybackSession(
                items = items,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0),
                playbackMode = PlaybackMode.ORDERED,
                repeatMode = player.repeatMode.toDomainRepeatMode(),
            ),
        )
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 250L
        const val POSITION_SAVE_INTERVAL_MS = 1_000L
    }
}

private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
}

private fun Int.toDomainRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}
