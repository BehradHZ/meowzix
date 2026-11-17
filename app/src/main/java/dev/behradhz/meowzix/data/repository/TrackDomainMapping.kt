package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.db.TrackEntity
import java.time.Instant
import java.util.UUID

/** Canonical mapping used by library, playlists, metadata tools, and search. */
internal fun TrackEntity.toDomain(): Track = Track(
    id = UUID.fromString(id),
    title = title,
    normalizedTitle = normalizedTitle,
    artist = artist,
    normalizedArtist = normalizedArtist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    artworkRef = artworkRef,
    favorite = favorite,
    hidden = hidden,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)
