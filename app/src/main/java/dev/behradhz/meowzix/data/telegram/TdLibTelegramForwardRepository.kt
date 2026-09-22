package dev.behradhz.meowzix.data.telegram

import android.net.Uri
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardOptions
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.drinkless.tdlib.TdApi

/**
 * Sends the original Telegram message behind a canonical Meowzix track.
 *
 * This intentionally does not upload a local file as a fallback. A forward action must preserve
 * the relationship with the Telegram message that Meowzix originally indexed; ordinary file
 * sharing remains a separate product action.
 */
@Singleton
class TdLibTelegramForwardRepository @Inject constructor(
    private val telegramDao: TelegramDao,
    private val telegramRepository: TelegramRepository,
) : TelegramForwardRepository {

    override suspend fun searchChats(query: String, limit: Int): List<TelegramChatSummary> {
        val activeClient = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Telegram account information is still loading.")
        val currentUserId = accountId.toLongOrNull()
            ?: error("Telegram account information is invalid.")
        val safeLimit = limit.coerceIn(1, 100)
        val trimmed = query.trim()
        val ids = LinkedHashSet<Long>()

        if (trimmed.isEmpty()) {
            // The main chat list is already maintained by TDLib locally, which keeps opening the
            // picker fast and avoids a network round trip for the common case.
            ids += activeClient.send(TdApi.GetChats(null, safeLimit)).chatIds.toList()
            runCatching { activeClient.send(TdApi.CreatePrivateChat(currentUserId, false)) }
                .getOrNull()
                ?.let { ids.add(it.id) }
        } else {
            // SearchChats is an offline lookup over chats already known to TDLib.
            ids += activeClient.send(TdApi.SearchChats(trimmed, null, safeLimit)).chatIds.toList()

            // Supplement local results from the server only when the local index did not fill the
            // requested window. This keeps typing responsive while still finding older chats.
            if (ids.size < safeLimit) {
                runCatching {
                    activeClient.send(TdApi.SearchChatsOnServer(trimmed, null, safeLimit - ids.size))
                }.getOrNull()?.chatIds?.let { ids += it.toList() }
            }
        }

        val selectedIds = telegramRepository.musicSourceState.value.selectedChatIds
        val result = ArrayList<TelegramChatSummary>(minOf(ids.size, safeLimit))
        for (chatId in ids.take(safeLimit)) {
            val chat = runCatching { activeClient.send(TdApi.GetChat(chatId)) }.getOrNull() ?: continue
            result += chat.toForwardSummary(currentUserId, chatId in selectedIds)
        }
        return result
    }

    override suspend fun forwardTrack(
        trackId: UUID,
        targetChatId: Long,
        options: TelegramForwardOptions,
    ) {
        val activeClient = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Telegram account information is still loading.")
        val source = telegramDao.telegramSourceForTrack(accountId, trackId.toString())
            ?: error("This track doesn't have an active Telegram source to forward.")

        // Refresh message accessibility before forwarding. The stored chat/message pair is the
        // durable identity; file references themselves can expire.
        activeClient.send(TdApi.GetMessage(source.chatId, source.messageId))
        val properties = activeClient.send(TdApi.GetMessageProperties(source.chatId, source.messageId))
        val target = activeClient.send(TdApi.GetChat(targetChatId))
        val targetIsSecret = target.type is TdApi.ChatTypeSecret

        if (options.includeSourceAttribution) {
            if (targetIsSecret) {
                error("Telegram can't keep original source attribution when sending to a secret chat. Send a copy instead.")
            }
            if (!properties.canBeForwarded) {
                error("The original Telegram message doesn't allow forwarding with source attribution.")
            }
        } else {
            val canCopy = if (targetIsSecret) properties.canBeCopiedToSecretChat else properties.canBeCopied
            if (!canCopy) {
                error("The original Telegram message doesn't allow its content to be copied to this chat.")
            }
        }

        val sendCopy = !options.includeSourceAttribution
        val removeCaption = sendCopy && !options.keepCaption
        activeClient.send(
            TdApi.ForwardMessages(
                targetChatId,
                null,
                source.chatId,
                longArrayOf(source.messageId),
                null,
                sendCopy,
                removeCaption,
            ),
        )
    }
}

private fun TdApi.Chat.toForwardSummary(
    currentUserId: Long,
    selected: Boolean,
): TelegramChatSummary = TelegramChatSummary(
    chatId = id,
    title = title.ifBlank { "Telegram chat" },
    kind = when (val chatType = type) {
        is TdApi.ChatTypePrivate -> if (chatType.userId == currentUserId) {
            TelegramChatKind.SAVED_MESSAGES
        } else {
            TelegramChatKind.PRIVATE
        }
        is TdApi.ChatTypeBasicGroup -> TelegramChatKind.BASIC_GROUP
        is TdApi.ChatTypeSupergroup -> TelegramChatKind.SUPERGROUP_OR_CHANNEL
        is TdApi.ChatTypeSecret -> TelegramChatKind.SECRET
        else -> TelegramChatKind.PRIVATE
    },
    selected = selected,
    profilePhotoRef = localPhotoRef(),
)

private fun TdApi.Chat.localPhotoRef(): String? {
    val photo = photo?.small ?: return null
    if (!photo.local.isDownloadingCompleted || photo.local.path.isBlank()) return null
    return File(photo.local.path).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }
}
