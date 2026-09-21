from pathlib import Path

p = Path('app/src/main/java/dev/behradhz/meowzix/data/telegram/TdLibTelegramRepository.kt')
text = p.read_text()

old = '''                chatIds.mapNotNull { chatId ->
                    runCatching { activeClient.send(TdApi.GetChat(chatId)) }.getOrNull()
                }.map { chat ->
                    chat.toSummary(
                        currentUserId = userId,
                        selected = chat.id in selectedIds,
                        profilePhotoRef = resolveChatPhotoRef(activeClient, chat),
                    )
                }.sortedWith(compareByDescending<TelegramChatSummary> { it.kind == TelegramChatKind.SAVED_MESSAGES }.thenBy { it.title.lowercase(Locale.ROOT) })
            }.onSuccess { chats ->
                _musicSourceState.update { it.copy(chats = chats, isLoadingChats = false) }
            }.onFailure { error ->
'''
new = '''                val loadedChats = chatIds.mapNotNull { chatId ->
                    runCatching { activeClient.send(TdApi.GetChat(chatId)) }.getOrNull()
                }
                val summaries = loadedChats.map { chat ->
                    chat.toSummary(
                        currentUserId = userId,
                        selected = chat.id in selectedIds,
                        profilePhotoRef = localChatPhotoRef(chat),
                    )
                }.sortedWith(compareByDescending<TelegramChatSummary> { it.kind == TelegramChatKind.SAVED_MESSAGES }.thenBy { it.title.lowercase(Locale.ROOT) })
                LoadedSelectableChats(loadedChats, summaries)
            }.onSuccess { loaded ->
                _musicSourceState.update { it.copy(chats = loaded.summaries, isLoadingChats = false) }
                // Avatars are cosmetic. They must never block Telegram chat discovery.
                scope.launch {
                    loaded.rawChats.forEach { chat ->
                        if (localChatPhotoRef(chat) != null) return@forEach
                        val ref = resolveChatPhotoRef(activeClient, chat) ?: return@forEach
                        _musicSourceState.update { state ->
                            state.copy(
                                chats = state.chats.map { row ->
                                    if (row.chatId == chat.id) row.copy(profilePhotoRef = ref) else row
                                },
                            )
                        }
                    }
                }
            }.onFailure { error ->
'''
if old not in text:
    raise SystemExit('refreshSelectableChats avatar block not found')
text = text.replace(old, new, 1)

old2 = '''    private suspend fun resolveChatPhotoRef(activeClient: TdLibClientAdapter, chat: TdApi.Chat): String? {
        val photo = chat.photo?.small ?: return null
        val resolved = if (photo.local.isDownloadingCompleted && photo.local.path.isNotBlank()) {
            photo
        } else {
            runCatching { activeClient.send(TdApi.DownloadFile(photo.id, CHAT_PHOTO_PRIORITY, 0L, 0L, true)) }.getOrNull()
        } ?: return null
        val path = resolved.local.path.takeIf(String::isNotBlank) ?: return null
        return File(path).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }
    }
'''
new2 = '''    private fun localChatPhotoRef(chat: TdApi.Chat): String? {
        val photo = chat.photo?.small ?: return null
        if (!photo.local.isDownloadingCompleted || photo.local.path.isBlank()) return null
        return File(photo.local.path).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }
    }

    private suspend fun resolveChatPhotoRef(activeClient: TdLibClientAdapter, chat: TdApi.Chat): String? {
        localChatPhotoRef(chat)?.let { return it }
        val photo = chat.photo?.small ?: return null
        val resolved = runCatching {
            activeClient.send(TdApi.DownloadFile(photo.id, CHAT_PHOTO_PRIORITY, 0L, 0L, true))
        }.getOrNull() ?: return null
        val path = resolved.local.path.takeIf {
            resolved.local.isDownloadingCompleted && it.isNotBlank()
        } ?: return null
        return File(path).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }
    }
'''
if old2 not in text:
    raise SystemExit('resolveChatPhotoRef block not found')
text = text.replace(old2, new2, 1)

marker = 'private data class MutableSyncResult('
addition = '''private data class LoadedSelectableChats(
    val rawChats: List<TdApi.Chat>,
    val summaries: List<TelegramChatSummary>,
)

'''
if marker not in text:
    raise SystemExit('MutableSyncResult marker not found')
text = text.replace(marker, addition + marker, 1)
p.write_text(text)
