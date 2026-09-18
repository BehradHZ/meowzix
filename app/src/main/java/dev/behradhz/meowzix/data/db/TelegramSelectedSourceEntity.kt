package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "telegram_selected_sources",
    primaryKeys = ["accountId", "chatId"],
    indices = [Index("accountId")],
)
data class TelegramSelectedSourceEntity(
    val accountId: String,
    val chatId: Long,
    val title: String,
    val kind: String,
    val newestMessageId: Long?,
    val initialScanComplete: Boolean,
    val lastSyncedAtEpochMs: Long?,
)
