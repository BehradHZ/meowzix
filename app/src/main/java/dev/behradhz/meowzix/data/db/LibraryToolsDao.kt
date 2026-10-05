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
    @Upsert suspend fun upsertMergeJournal(value: TrackMergeJournalEntity)
    @Query("UPDATE track_merge_journal SET reversedAtEpochMs = :reversedAtEpochMs WHERE id = :id") suspend fun markMergeReversed(id: String, reversedAtEpochMs: Long)

    @Query("SELECT * FROM rule_playlists WHERE playlistId = :playlistId LIMIT 1") suspend fun rulePlaylist(playlistId: String): RulePlaylistEntity?
    @Query("SELECT * FROM rule_playlists ORDER BY playlistId") suspend fun allRulePlaylists(): List<RulePlaylistEntity>
    @Query("SELECT * FROM rule_playlists ORDER BY playlistId") fun observeRulePlaylists(): Flow<List<RulePlaylistEntity>>
    @Upsert suspend fun upsertRulePlaylist(value: RulePlaylistEntity)
    @Query("DELETE FROM rule_playlists WHERE playlistId = :playlistId") suspend fun deleteRulePlaylist(playlistId: String)

    @Query("SELECT * FROM backup_unresolved_references WHERE backupId = :backupId AND resolvedTrackId IS NULL ORDER BY createdAtEpochMs")
    suspend fun unresolvedBackupReferences(backupId: String): List<BackupUnresolvedReferenceEntity>
    @Upsert suspend fun upsertUnresolvedBackupReference(value: BackupUnresolvedReferenceEntity)
    @Query("UPDATE backup_unresolved_references SET resolvedTrackId = :trackId WHERE id = :id") suspend fun markBackupReferenceResolved(id: String, trackId: String)
}
