package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["hidden", "normalizedTitle"]),
        Index("normalizedArtist"),
        Index("favorite"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
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
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
