package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Tracks whose automatically discovered lyrics must not be re-attached after removal. */
@Entity(
    tableName = "lyrics_detached_tracks",
    foreignKeys = [ForeignKey(
        entity = TrackEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class LyricsDetachedTrackEntity(@PrimaryKey val trackId: String)
