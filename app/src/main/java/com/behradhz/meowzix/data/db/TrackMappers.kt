package com.behradhz.meowzix.data.db

import com.behradhz.meowzix.core.model.Track
import com.behradhz.meowzix.data.db.entity.TrackEntity
import java.time.Instant
import java.util.UUID

fun TrackEntity.toDomain(): Track = Track(
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
