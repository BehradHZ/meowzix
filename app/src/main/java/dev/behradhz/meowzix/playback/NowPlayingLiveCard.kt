package dev.behradhz.meowzix.playback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import dev.behradhz.meowzix.R

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
    private var lastContent: LiveSongContent? = null

    fun update(player: Player, enabled: Boolean) {
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
        if (lastContent == content) return

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
            .setStyle(NotificationCompat.BigTextStyle().bigText("${content.title}  ·  ${content.artist}"))
            .setSubText("Meowzix · Now playing")
            .setContentIntent(openPlayerIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)

        // MediaStyle cannot be promoted. This optional Standard/BigText notification requests
        // promotion instead; system eligibility and device UI remain outside our control.
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText("♫")
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
            lastContent = content
        } catch (_: SecurityException) {
            cancel()
        }
    }

    fun cancel() {
        if (lastContent != null) {
            manager.cancel(NOTIFICATION_ID)
            lastContent = null
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
