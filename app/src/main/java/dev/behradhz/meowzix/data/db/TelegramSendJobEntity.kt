package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telegram_send_jobs",
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["state"]),
        Index(value = ["activeDedupeKey"], unique = true),
    ],
)
data class TelegramSendJobEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val trackId: String,
    val targetChatId: Long,
    val targetTitle: String?,
    val state: String,
    val progressPercent: Int,
    val attemptCount: Int,
    val includeSourceAttribution: Boolean,
    val keepCaption: Boolean,
    val activeDedupeKey: String?,
    val sentMessageId: Long?,
    val errorMessage: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
