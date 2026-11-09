package dev.behradhz.meowzix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.SizeF
import androidx.core.os.BundleCompat
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
    val queueTotal: Int = queue.size,
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
                queueTotal = (player.mediaItemCount - player.currentMediaItemIndex.coerceAtLeast(0) - 1).coerceAtLeast(0),
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
        WidgetInstancePreferences.remove(context, appWidgetIds)
        PlaybackWidgetSnapshotStore.clearMenu(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    companion object {
        fun requestAppearanceRefresh(context: Context) {
            for (provider in listOf(PlaybackWidgetProvider::class.java, VinylWidgetProvider::class.java)) {
                val ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(ComponentName(context, provider))
                if (ids.isNotEmpty()) context.sendBroadcast(Intent(context, provider).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }

        private fun ids(context: Context) = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, PlaybackWidgetProvider::class.java),
        )

        /** Save before expensive artwork decoding. Never clear a working widget on refresh. */
        suspend fun updateAll(context: Context, state: PlaybackWidgetState) {
            if (ids(context).isEmpty()) return
            PlaybackWidgetSnapshotStore.save(context, state)
            render(context, ids(context), state)
        }

        fun hasWidgets(context: Context) = ids(context).isNotEmpty()

        /**
         * Lightweight text-only ticker for long track titles. RemoteViews marquee cannot
         * reliably acquire focus from launcher hosts; this works without custom views.
         */
        fun advanceTitle(context: Context) {
            val active = ids(context)
            if (active.isEmpty()) return
            val state = PlaybackWidgetSnapshotStore.load(context, assumePaused = false)
            val title = state.title?.takeIf(String::isNotBlank) ?: return
            if (!state.isPlaying) return
            val window = PlaybackWidgetTitleTicker.next(state.mediaId, title)
            if (window == title) return
            val update = RemoteViews(context.packageName, R.layout.widget_playback).apply {
                setTextViewText(R.id.widget_title, window)
            }
            // Text-only partial actions are safe; bitmap updates always use full updates.
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(active, update)
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
                    val logical = widgetLogicalQueue(context)
                    val state = live.copy(
                        isFavorite = favorite,
                        queueIndex = logical?.currentIndex ?: live.queueIndex,
                        queueTotal = logical?.let {
                            (it.tracks.size - it.currentIndex.coerceAtLeast(0) - 1).coerceAtLeast(0)
                        } ?: live.queueTotal,
                    )
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
                val palette = widgetPalette(context, id)
                fun layoutFor(width: Float, height: Float) = when {
                    width < 88f -> narrowViews(context, id, state, artwork, palette)
                    // Allow a 44dp hit area for each control and space for the
                    // track title; narrower widgets use the compact view.
                    width < 285f -> mediumViews(context, id, state, artwork, palette)
                    else -> buildViews(
                        context, id, state, artwork, height >= 90f, palette,
                        showQueueHeading = height >= 150f,
                    )
                }
                val options = manager.getAppWidgetOptions(id)
                val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val sizes = BundleCompat.getParcelableArrayList(
                        options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java,
                    )?.filter { it.width > 0f && it.height > 0f }?.distinct()?.take(8)
                    val layouts = if (!sizes.isNullOrEmpty()) {
                        sizes.associateWith { layoutFor(it.width, it.height) }
                    } else mapOf(
                        SizeF(48f, 48f) to layoutFor(48f, 48f),
                        SizeF(130f, 48f) to layoutFor(130f, 48f),
                        SizeF(192f, 48f) to layoutFor(192f, 48f),
                        SizeF(192f, 98f) to layoutFor(192f, 98f),
                    )
                    RemoteViews(layouts)
                } else {
                    layoutFor(
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).toFloat(),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 55).toFloat(),
                    )
                }
                manager.updateAppWidget(id, views)
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_queue)
            }
        }

        private fun narrowViews(
            context: Context,
            widgetId: Int,
            state: PlaybackWidgetState,
            artwork: Bitmap?,
            palette: WidgetPalette,
        ) = RemoteViews(context.packageName, R.layout.widget_playback_narrow).apply {
            setOnClickPendingIntent(R.id.widget_artwork, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_play_pause, control(context, widgetId, WidgetAction.PLAY_PAUSE))
            if (artwork != null) setImageViewBitmap(R.id.widget_artwork, artwork)
            else setImageViewResource(R.id.widget_artwork, R.drawable.meowzix_logo)
            val model = state.toRenderModel()
            setImageViewResource(R.id.widget_play_pause, model.playPauseIcon)
            setContentDescription(R.id.widget_play_pause, model.playPauseDescription)
            setContentDescription(R.id.widget_artwork, "${model.title} – ${model.artist}. Open player")
            applyPalette(palette, R.layout.widget_playback_narrow)
        }

        private fun mediumViews(
            context: Context,
            widgetId: Int,
            state: PlaybackWidgetState,
            artwork: Bitmap?,
            palette: WidgetPalette,
        ) = RemoteViews(context.packageName, R.layout.widget_playback_medium).apply {
            val model = state.toRenderModel()
            setOnClickPendingIntent(R.id.widget_artwork, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_artist, openAppIntent(context))
            setOnClickPendingIntent(R.id.widget_play_pause, control(context, widgetId, WidgetAction.PLAY_PAUSE))
            if (artwork != null) setImageViewBitmap(R.id.widget_artwork, artwork)
            else setImageViewResource(R.id.widget_artwork, R.drawable.meowzix_logo)
            setTextViewText(R.id.widget_title, widgetStyledText(model.title, palette))
            setTextViewText(R.id.widget_artist, widgetStyledText(model.artist, palette))
            setImageViewResource(R.id.widget_play_pause, model.playPauseIcon)
            setContentDescription(R.id.widget_play_pause, model.playPauseDescription)
            applyPalette(palette, R.layout.widget_playback_medium)
        }

        private fun buildViews(
            context: Context,
            widgetId: Int,
            state: PlaybackWidgetState,
            artwork: Bitmap?,
            expanded: Boolean,
            palette: WidgetPalette,
            showQueueHeading: Boolean,
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

            setTextViewText(R.id.widget_title, widgetStyledText(model.title, palette))
            setTextViewText(R.id.widget_artist, widgetStyledText(model.artist, palette))
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
            setViewVisibility(R.id.widget_queue_heading, if (showQueueHeading) View.VISIBLE else View.GONE)
            val total = state.queueTotal
            setTextViewText(
                R.id.widget_queue_heading,
                widgetStyledText(if (palette.monochrome) "●  QUEUE  $total" else "QUEUE  ·  $total tracks", palette),
            )
            setViewVisibility(R.id.widget_queue, if (total == 0) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_queue_empty, if (total == 0) View.VISIBLE else View.GONE)
            if (expanded && total > 0) {
                val template = PendingIntent.getBroadcast(
                    context, 61000 + widgetId,
                    Intent(context, PlaybackWidgetActionReceiver::class.java).apply {
                        action = PlaybackWidgetActionReceiver.ACTION_QUEUE
                        data = Uri.parse("meowzix://widget/$widgetId/queue")
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                setPendingIntentTemplate(R.id.widget_queue, template)
                // The service supplies the entire logical queue lazily, not only
                // Media3's short materialized playback window.
                @Suppress("DEPRECATION")
                setRemoteAdapter(
                    R.id.widget_queue,
                    Intent(context, PlaybackWidgetQueueService::class.java).apply {
                        data = Uri.parse("meowzix://widget/$widgetId/rows")
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    },
                )
            }
            applyPalette(palette, R.layout.widget_playback)
        }

        private fun RemoteViews.applyPalette(palette: WidgetPalette, layoutId: Int) {
            setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (palette.style == WidgetVisualStyle.MINIMAL)
                    R.drawable.widget_playback_background_flat else R.drawable.widget_playback_background,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setColorStateList(
                    R.id.widget_root,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(palette.background),
                )
                setColorStateList(
                    R.id.widget_play_pause, "setImageTintList",
                    ColorStateList.valueOf(palette.accent),
                )
                setColorStateList(
                    R.id.widget_play_pause, "setBackgroundTintList",
                    ColorStateList.valueOf(palette.controlBackground),
                )
                if (layoutId == R.layout.widget_playback) {
                    listOf(
                        R.id.widget_previous, R.id.widget_next, R.id.widget_more,
                        R.id.widget_favorite, R.id.widget_forward,
                        R.id.widget_shuffle, R.id.widget_repeat,
                    ).forEach { id ->
                        setColorStateList(id, "setImageTintList", ColorStateList.valueOf(palette.accent))
                    }
                    setColorStateList(
                        R.id.widget_more, "setBackgroundTintList",
                        ColorStateList.valueOf(palette.controlBackground),
                    )
                }
            } else {
                // Legacy RemoteViews lacks ColorStateList actions; preserve the
                // actual palette even when the launcher can't tint a rounded shape.
                setInt(R.id.widget_root, "setBackgroundColor", palette.background)
            }
            if (layoutId != R.layout.widget_playback_narrow) {
                setTextColor(R.id.widget_title, palette.primary)
                setTextColor(R.id.widget_artist, palette.secondary)
            }
            if (layoutId == R.layout.widget_playback) {
                setTextColor(R.id.widget_queue_heading, palette.accent)
                setTextColor(R.id.widget_queue_empty, palette.secondary)
            }
        }

        internal fun queueRow(context: Context, currentIndex: Int, item: WidgetQueueTrack, palette: WidgetPalette) =
            RemoteViews(context.packageName, R.layout.widget_playback_queue_row).apply {
                val current = item.index == currentIndex
                setTextViewText(R.id.widget_queue_position, if (current) "▶" else "${item.index + 1}")
                setTextViewText(R.id.widget_queue_title, widgetStyledText(item.title, palette))
                val label = if (current) "NOW PLAYING" else if (item.origin == "MANUAL") "QUEUE" else "NEXT"
                setTextViewText(
                    R.id.widget_queue_artist,
                    widgetStyledText(
                        if (current) label else "$label · ${item.artist ?: "Unknown artist"}",
                        palette,
                    ),
                )
                setTextColor(R.id.widget_queue_title, palette.primary)
                setTextColor(R.id.widget_queue_position, palette.accent)
                setTextColor(R.id.widget_queue_artist, palette.secondary)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setColorStateList(R.id.widget_queue_row, "setBackgroundTintList", ColorStateList.valueOf(palette.background))
                }
                setOnClickFillInIntent(
                    R.id.widget_queue_row,
                    Intent().apply {
                        putExtra(PlaybackWidgetActionReceiver.EXTRA_QUEUE_INDEX, item.index)
                        putExtra(PlaybackWidgetActionReceiver.EXTRA_MEDIA_ID, item.mediaId)
                        item.occurrenceId?.let {
                            putExtra(PlaybackWidgetActionReceiver.EXTRA_OCCURRENCE_ID, it)
                        }
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

