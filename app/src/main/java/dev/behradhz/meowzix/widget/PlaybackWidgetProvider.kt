package dev.behradhz.meowzix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.behradhz.meowzix.MainActivity
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.playback.PlaybackService
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaybackWidgetState(
    val title: String?,
    val artist: String?,
    val artworkRef: String?,
    val isPlaying: Boolean,
) {
    val hasTrack: Boolean get() = !title.isNullOrBlank()

    companion object {
        fun fromPlayer(player: Player): PlaybackWidgetState {
            val item = player.currentMediaItem
            return PlaybackWidgetState(
                title = item?.mediaMetadata?.title?.toString()?.takeIf(String::isNotBlank),
                artist = item?.mediaMetadata?.artist?.toString()?.takeIf(String::isNotBlank),
                artworkRef = item?.mediaMetadata?.artworkUri?.toString(),
                isPlaying = player.isPlaying,
            )
        }

        val Empty = PlaybackWidgetState(null, null, null, false)
    }
}

@UnstableApi
class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Clear potentially stale metadata immediately, then asynchronously read the exact
        // authoritative Media3 session. No polling and no duplicate player are involved.
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context, PlaybackWidgetState.Empty, null))
        }

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var controller: MediaController? = null
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                controller = MediaController.Builder(context, token).buildAsync().await()
                render(
                    context = context,
                    widgetIds = appWidgetIds,
                    state = PlaybackWidgetState.fromPlayer(controller),
                )
            } catch (_: Throwable) {
                // The deterministic empty state rendered above is the fallback when the service
                // cannot be reached or recreated.
            } finally {
                controller?.release()
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        /**
         * Called by PlaybackService only on meaningful player events (item/state/timeline changes).
         * Artwork decoding happens off main and is bounded for RemoteViews/Binder safety.
         */
        suspend fun updateAll(context: Context, state: PlaybackWidgetState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
            if (ids.isEmpty()) return
            render(context, ids, state)
        }

        private suspend fun render(
            context: Context,
            widgetIds: IntArray,
            state: PlaybackWidgetState,
        ) {
            val artwork = withContext(Dispatchers.IO) {
                state.artworkRef?.let { decodeArtwork(context, it, MAX_ARTWORK_PX) }
            }
            val manager = AppWidgetManager.getInstance(context)
            widgetIds.forEach { widgetId ->
                manager.updateAppWidget(widgetId, buildViews(context, state, artwork))
            }
        }

        private fun buildViews(
            context: Context,
            state: PlaybackWidgetState,
            artwork: Bitmap?,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_playback).apply {
            setOnClickPendingIntent(R.id.widget_artwork, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_artist, openAppIntent(context))
            setOnClickPendingIntent(
                R.id.widget_previous,
                mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, REQUEST_PREVIOUS),
            )
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, REQUEST_PLAY_PAUSE),
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT, REQUEST_NEXT),
            )

            setTextViewText(R.id.widget_title, state.title ?: "Nothing playing")
            setTextViewText(
                R.id.widget_artist,
                state.artist ?: if (state.hasTrack) "Unknown artist" else "Open Meowzix to choose a track",
            )
            if (artwork != null) {
                setImageViewBitmap(R.id.widget_artwork, artwork)
            } else {
                setImageViewResource(R.id.widget_artwork, R.drawable.meowzix_logo)
            }
            setImageViewResource(
                R.id.widget_play_pause,
                if (state.isPlaying) R.drawable.widget_pause else R.drawable.widget_play,
            )
            setContentDescription(
                R.id.widget_play_pause,
                if (state.isPlaying) "Pause" else "Play",
            )
            setContentDescription(
                R.id.widget_artwork,
                state.title?.let { "$it cover art" } ?: "Meowzix",
            )
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOW_PLAYING
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /**
         * Route widget transport buttons through Media3's receiver. The receiver resolves the
         * existing MediaLibraryService (PlaybackService), so widget, notification, hardware keys
         * and in-app controls all operate the exact same player timeline and queue.
         */
        private fun mediaButtonIntent(context: Context, keyCode: Int, requestCode: Int): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(context, MediaButtonReceiver::class.java)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun decodeArtwork(context: Context, ref: String, maxSizePx: Int): Bitmap? {
            val uri = runCatching(Uri::parse).getOrNull() ?: return null

            fun open() = when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.let(::FileInputStream)
                "content", "android.resource" -> context.contentResolver.openInputStream(uri)
                else -> null
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { open()?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > maxSizePx * 2 || bounds.outHeight / sample > maxSizePx * 2) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            return runCatching { open()?.use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
        }

        private const val MAX_ARTWORK_PX = 256
        private const val REQUEST_OPEN_APP = 4100
        private const val REQUEST_PREVIOUS = 4101
        private const val REQUEST_PLAY_PAUSE = 4102
        private const val REQUEST_NEXT = 4103
    }
}
