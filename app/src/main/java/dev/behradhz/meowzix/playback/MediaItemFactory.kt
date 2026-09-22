package dev.behradhz.meowzix.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackItem
import java.util.UUID

private const val PLAYBACK_MODE_KEY = "meowzix.playback_mode"
private const val REPEAT_MODE_KEY = "meowzix.repeat_mode"

fun PlayableTrack.toMediaItem(
    playbackMode: PlaybackMode = PlaybackMode.ORDERED,
    repeatMode: RepeatMode = RepeatMode.OFF,
): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(contentUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkRef?.let(Uri::parse))
            .setDurationMs(durationMs)
            .setIsPlayable(true)
            .setExtras(queuePolicyBundle(playbackMode, repeatMode))
            .build(),
    )
    .build()

fun PlayableTrack.toPersistedPlaybackItem(): PersistedPlaybackItem = PersistedPlaybackItem(
    mediaId = id.toString(),
    uri = contentUri,
    title = title,
    artist = artist,
    artworkUri = artworkRef,
    durationMs = durationMs.coerceAtLeast(0),
)

fun PersistedPlaybackItem.toPlayableTrack(): PlayableTrack? {
    val trackId = runCatching { UUID.fromString(mediaId) }.getOrNull() ?: return null
    return PlayableTrack(
        id = trackId,
        title = title.ifBlank { "Unknown Track" },
        artist = artist,
        album = null,
        durationMs = durationMs.coerceAtLeast(0),
        artworkRef = artworkUri,
        contentUri = uri,
    )
}

fun PersistedPlaybackItem.toMediaItem(
    playbackMode: PlaybackMode = PlaybackMode.ORDERED,
    repeatMode: RepeatMode = RepeatMode.OFF,
): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUri?.let(Uri::parse))
            .setDurationMs(durationMs)
            .setIsPlayable(true)
            .setExtras(queuePolicyBundle(playbackMode, repeatMode))
            .build(),
    )
    .build()

fun MediaItem.toPersistedPlaybackItem(): PersistedPlaybackItem? {
    val uri = localConfiguration?.uri?.toString() ?: return null
    return PersistedPlaybackItem(
        mediaId = mediaId,
        uri = uri,
        title = mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown Track" },
        artist = mediaMetadata.artist?.toString(),
        artworkUri = mediaMetadata.artworkUri?.toString(),
        durationMs = mediaMetadata.durationMs?.coerceAtLeast(0) ?: 0,
    )
}

fun MediaItem.withQueuePolicy(playbackMode: PlaybackMode, repeatMode: RepeatMode): MediaItem =
    buildUpon()
        .setMediaMetadata(
            mediaMetadata.buildUpon()
                .setExtras(queuePolicyBundle(playbackMode, repeatMode))
                .build(),
        )
        .build()

fun MediaItem.playbackMode(): PlaybackMode = mediaMetadata.extras
    ?.getString(PLAYBACK_MODE_KEY)
    ?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
    ?: PlaybackMode.ORDERED

fun MediaItem.repeatMode(): RepeatMode = mediaMetadata.extras
    ?.getString(REPEAT_MODE_KEY)
    ?.let { runCatching { RepeatMode.valueOf(it) }.getOrNull() }
    ?: RepeatMode.OFF

private fun queuePolicyBundle(playbackMode: PlaybackMode, repeatMode: RepeatMode): Bundle =
    Bundle().apply {
        putString(PLAYBACK_MODE_KEY, playbackMode.name)
        putString(REPEAT_MODE_KEY, repeatMode.name)
    }
