package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val artworkRef: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
