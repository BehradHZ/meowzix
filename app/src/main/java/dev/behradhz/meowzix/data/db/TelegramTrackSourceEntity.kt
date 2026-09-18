package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telegram_track_sources",
    foreignKeys = [ForeignKey(
        entity = TrackSourceEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackSourceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("trackSourceId"),
        Index("chatId"),
        Index(value = ["accountId", "chatId", "messageId"], unique = true),
    ],
)
data class TelegramTrackSourceEntity(
    @PrimaryKey val trackSourceId: String,
    val accountId: String,
    val chatId: Long,
    val messageId: Long,
    val tdFileId: Int?,
    val tdPersistentFileId: String?,
    val fileName: String?,
    val telegramTitle: String?,
    val telegramPerformer: String?,
    val remoteRevisionKey: String?,
)
