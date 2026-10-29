package dev.behradhz.meowzix.playback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.C
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Optional, secondary now-playing status card. The Media3 MediaStyle notification remains
 * authoritative for playback controls. This standard notification may be promoted by Android
 * 16 QPR / the OEM, but SystemUI makes the final decision about lockscreen/AOD/chip display.
 */
internal class NowPlayingLiveCard(
    private val context: Context,
    private val openPlayerIntent: PendingIntent,
    private val scope: CoroutineScope,
    private val onArtworkChanged: () -> Unit,
) {
    private var artworkKey: String? = null
    private var artworkJob: Job? = null
    private var artworkBitmap: Bitmap? = null
    private val fallbackArtwork by lazy { buildLiveSongFallbackArtwork(context) }
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastState: LiveSongCardSnapshot? = null

    fun update(
        player: Player,
        enabled: Boolean,
        style: LiveSongCardStyle,
        left: LiveSongSideAction,
        right: LiveSongSideAction,
        isFavorite: Boolean,
    ) {
        val metadata = player.currentMediaItem?.mediaMetadata
        val content = if (enabled) liveSongContent(
            playWhenReady = player.playWhenReady,
            isEnded = player.playbackState == Player.STATE_ENDED ||
                player.playbackState == Player.STATE_IDLE,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
        ) else null

        if (content == null || !hasNotificationPermission()) {
            cancel()
            return
        }

        // Media3 supplies a local artwork URI for both restored and newly queued tracks.
        // Decode once per artwork identity off the playback thread; never decode on progress ticks.
        val artworkUri = metadata?.artworkUri?.toString()
        val artworkData = metadata?.artworkData
        val nextArtworkKey = liveSongArtworkKey(
            player.currentMediaItem?.mediaId.orEmpty(),
            artworkUri,
            artworkData?.size,
        )
        if (artworkKey != nextArtworkKey) {
            artworkKey = nextArtworkKey
            artworkJob?.cancel()
            artworkBitmap = fallbackArtwork // Never keep the previous song's cover during loading.
            if (artworkUri != null || artworkData != null) {
                artworkJob = scope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        decodeLiveSongSquareArtwork(context, artworkUri, artworkData)
                    }
                    if (artworkKey == nextArtworkKey) {
                        artworkBitmap = loaded ?: fallbackArtwork
                        // Rebuild with the latest playback/controls state, not a stale snapshot.
                        lastState = null
                        onArtworkChanged()
                    }
                }
            }
        }
        val actions = liveSongControls(
            style, left, right,
            canPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
                player.hasPreviousMediaItem(),
            canNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
                player.hasNextMediaItem(),
        )
        val progress = liveSongProgress(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                ?: metadata?.durationMs?.takeIf { it > 0L },
        )
        val snapshot = LiveSongCardSnapshot(content, actions, isFavorite, progress)
        if (lastState == snapshot) return

        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live song card", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Optional now-playing song and artist card; separate from media controls"
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            },
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(content.title)
            .setContentText(content.artist)
            // The system template positions this square thumbnail near the title/artist.
            // Avoid custom RemoteViews: those make the card ineligible for Live Updates.
            .setLargeIcon(artworkBitmap ?: fallbackArtwork)
            // Keep the full artist separate from the clock/favorite metadata to avoid truncation.
            // The subtext is meaningful (unlike the old duplicated "Meowzix" header).
            .setSubText("${if (isFavorite) "♥ Liked" else "♡ Not liked"} · ${progress.label}")
            .setContentIntent(openPlayerIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)

        // Android 16+ renders a real promoted ProgressStyle bar. Earlier Android
        // versions use the standard notification progress indicator instead.
        // Neither is draggable; scrubbing remains in Android's MediaSession player.
        if (progress.durationMs != null && Build.VERSION.SDK_INT >= 36) {
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .addProgressSegment(NotificationCompat.ProgressStyle.Segment(LIVE_PROGRESS_MAX))
                    .setProgress(progress.progressValue)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(
                "${content.artist}\n${if (isFavorite) "♥ Liked" else "♡ Not liked"} · ${progress.label}"
            ))
            if (progress.durationMs != null) {
                builder.setProgress(LIVE_PROGRESS_MAX, progress.progressValue, false)
            }
        }

        actions.forEach { action ->
            builder.addAction(
                liveSongActionIcon(action),
                liveSongActionLabel(action),
                PendingIntent.getBroadcast(
                    context,
                    4980 + action.ordinal,
                    Intent(context, LiveSongControlsReceiver::class.java)
                        .setAction(LiveSongControlsReceiver.actionName(context, action)),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }

        // MediaStyle cannot be promoted. This optional Standard/BigText notification requests
        // promotion instead; system eligibility and device UI remain outside our control.
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(liveSongChipLabel(content.title))
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
            lastState = snapshot
        } catch (_: SecurityException) {
            cancel()
        }
    }

    fun cancel() {
        artworkJob?.cancel()
        artworkJob = null
        artworkKey = null
        artworkBitmap = null
        if (lastState != null) {
            manager.cancel(NOTIFICATION_ID)
            lastState = null
        }
    }

    private fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private companion object {
        const val LIVE_PROGRESS_MAX = 1_000
        const val CHANNEL_ID = "meowzix_live_song"
        const val NOTIFICATION_ID = 4098
    }
}

/** A new track must not accidentally reuse the previous track's cached cover. */
internal fun liveSongArtworkKey(mediaId: String, uri: String?, embeddedBytes: Int?): String =
    "$mediaId|${uri.orEmpty()}|${embeddedBytes ?: 0}"

/**
 * Notification artwork: a bounded 128px center-cropped square. File/content/resource URIs only.
 * Neither raw audio nor artwork is fetched over the network, and decoding never blocks playback.
 */
private fun decodeLiveSongSquareArtwork(context: Context, ref: String?, data: ByteArray?): Bitmap? {
    val decoded = runCatching {
        val uri = ref?.let(Uri::parse)?.takeIf { it.scheme in setOf("content", "file", "android.resource") }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } else if (data != null) {
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 256 || bounds.outHeight / sampleSize > 256) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            data?.let { BitmapFactory.decodeByteArray(it, 0, it.size, options) }
        }
    }.getOrNull() ?: return null
    return centerCropLiveSongArtwork(decoded)
}

private fun centerCropLiveSongArtwork(source: Bitmap): Bitmap {
    val size = 128
    val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val scale = size.toFloat() / min(source.width, source.height).coerceAtLeast(1)
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate((size - source.width * scale) / 2f, (size - source.height * scale) / 2f)
    }
    val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        setLocalMatrix(matrix)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
    Canvas(result).drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 12f, 12f, paint)
    return result
}

/** Preserve a visible square when the current song has no readable cover. */
private fun buildLiveSongFallbackArtwork(context: Context): Bitmap {
    val size = 128
    val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(Color.rgb(38, 39, 48))
    ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.apply {
        setBounds(12, 12, size - 12, size - 12)
        draw(canvas)
    }
    return result
}

internal data class LiveSongContent(val title: String, val artist: String)

/** Android reserves at most 96dp for a live-update chip; keep a short, readable title. */
internal fun liveSongChipLabel(title: String): String {
    val clean = title.trim()
    if (clean.length <= 7) return clean
    return clean.take(6).trimEnd() + "…"
}

/** Avoid stale notifications after pause, stop, or disabling the preference. */
internal fun liveSongContent(
    playWhenReady: Boolean,
    isEnded: Boolean,
    title: String?,
    artist: String?,
): LiveSongContent? {
    if (!playWhenReady || isEnded || title.isNullOrBlank()) return null
    return LiveSongContent(title.trim(), artist?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown artist")
}

internal data class LiveSongCardSnapshot(
    val content: LiveSongContent,
    val actions: List<LiveSongAction>,
    val isFavorite: Boolean,
    val progress: LiveSongProgress,
)

/** Position is rounded to a second only for a lightweight, stable notification snapshot. */
internal data class LiveSongProgress(val positionMs: Long, val durationMs: Long?) {
    val label: String get() = "${formatLiveSongTime(positionMs)} / ${durationMs?.let(::formatLiveSongTime) ?: "—:—"}"
    val progressValue: Int get() {
        val duration = durationMs ?: return 0
        return (positionMs.toDouble() / duration * 1_000).toInt().coerceIn(0, 1_000)
    }
}

internal fun liveSongProgress(positionMs: Long, durationMs: Long?): LiveSongProgress {
    val duration = durationMs?.takeIf { it > 0L }
    val clamped = positionMs.coerceAtLeast(0L).let { if (duration == null) it else it.coerceAtMost(duration) }
    // Notification refreshes at a low frequency; updates between seconds have no useful visual effect.
    return LiveSongProgress((clamped / 1_000L) * 1_000L, duration)
}

internal fun formatLiveSongTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val rest = seconds % 60L
    return if (hours > 0) "${hours}:${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
    else "${minutes}:${rest.toString().padStart(2, '0')}"
}

internal enum class LiveSongAction { PREVIOUS, PAUSE, NEXT, FAVORITE, SHUFFLE, REPEAT }

/** The Android standard notification shows at most three expanded action buttons. */
internal fun liveSongControls(
    style: LiveSongCardStyle,
    left: LiveSongSideAction,
    right: LiveSongSideAction,
    canPrevious: Boolean,
    canNext: Boolean,
): List<LiveSongAction> {
    if (style == LiveSongCardStyle.MINIMAL) return emptyList()
    return listOf(LiveSongAction.valueOf(left.name), LiveSongAction.PAUSE, LiveSongAction.valueOf(right.name))
        .distinct()
        .filter {
            when (it) {
                LiveSongAction.PREVIOUS -> canPrevious
                LiveSongAction.NEXT -> canNext
                else -> true
            }
        }
}

private fun liveSongActionIcon(action: LiveSongAction): Int = when (action) {
    LiveSongAction.PREVIOUS -> R.drawable.widget_previous
    LiveSongAction.PAUSE -> R.drawable.widget_pause
    LiveSongAction.NEXT -> R.drawable.widget_next
    LiveSongAction.FAVORITE -> R.drawable.live_song_favorite
    LiveSongAction.SHUFFLE -> R.drawable.live_song_shuffle
    LiveSongAction.REPEAT -> R.drawable.live_song_repeat
}

private fun liveSongActionLabel(action: LiveSongAction): String =
    action.name.lowercase().replaceFirstChar(Char::uppercase)
