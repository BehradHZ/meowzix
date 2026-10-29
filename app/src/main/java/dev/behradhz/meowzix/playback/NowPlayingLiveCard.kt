package dev.behradhz.meowzix.playback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.os.Build
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.C
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction
import java.text.BreakIterator
import java.util.Locale

/**
 * Optional, secondary now-playing status card. The Media3 MediaStyle notification remains
 * authoritative for playback controls. This standard notification may be promoted by Android
 * 16 QPR / the OEM, but SystemUI makes the final decision about lockscreen/AOD/chip display.
 */
internal class NowPlayingLiveCard(
    private val context: Context,
    private val openPlayerIntent: PendingIntent,
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var lastState: LiveSongCardSnapshot? = null

    fun update(
        player: Player,
        enabled: Boolean,
        style: LiveSongCardStyle,
        left: LiveSongSideAction,
        right: LiveSongSideAction,
        isFavorite: Boolean,
        forceRefresh: Boolean = false,
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
        val snapshot = LiveSongCardSnapshot(player.currentMediaItem?.mediaId.orEmpty(), content, actions, isFavorite, progress)
        if (!shouldPublishLiveSongCard(lastState, snapshot, forceRefresh)) return

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
            // Keep the Meowzix symbol instead of the system play triangle.
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(ContextCompat.getColor(context, R.color.live_song_accent))
            .setContentTitle(content.title)
            // Standard ProgressStyle controls the bar position; content text is the nearest
            // system-managed row above it. Subtext is a *header* field, never put time there.
            .setContentText(liveSongDetailLine(content.artist, progress))
            .setSubText(if (isFavorite) "♥ Liked" else "♡ Not liked")
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
                "${content.artist}\n${progress.label}"
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

        // Status chip prioritizes the track title. Android owns the actual AOD rendering;
        // this notification cannot force SystemUI to redraw at a specific instant.
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(chipTitle(content.title))
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
            lastState = snapshot
        } catch (_: SecurityException) {
            cancel()
        }
    }

    fun cancel() {
        if (lastState != null) {
            manager.cancel(NOTIFICATION_ID)
            lastState = null
        }
    }

    private fun chipTitle(title: String): String {
        // SystemUI owns the exact layout: 96dp total includes its icon and padding.
        // Measure actual glyph widths instead of reserving a fixed number of letters.
        val metrics = context.resources.displayMetrics
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, CHIP_TEXT_SIZE_SP, metrics)
        }
        return liveSongChipLabel(title, CHIP_TEXT_WIDTH_DP * metrics.density) { paint.measureText(it) }
    }

    private fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    private companion object {
        // Approximate available text width after the status chip's icon and padding.
        // SystemUI/OEMs can still shorten or hide text independently.
        const val CHIP_TEXT_WIDTH_DP = 60f
        const val CHIP_TEXT_SIZE_SP = 14f
        const val LIVE_PROGRESS_MAX = 1_000
        const val CHANNEL_ID = "meowzix_live_song"
        const val NOTIFICATION_ID = 4098
    }
}

/** Places elapsed / total time beside the artist in the standard content row, above the bar. */
internal fun liveSongDetailLine(artist: String, progress: LiveSongProgress): String =
    "$artist · ${progress.label}"

internal data class LiveSongContent(val title: String, val artist: String)

/**
 * Fit the longest prefix plus a single ellipsis in the estimated chip text area.
 * BreakIterator avoids splitting surrogate pairs and combining-character sequences.
 * The system still decides what is actually visible on a given Android/OEM build.
 */
internal fun liveSongChipLabel(
    title: String,
    maxTextWidth: Float,
    measureText: (String) -> Float,
): String {
    val clean = title.trim()
    if (clean.isEmpty() || maxTextWidth <= 0f) return ""
    if (measureText(clean) <= maxTextWidth) return clean

    val ellipsis = "…"
    if (measureText(ellipsis) > maxTextWidth) return ""
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(clean) }
    var lastFittingEnd = 0
    var end = boundaries.next()
    while (end != BreakIterator.DONE) {
        val prefix = clean.substring(0, end).trimEnd()
        if (measureText(prefix + ellipsis) > maxTextWidth) break
        lastFittingEnd = prefix.length
        end = boundaries.next()
    }
    return clean.substring(0, lastFittingEnd) + ellipsis
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

/** Allow one deliberate re-post after a song transition, without disabling normal deduplication. */
internal fun shouldPublishLiveSongCard(
    previous: LiveSongCardSnapshot?,
    next: LiveSongCardSnapshot,
    forceRefresh: Boolean,
): Boolean = forceRefresh || previous != next

internal data class LiveSongCardSnapshot(
    // A different track can have identical title, artist, duration and position.
    val mediaId: String,
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
    // Keep sub-second position changes out of notification updates; the service refreshes once per second.
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
