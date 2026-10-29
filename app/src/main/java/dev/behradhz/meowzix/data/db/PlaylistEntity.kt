package dev.behradhz.meowzix.data.db

import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "0") val telegramManaged: Boolean = false,
    @ColumnInfo(defaultValue = "0") val titleOverridden: Boolean = false,
    @ColumnInfo(defaultValue = "0") val descriptionOverridden: Boolean = false,
    @ColumnInfo(defaultValue = "0") val artworkOverridden: Boolean = false,
)
