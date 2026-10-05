package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_versions WHERE trackId = :trackId ORDER BY selected DESC, userSelected DESC, updatedAtEpochMs DESC")
    fun observeForTrack(trackId: String): Flow<List<LyricsVersionEntity>>
    @Query("SELECT * FROM lyrics_versions WHERE id = :id LIMIT 1") suspend fun byId(id: String): LyricsVersionEntity?
    @Query("SELECT * FROM lyrics_versions ORDER BY trackId, createdAtEpochMs, id") suspend fun allVersions(): List<LyricsVersionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(version: LyricsVersionEntity)
    @Upsert suspend fun upsert(version: LyricsVersionEntity)
    @Query("UPDATE lyrics_versions SET selected = 0 WHERE trackId = :trackId") suspend fun clearSelection(trackId: String)
    @Query("UPDATE lyrics_versions SET selected = 1, userSelected = :userSelected, updatedAtEpochMs = :updatedAt WHERE id = :id AND trackId = :trackId")
    suspend fun markSelected(id: String, trackId: String, userSelected: Boolean, updatedAt: Long): Int
    @Query("UPDATE lyrics_versions SET userDelayMs = :delayMs, updatedAtEpochMs = :updatedAt WHERE id = :id") suspend fun updateDelay(id: String, delayMs: Long, updatedAt: Long): Int
    @Query("DELETE FROM lyrics_versions WHERE id = :id AND trackId = :trackId") suspend fun delete(id: String, trackId: String): Int
    @Query("SELECT id FROM lyrics_versions WHERE trackId = :trackId ORDER BY userSelected DESC, updatedAtEpochMs DESC LIMIT 1") suspend fun fallbackVersionId(trackId: String): String?

    @Transaction
    suspend fun select(id: String, trackId: String, userSelected: Boolean, updatedAt: Long): Boolean {
        clearSelection(trackId)
        return markSelected(id, trackId, userSelected, updatedAt) == 1
    }
}
