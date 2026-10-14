package dev.behradhz.meowzix.domain.telegram

/**
 * Preserve Telegram's recent-chat ordering within each priority tier.
 * The original chat list comes from TDLib's GetChats, not alphabetic sorting.
 */
fun prioritizeTelegramSourceChats(
    chats: List<TelegramChatSummary>,
    previouslySelected: Set<Long>,
): List<TelegramChatSummary> = chats.sortedBy { chat ->
    when {
        chat.selected -> 0
        chat.chatId in previouslySelected -> 1
        else -> 2
    }
}
