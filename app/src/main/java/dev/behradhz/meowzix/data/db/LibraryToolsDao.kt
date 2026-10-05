package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryToolsDao {
    @Query("SELECT * FROM track_metadata_overrides WHERE trackId = :trackId LIMIT 1")
    suspend fun metadataOverride(trackId: String): TrackMetadataOverrideEntity?
    @Query("SELECT * FROM track_metadata_overrides ORDER BY trackId")
    suspend fun allMetadataOverrides(): List<TrackMetadataOverrideEntity>
    @Query("SELECT * FROM track_metadata_overrides ORDER BY trackId")
    fun observeMetadataOverrides(): Flow<List<TrackMetadataOverrideEntity>>
    @Upsert suspend fun upsertMetadataOverride(value: TrackMetadataOverrideEntity)
    @Query("DELETE FROM track_metadata_overrides WHERE trackId = :trackId") suspend fun deleteMetadataOverride(trackId: String)

    @Query("SELECT * FROM track_merge_journal WHERE id = :id LIMIT 1") suspend fun mergeJournal(id: String): TrackMergeJournalEntity?
    @Query("SELECT * FROM track_merge_journal WHERE reversedAtEpochMs IS NULL ORDER BY createdAtEpochMs DESC") suspend fun activeMergeJournal(): List<TrackMergeJournalEntity>
    @Query("SELECT survivorTrackId FROM track_merge_journal WHERE mergedTrackId = :mergedTrackId AND reversedAtEpochMs IS NULL ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun activeSurvivorForMerged(mergedTrackId: String): String?
    @Query("SELECT * FROM track_merge_journal WHERE mergedTrackId = :trackId AND reversedAtEpochMs IS NULL ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun activeMergeForMerged(trackId: String): TrackMergeJournalEntity?
    @Upsert suspend fun upsertMergeJournal(value: TrackMergeJournalEntity)
    @Query("UPDATE track_merge_journal SET reversedAtEpochMs = :reversedAtEpochMs WHERE id = :id AND reversedAtEpochMs IS NULL")
    suspend fun markMergeReversed(id: String, reversedAtEpochMs: Long): Int

    @Query("SELECT * FROM rule_playlists WHERE playlistId = :playlistId LIMIT 1") suspend fun rulePlaylist(playlistId: String): RulePlaylistEntity?
    @Query("SELECT * FROM rule_playlists ORDER BY playlistId") suspend fun allRulePlaylists(): List<RulePlaylistEntity>
    @Query("SELECT * FROM rule_playlists ORDER BY playlistId") fun observeRulePlaylists(): Flow<List<RulePlaylistEntity>>
    @Query(
        "SELECT rp.playlistId AS playlistId, p.title AS title, rp.matchMode AS matchMode, " +
            "rp.rulesJson AS rulesJson, rp.sortMode AS sortMode, rp.updatedAtEpochMs AS updatedAtEpochMs " +
            "FROM rule_playlists rp INNER JOIN playlists p ON p.id = rp.playlistId " +
            "ORDER BY p.title COLLATE NOCASE, rp.playlistId",
    )
    fun observeRulePlaylistRecords(): Flow<List<RulePlaylistRecordRow>>
    @Upsert suspend fun upsertRulePlaylist(value: RulePlaylistEntity)
    @Query("DELETE FROM rule_playlists WHERE playlistId = :playlistId") suspend fun deleteRulePlaylist(playlistId: String)

    @Query("SELECT * FROM backup_unresolved_references WHERE backupId = :backupId AND resolvedTrackId IS NULL ORDER BY createdAtEpochMs")
    suspend fun unresolvedBackupReferences(backupId: String): List<BackupUnresolvedReferenceEntity>
    @Upsert suspend fun upsertUnresolvedBackupReference(value: BackupUnresolvedReferenceEntity)
    @Query("UPDATE backup_unresolved_references SET resolvedTrackId = :trackId WHERE id = :id") suspend fun markBackupReferenceResolved(id: String, trackId: String)
}

data class RulePlaylistRecordRow(
    val playlistId: String,
    val title: String,
    val matchMode: String,
    val rulesJson: String,
    val sortMode: String,
    val updatedAtEpochMs: Long,
)