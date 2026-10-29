package dev.behradhz.meowzix.playback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction

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
        val snapshot = LiveSongCardSnapshot(content, actions)
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
            // The authoritative Media3 card already shows the song and artist on AOD.
            // Keep this auxiliary promoted card generic to avoid repeating both.
            .setContentTitle("Playing music")
            .setContentText("Tap to open player")
            .setContentIntent(openPlayerIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)

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
        const val CHANNEL_ID = "meowzix_live_song"
        const val NOTIFICATION_ID = 4098
    }
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
)

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
