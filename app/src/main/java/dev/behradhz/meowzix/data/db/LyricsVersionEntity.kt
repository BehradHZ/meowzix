package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyrics_versions",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("trackId"),
        Index(value = ["trackId", "selected"]),
    ],
)
data class LyricsVersionEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val sourceType: String,
    val sourceLabel: String?,
    val rawText: String,
    val contentType: String,
    val selected: Boolean,
    val userSelected: Boolean,
    val userDelayMs: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
