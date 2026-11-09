package dev.behradhz.meowzix.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * One internal command route for app widgets and the promoted Live Song notification.
 *
 * These controls run in our app process. When the playback service is alive, invoking
 * its existing ExoPlayer on main is deterministic and avoids a fresh MediaController
 * connection for every click. When it is not alive (e.g. a paused player after process
 * death), a short-lived Media3 controller reconnects to the authoritative session.
 *
 * Only non-exported receivers may call this bridge. Never expose it as a service Intent
 * action, and never create a second player.
 */
internal sealed interface ExternalPlaybackAction {
    data object TogglePlayPause : ExternalPlaybackAction
    data object Pause : ExternalPlaybackAction
    data object Previous : ExternalPlaybackAction
    data object Next : ExternalPlaybackAction
    data object Favorite : ExternalPlaybackAction
    data object Shuffle : ExternalPlaybackAction
    data object Repeat : ExternalPlaybackAction
    data object Forward : ExternalPlaybackAction
    data class QueueSelection(val index: Int, val mediaId: String) : ExternalPlaybackAction
}

internal object PlaybackServiceBridge {
    // Both receivers and PlaybackService use the app's main looper.
    private var service: PlaybackService? = null

    fun register(instance: PlaybackService) {
        check(Looper.myLooper() == Looper.getMainLooper())
        service = instance
    }

    fun unregister(instance: PlaybackService) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (service === instance) service = null
    }

    fun dispatchIfRunning(action: ExternalPlaybackAction): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val current = service ?: return false
        current.executeExternalPlaybackAction(action)
        return true
    }
}

@OptIn(markerClass = [UnstableApi::class])
internal object ExternalPlaybackDispatcher {
    private const val TAG = "MeowzixSystemControls"
    private const val FORWARD_ACTION = "dev.behradhz.meowzix.action.OPEN_TELEGRAM_FORWARD"

    fun enqueue(context: Context, action: ExternalPlaybackAction, pending: BroadcastReceiver.PendingResult) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            try {
                dispatch(context, action)
            } catch (error: Exception) {
                Log.w(TAG, "System playback action failed: ${action.javaClass.simpleName}", error)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }

    /**
     * Call on Dispatchers.Main. Returns after a command has been submitted to the
     * active player or Media3 session, not merely after a PendingIntent was received.
     */
    suspend fun dispatch(context: Context, action: ExternalPlaybackAction) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (PlaybackServiceBridge.dispatchIfRunning(action)) return

        var future: ListenableFuture<MediaController>? = null
        var controller: MediaController? = null
        try {
            val connecting = MediaController.Builder(
                context.applicationContext,
                SessionToken(
                    context.applicationContext,
                    ComponentName(context.applicationContext, PlaybackService::class.java),
                ),
            ).buildAsync()
            future = connecting
            controller = withTimeout(5_000L) { connecting.await() }
            dispatchToController(controller, action)
        } catch (error: Exception) {
            Log.w(TAG, "Media3 controller command failed: ${action.javaClass.simpleName}", error)
        } finally {
            // Media3 guarantees that release waits for pending player commands to
            // be delivered to the session (or times out); release only after dispatch.
            if (controller != null) controller.release()
            else future?.let(MediaController::releaseFuture)
        }
    }

    private suspend fun dispatchToController(controller: MediaController, action: ExternalPlaybackAction) {
        when (action) {
            ExternalPlaybackAction.TogglePlayPause ->
                if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                    if (controller.playWhenReady && controller.playbackState != Player.STATE_ENDED) {
                        controller.pause()
                    } else {
                        if (controller.playbackState == Player.STATE_IDLE &&
                            controller.isCommandAvailable(Player.COMMAND_PREPARE)
                        ) controller.prepare()
                        controller.play()
                    }
                }
            ExternalPlaybackAction.Pause ->
                if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) controller.pause()
            ExternalPlaybackAction.Previous ->
                if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
                    controller.hasPreviousMediaItem()
                ) controller.seekToPreviousMediaItem()
            ExternalPlaybackAction.Next ->
                if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
                    controller.hasNextMediaItem()
                ) controller.seekToNextMediaItem()
            is ExternalPlaybackAction.QueueSelection -> {
                if (action.mediaId.isNotBlank() &&
                    controller.isCommandAvailable(Player.COMMAND_GET_TIMELINE) &&
                    controller.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) &&
                    action.index in 0 until controller.mediaItemCount &&
                    controller.getMediaItemAt(action.index).mediaId == action.mediaId
                ) {
                    controller.seekTo(action.index, 0L)
                    if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) controller.play()
                }
            }
            ExternalPlaybackAction.Favorite -> custom(controller, LiveSongSessionActions.FAVORITE)
            ExternalPlaybackAction.Shuffle -> custom(controller, LiveSongSessionActions.SHUFFLE)
            ExternalPlaybackAction.Repeat -> custom(controller, LiveSongSessionActions.REPEAT)
            ExternalPlaybackAction.Forward -> custom(controller, FORWARD_ACTION)
        }
    }

    private suspend fun custom(controller: MediaController, name: String) {
        val command = SessionCommand(name, Bundle.EMPTY)
        if (!controller.isSessionCommandAvailable(command)) {
            Log.w(TAG, "Session does not permit requested command: $name")
            return
        }
        val result = withTimeout(3_000L) {
            controller.sendCustomCommand(command, Bundle.EMPTY).await()
        }
        if (result.resultCode != SessionResult.RESULT_SUCCESS) {
            Log.w(TAG, "Custom command rejected: $name, code=${result.resultCode}")
        }
    }
}
