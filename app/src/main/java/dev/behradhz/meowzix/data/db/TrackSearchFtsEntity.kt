package dev.behradhz.meowzix.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * Room-owned declaration of the auxiliary library search index.
 *
 * TrackEntity remains the source of truth. Production triggers populate and synchronize this
 * standalone FTS table so search can use prefix matching without making FTS part of Track writes.
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    notIndexed = ["trackId"],
    prefix = [2, 3],
)
@Entity(tableName = "track_search_fts")
data class TrackSearchFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val trackId: String,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val album: String,
)
