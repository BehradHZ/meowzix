package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TelegramDao {
    @Query("SELECT * FROM telegram_selected_sources WHERE accountId = :accountId ORDER BY title COLLATE NOCASE")
    suspend fun selectedSources(accountId: String): List<TelegramSelectedSourceEntity>

    @Query("SELECT * FROM telegram_selected_sources WHERE accountId = :accountId AND chatId = :chatId LIMIT 1")
    suspend fun selectedSource(accountId: String, chatId: Long): TelegramSelectedSourceEntity?

    @Upsert
    suspend fun upsertSelectedSource(source: TelegramSelectedSourceEntity)

    @Query("DELETE FROM telegram_selected_sources WHERE accountId = :accountId AND chatId = :chatId")
    suspend fun deleteSelectedSource(accountId: String, chatId: Long)

    @Query("UPDATE telegram_selected_sources SET newestMessageId = :newestMessageId, initialScanComplete = :initialScanComplete, lastSyncedAtEpochMs = :syncedAt WHERE accountId = :accountId AND chatId = :chatId")
    suspend fun updateSyncCheckpoint(
        accountId: String,
        chatId: Long,
        newestMessageId: Long?,
        initialScanComplete: Boolean,
        syncedAt: Long,
    )

    @Upsert
    suspend fun upsertTelegramTrackSource(source: TelegramTrackSourceEntity)

    @Query("SELECT * FROM telegram_track_sources WHERE accountId = :accountId AND chatId = :chatId AND messageId = :messageId LIMIT 1")
    suspend fun telegramSourceForMessage(
        accountId: String,
        chatId: Long,
        messageId: Long,
    ): TelegramTrackSourceEntity?

    @Query("SELECT * FROM telegram_track_sources WHERE accountId = :accountId AND chatId = :chatId")
    suspend fun telegramSourcesForChat(accountId: String, chatId: Long): List<TelegramTrackSourceEntity>

    @Query("SELECT trackSourceId FROM telegram_track_sources WHERE accountId = :accountId AND chatId = :chatId AND messageId IN (:messageIds)")
    suspend fun trackSourceIdsForMessages(
        accountId: String,
        chatId: Long,
        messageIds: List<Long>,
    ): List<String>
}
