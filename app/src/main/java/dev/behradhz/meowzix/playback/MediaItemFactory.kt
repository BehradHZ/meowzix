package dev.behradhz.meowzix.playback

import android.net.Uri
import android.os.Bundle
import java.util.UUID
import dev.behradhz.meowzix.domain.playback.ProgressiveQueueSnapshot
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackItem

private const val PLAYBACK_MODE_KEY = "meowzix.playback_mode"
private const val REPEAT_MODE_KEY = "meowzix.repeat_mode"
private const val QUEUE_OCCURRENCE_KEY = "meowzix.queue_occurrence_id"

fun PlayableTrack.toMediaItem(
    playbackMode: PlaybackMode = PlaybackMode.ORDERED,
    repeatMode: RepeatMode = RepeatMode.OFF,
    occurrenceId: UUID? = null,
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
            .setExtras(queuePolicyBundle(playbackMode, repeatMode, occurrenceId))
            .build(),
    )
    .build()

/**
 * Keep occurrence identity distinct from the canonical track media ID. Two
 * queued copies of the same song have the same mediaId but different queue
 * occurrences; comparing only mediaId corrupts Media3 window reconciliation.
 */
internal fun ProgressiveQueueSnapshot.mediaItems(
    startIndex: Int = materializedStartIndex,
    endExclusive: Int = materializedEndExclusive,
): List<MediaItem> = (startIndex until endExclusive).map { index ->
    tracks[index].toMediaItem(playbackMode, repeatMode, occurrenceIds[index])
}

internal fun MediaItem.queueOccurrenceId(): UUID? =
    mediaMetadata.extras?.getString(QUEUE_OCCURRENCE_KEY)
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

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
                .setExtras(Bundle(mediaMetadata.extras ?: Bundle()).apply {
                    putString(PLAYBACK_MODE_KEY, playbackMode.name)
                    putString(REPEAT_MODE_KEY, repeatMode.name)
                })
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

private fun queuePolicyBundle(
    playbackMode: PlaybackMode,
    repeatMode: RepeatMode,
    occurrenceId: UUID? = null,
): Bundle = Bundle().apply {
    putString(PLAYBACK_MODE_KEY, playbackMode.name)
    putString(REPEAT_MODE_KEY, repeatMode.name)
    if (occurrenceId != null) putString(QUEUE_OCCURRENCE_KEY, occurrenceId.toString())
}
