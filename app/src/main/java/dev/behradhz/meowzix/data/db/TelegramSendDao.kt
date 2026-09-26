package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramSendDao {
    @Query("SELECT * FROM telegram_send_jobs ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<TelegramSendJobEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(job: TelegramSendJobEntity): Long

    @Query("SELECT * FROM telegram_send_jobs WHERE id = :id LIMIT 1")
    suspend fun jobById(id: String): TelegramSendJobEntity?

    @Query("SELECT * FROM telegram_send_jobs WHERE activeDedupeKey = :dedupeKey LIMIT 1")
    suspend fun activeByDedupeKey(dedupeKey: String): TelegramSendJobEntity?

    @Query("SELECT * FROM telegram_send_jobs WHERE accountId = :accountId AND targetChatId = :targetChatId AND trackId = :trackId AND state = 'SENT' ORDER BY updatedAtEpochMs DESC LIMIT 1")
    suspend fun sentForTrackDestination(
        accountId: String,
        targetChatId: Long,
        trackId: String,
    ): TelegramSendJobEntity?

    @Query(
        """
        SELECT * FROM telegram_send_jobs
        WHERE activeDedupeKey IS NOT NULL
          AND state IN ('QUEUED', 'RETRYING', 'WAITING_FOR_NETWORK', 'WAITING_FOR_TELEGRAM')
        ORDER BY createdAtEpochMs ASC
        LIMIT 1
        """,
    )
    suspend fun nextRunnable(): TelegramSendJobEntity?

    @Query(
        """
        UPDATE telegram_send_jobs
        SET state = :state,
            progressPercent = :progressPercent,
            attemptCount = :attemptCount,
            errorMessage = :errorMessage,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun updateState(
        id: String,
        state: String,
        progressPercent: Int,
        attemptCount: Int,
        errorMessage: String?,
        updatedAtEpochMs: Long,
    )

    @Query(
        """
        UPDATE telegram_send_jobs
        SET progressPercent = :progressPercent,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(id: String, progressPercent: Int, updatedAtEpochMs: Long)

    @Query(
        """
        UPDATE telegram_send_jobs
        SET state = 'SENT',
            progressPercent = 100,
            sentMessageId = :sentMessageId,
            errorMessage = NULL,
            activeDedupeKey = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun markSent(id: String, sentMessageId: Long, updatedAtEpochMs: Long)

    @Query(
        """
        UPDATE telegram_send_jobs
        SET state = 'FAILED',
            errorMessage = :errorMessage,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun markFailed(id: String, errorMessage: String, updatedAtEpochMs: Long)

    @Query(
        """
        UPDATE telegram_send_jobs
        SET state = 'VERIFYING',
            errorMessage = :errorMessage,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE state IN ('CHECKING', 'PREPARING_FILE', 'UPLOADING', 'SENDING')
        """,
    )
    suspend fun recoverInterrupted(errorMessage: String, updatedAtEpochMs: Long)

    @Query(
        """
        UPDATE telegram_send_jobs
        SET state = 'RETRYING',
            progressPercent = 0,
            attemptCount = 0,
            errorMessage = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
          AND activeDedupeKey IS NOT NULL
          AND state IN ('FAILED', 'VERIFYING', 'WAITING_FOR_NETWORK', 'WAITING_FOR_TELEGRAM')
        """,
    )
    suspend fun retry(id: String, updatedAtEpochMs: Long): Int

    @Query(
        """
        UPDATE telegram_send_jobs
        SET state = 'CANCELED',
            activeDedupeKey = NULL,
            errorMessage = NULL,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :id
          AND state IN ('QUEUED', 'RETRYING', 'WAITING_FOR_NETWORK', 'WAITING_FOR_TELEGRAM', 'FAILED', 'VERIFYING')
        """,
    )
    suspend fun cancel(id: String, updatedAtEpochMs: Long): Int
}
