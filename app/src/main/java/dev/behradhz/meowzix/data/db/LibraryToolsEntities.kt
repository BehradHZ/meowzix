package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_metadata_overrides",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackMetadataOverrideEntity(
    @PrimaryKey val trackId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkRef: String?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "track_merge_journal",
    indices = [Index("survivorTrackId"), Index("mergedTrackId")],
)
data class TrackMergeJournalEntity(
    @PrimaryKey val id: String,
    val survivorTrackId: String,
    val mergedTrackId: String,
    val snapshotJson: String,
    val createdAtEpochMs: Long,
    val reversedAtEpochMs: Long?,
)

@Entity(
    tableName = "rule_playlists",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RulePlaylistEntity(
    @PrimaryKey val playlistId: String,
    val matchMode: String,
    val rulesJson: String,
    val sortMode: String,
    val updatedAtEpochMs: Long,
)
