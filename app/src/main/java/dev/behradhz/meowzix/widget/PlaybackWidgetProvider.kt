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
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
import kotlinx.coroutines.withTimeout

data class PlaybackWidgetState(
    val title: String?,
    val artist: String?,
    val artworkRef: String?,
    val isPlaying: Boolean,
    val isFavorite: Boolean = false,
    val mediaId: String? = null,
    val queueIndex: Int = -1,
    val queue: List<WidgetQueueTrack> = emptyList(),
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
                mediaId = item?.mediaId,
                queueIndex = player.currentMediaItemIndex,
                queue = player.widgetQueue(),
            )
        }

        val Empty = PlaybackWidgetState(null, null, null, false)
    }
}

data class PlaybackWidgetRenderModel(
    val title: String,
    val artist: String,
    val playPauseIcon: Int,
    val playPauseDescription: String,
    val artworkDescription: String,
)

internal fun PlaybackWidgetState.toRenderModel() = PlaybackWidgetRenderModel(
    title = title ?: "Nothing playing",
    artist = artist ?: if (hasTrack) "Unknown artist" else "Open Meowzix to choose a track",
    playPauseIcon = if (isPlaying) R.drawable.widget_pause else R.drawable.widget_play,
    playPauseDescription = if (isPlaying) "Pause" else "Play",
    artworkDescription = title?.let { "$it cover art" } ?: "Meowzix",
)

/** Responsive classic widget. The separate 2x2 vinyl widget is unaffected. */
@OptIn(markerClass = [UnstableApi::class])
class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        reloadFromSession(context, appWidgetIds, goAsync())
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, manager, widgetId, newOptions)
        reloadFromSession(context, intArrayOf(widgetId), goAsync())
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        PlaybackWidgetSnapshotStore.clearMenu(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    companion object {
        private fun ids(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, PlaybackWidgetProvider::class.java),
        )

        /** Save before expensive artwork decoding. Never clear a working widget on refresh. */
        suspend fun updateAll(context: Context, state: PlaybackWidgetState) {
            if (ids(context).isEmpty()) return
            PlaybackWidgetSnapshotStore.save(context, state)
            render(context, ids(context), state)
        }

        suspend fun refreshCached(context: Context, widgetId: Int) {
            render(context, intArrayOf(widgetId), PlaybackWidgetSnapshotStore.load(context, assumePaused = false))
        }

        private fun reloadFromSession(
            context: Context,
            widgetIds: IntArray,
            pending: android.content.BroadcastReceiver.PendingResult,
        ) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            scope.launch {
                try {
                // Retain the last usable state while a stopped/recreated service reconnects.
                val cached = PlaybackWidgetSnapshotStore.load(context)
                render(context, widgetIds, cached)
                var controller: MediaController? = null
                var future: ListenableFuture<MediaController>? = null
                try {
                    val connecting = MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    ).buildAsync()
                    future = connecting
                    controller = withTimeout(6_000L) { connecting.await() }
                    val live = PlaybackWidgetState.fromPlayer(controller)
                    val favorite = cached.mediaId == live.mediaId && cached.isFavorite
                    val state = live.copy(isFavorite = favorite)
                    PlaybackWidgetSnapshotStore.save(context, state)
                    render(context, widgetIds, state)
                } catch (_: Exception) {
                    // Cached paused metadata remains visible; transport still opens Media3.
                } finally {
                    if (controller != null) controller.release()
                    else future?.let(MediaController::releaseFuture)
                }
                } finally {
                    pending.finish()
                    scope.cancel()
                }
            }
        }

        private suspend fun render(context: Context, widgetIds: IntArray, state: PlaybackWidgetState) {
            val artwork = withContext(Dispatchers.IO) {
                state.artworkRef?.let { decodeArtwork(context, it, 192) }
            }
            val manager = AppWidgetManager.getInstance(context)
            widgetIds.forEach { id ->
                val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RemoteViews(
                        mapOf(
                            SizeF(250f, 118f) to buildViews(context, id, state, artwork, false),
                            SizeF(250f, 205f) to buildViews(context, id, state, artwork, true),
                        ),
                    )
                } else {
                    val opts = manager.getAppWidgetOptions(id)
                    val height = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 118)
                    buildViews(context, id, state, artwork, height >= 190)
                }
                manager.updateAppWidget(id, views)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    @Suppress("DEPRECATION")
                    manager.notifyAppWidgetViewDataChanged(id, R.id.widget_queue)
                }
            }
        }

        private fun buildViews(
            context: Context,
            widgetId: Int,
            state: PlaybackWidgetState,
            artwork: Bitmap?,
            expanded: Boolean,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_playback).apply {
            val model = state.toRenderModel()
            val menu = PlaybackWidgetSnapshotStore.menuExpanded(context, widgetId)
            setOnClickPendingIntent(R.id.widget_artwork, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_artist, openAppIntent(context))

            setOnClickPendingIntent(R.id.widget_previous, control(context, widgetId, WidgetAction.PREVIOUS))
            setOnClickPendingIntent(R.id.widget_play_pause, control(context, widgetId, WidgetAction.PLAY_PAUSE))
            setOnClickPendingIntent(R.id.widget_next, control(context, widgetId, WidgetAction.NEXT))
            setOnClickPendingIntent(R.id.widget_more, control(context, widgetId, WidgetAction.MENU))
            setOnClickPendingIntent(R.id.widget_favorite, control(context, widgetId, WidgetAction.FAVORITE))
            setOnClickPendingIntent(R.id.widget_forward, control(context, widgetId, WidgetAction.FORWARD))
            setOnClickPendingIntent(R.id.widget_shuffle, control(context, widgetId, WidgetAction.SHUFFLE))
            setOnClickPendingIntent(R.id.widget_repeat, control(context, widgetId, WidgetAction.REPEAT))

            setTextViewText(R.id.widget_title, model.title)
            setTextViewText(R.id.widget_artist, model.artist)
            if (artwork != null) setImageViewBitmap(R.id.widget_artwork, artwork)
            else setImageViewResource(R.id.widget_artwork, R.drawable.meowzix_logo)
            setImageViewResource(R.id.widget_play_pause, model.playPauseIcon)
            setContentDescription(R.id.widget_play_pause, model.playPauseDescription)
            setContentDescription(R.id.widget_artwork, model.artworkDescription)
            setContentDescription(
                R.id.widget_favorite,
                if (state.isFavorite) "Remove from favorites" else "Add to favorites",
            )
            setContentDescription(
                R.id.widget_more,
                if (menu) "Hide extra actions" else "Show extra actions",
            )
            setViewVisibility(R.id.widget_transport, if (menu) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_overflow_actions, if (menu) View.VISIBLE else View.GONE)

            setViewVisibility(R.id.widget_queue_section, if (expanded) View.VISIBLE else View.GONE)
            val items = state.queue
            setTextViewText(R.id.widget_queue_heading, "QUEUE  ·  ${items.size} tracks")
            setViewVisibility(R.id.widget_queue, if (items.isEmpty()) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_queue_empty, if (items.isEmpty()) View.VISIBLE else View.GONE)
            if (expanded && items.isNotEmpty()) {
                val template = PendingIntent.getBroadcast(
                    context, 61000 + widgetId,
                    Intent(context, PlaybackWidgetActionReceiver::class.java).apply {
                        action = PlaybackWidgetActionReceiver.ACTION_QUEUE
                        data = Uri.parse("meowzix://widget/$widgetId/queue")
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                setPendingIntentTemplate(R.id.widget_queue, template)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val builder = RemoteViews.RemoteCollectionItems.Builder()
                        .setViewTypeCount(1)
                        .setHasStableIds(true)
                    items.forEach { item -> builder.addItem(item.index.toLong(), queueRow(context, state, item)) }
                    setRemoteAdapter(R.id.widget_queue, builder.build())
                } else {
                    @Suppress("DEPRECATION")
                    setRemoteAdapter(
                        R.id.widget_queue,
                        Intent(context, PlaybackWidgetQueueService::class.java).apply {
                            data = Uri.parse("meowzix://widget/$widgetId/rows")
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        },
                    )
                }
            }
        }

        internal fun queueRow(context: Context, state: PlaybackWidgetState, item: WidgetQueueTrack) =
            RemoteViews(context.packageName, R.layout.widget_playback_queue_row).apply {
                val current = item.index == state.queueIndex && item.mediaId == state.mediaId
                setTextViewText(R.id.widget_queue_position, if (current) "▶" else "${item.index + 1}")
                setTextViewText(R.id.widget_queue_title, item.title)
                setTextViewText(R.id.widget_queue_artist, if (current) "NOW PLAYING" else item.artist ?: "Unknown artist")
                setOnClickFillInIntent(
                    R.id.widget_queue_row,
                    Intent().apply {
                        putExtra(PlaybackWidgetActionReceiver.EXTRA_QUEUE_INDEX, item.index)
                        putExtra(PlaybackWidgetActionReceiver.EXTRA_MEDIA_ID, item.mediaId)
                    },
                )
            }

        private fun control(context: Context, widgetId: Int, action: WidgetAction) =
            PendingIntent.getBroadcast(
                context,
                6000 + widgetId * 16 + action.ordinal,
                Intent(context, PlaybackWidgetActionReceiver::class.java).apply {
                    this.action = PlaybackWidgetActionReceiver.ACTION_CONTROL
                    data = Uri.parse("meowzix://widget/$widgetId/${action.name}")
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(PlaybackWidgetActionReceiver.EXTRA_ACTION, action.name)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun openAppIntent(context: Context) = PendingIntent.getActivity(
            context, 4100,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOW_PLAYING
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun decodeArtwork(context: Context, ref: String, maxSizePx: Int): Bitmap? {
            val uri = runCatching { Uri.parse(ref) }.getOrNull() ?: return null
            fun open() = when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.let(::FileInputStream)
                "content", "android.resource" -> context.contentResolver.openInputStream(uri)
                else -> null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { open()?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxSizePx || bounds.outHeight / sample > maxSizePx)
                sample *= 2
            return runCatching {
                open()?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                    })
                }
            }.getOrNull()
        }
    }
}

/** Menu shortcuts; all playback actions must go through the live Media3 session. */
internal enum class WidgetAction { PLAY_PAUSE, PREVIOUS, NEXT, MENU, FAVORITE, FORWARD, SHUFFLE, REPEAT }

