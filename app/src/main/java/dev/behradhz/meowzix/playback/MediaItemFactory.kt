package dev.behradhz.meowzix.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackItem

fun PlayableTrack.toMediaItem(): MediaItem = MediaItem.Builder()
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
            .build(),
    )
    .build()

fun PersistedPlaybackItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUri?.let(Uri::parse))
            .setDurationMs(durationMs)
            .setIsPlayable(true)
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
