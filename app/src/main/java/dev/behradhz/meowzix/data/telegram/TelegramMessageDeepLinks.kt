package dev.behradhz.meowzix.data.telegram

/**
 * Fallback for messages that TDLib cannot expose through GetMessageLink.
 * TDLib message IDs encode the server ID in the upper bits; raw IDs are not URL IDs.
 */
internal fun telegramMessageDeepLink(chatId: Long, tdMessageId: Long): String? {
    if (chatId == 0L || tdMessageId <= 0L) return null
    val messageId = tdMessageId ushr 20
    if (messageId == 0L) return null
    return when {
        // Includes Saved Messages. Handled by the official Telegram Android client.
        chatId > 0L -> "tg://openmessage?user_id=$chatId&message_id=$messageId"
        // Private/public supergroups and channels.
        chatId < -1_000_000_000_000L ->
            "https://t.me/c/${-chatId - 1_000_000_000_000L}/$messageId"
        // Basic groups cannot use t.me/c; Android supports an internal openmessage route.
        else -> "tg://openmessage?chat_id=${-chatId}&message_id=$messageId"
    }
}
