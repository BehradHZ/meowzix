package dev.behradhz.meowzix.core.model

import java.time.Instant
import java.util.UUID

data class Track(
    val id: UUID,
    val title: String,
    val normalizedTitle: String,
    val artist: String?,
    val normalizedArtist: String?,
    val album: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val artworkRef: String?,
    val favorite: Boolean,
    val hidden: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
