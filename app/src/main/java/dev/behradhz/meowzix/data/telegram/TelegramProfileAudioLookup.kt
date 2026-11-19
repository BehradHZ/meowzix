package dev.behradhz.meowzix.data.telegram

import org.drinkless.tdlib.TdApi

/** Resolve profile-pinned audio without treating synthetic negative IDs as chat message IDs. */
internal suspend fun findTelegramProfileAudio(
    client: TdLibClientAdapter,
    chatId: Long,
    syntheticMessageId: Long,
    persistentFileId: String?,
): TdApi.Audio? {
    require(syntheticMessageId < 0L) { "Profile audio IDs must be negative" }
    val type = client.send(TdApi.GetChat(chatId)).type as? TdApi.ChatTypePrivate ?: return null
    var offset = 0
    while (offset < 10_000) {
        val page = client.send(TdApi.GetUserProfileAudios(type.userId, offset, 100))
        val match = page.audios.firstOrNull { audio ->
            val stableId = audio.audio.remote.uniqueId
            (!persistentFileId.isNullOrBlank() && stableId == persistentFileId) ||
                telegramProfileMessageId(audio.profileIdentity()) == syntheticMessageId
        }
        if (match != null) return match
        if (page.audios.isEmpty()) break
        offset += page.audios.size
        if (offset >= page.totalCount) break
    }
    return null
}
