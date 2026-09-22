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

    @Query("SELECT tg.* FROM telegram_track_sources tg INNER JOIN track_sources s ON s.id = tg.trackSourceId INNER JOIN telegram_selected_sources selected ON selected.accountId = tg.accountId AND selected.chatId = tg.chatId WHERE tg.accountId = :accountId AND s.trackId = :trackId AND s.availability != 'MISSING' ORDER BY tg.messageId DESC LIMIT 1")
    suspend fun telegramSourceForTrack(accountId: String, trackId: String): TelegramTrackSourceEntity?

    @Query("SELECT tg.* FROM telegram_track_sources tg INNER JOIN track_sources s ON s.id = tg.trackSourceId INNER JOIN telegram_selected_sources selected ON selected.accountId = tg.accountId AND selected.chatId = tg.chatId WHERE s.trackId = :trackId AND s.availability != 'MISSING' ORDER BY tg.messageId DESC LIMIT 1")
    suspend fun telegramSourceForAnyAccountTrack(trackId: String): TelegramTrackSourceEntity?

    @Query("SELECT * FROM telegram_track_sources WHERE accountId = :accountId AND chatId = :chatId")
    suspend fun telegramSourcesForChat(accountId: String, chatId: Long): List<TelegramTrackSourceEntity>

    @Query("SELECT tg.* FROM telegram_track_sources tg INNER JOIN telegram_selected_sources selected ON selected.accountId = tg.accountId AND selected.chatId = tg.chatId")
    suspend fun allSelectedTelegramTrackSources(): List<TelegramTrackSourceEntity>

    @Query("SELECT * FROM telegram_track_sources")
    suspend fun allTelegramTrackSources(): List<TelegramTrackSourceEntity>

    @Query("SELECT trackSourceId FROM telegram_track_sources WHERE accountId = :accountId AND chatId = :chatId AND messageId IN (:messageIds)")
    suspend fun trackSourceIdsForMessages(
        accountId: String,
        chatId: Long,
        messageIds: List<Long>,
    ): List<String>
}
