package dev.behradhz.meowzix.data.telegram

/**
 * Process-memory snapshot shared by both forward entry points. A snapshot is
 * valid only for the same Telegram account, TDLib client and folder revision.
 * TTL covers membership/title changes that may not emit UpdateChatFolders.
 *
 * Empty snapshots are intentionally cached too: an account with no folders
 * should not block on TDLib every time the picker opens.
 */
internal class ForwardFolderMemoryCache<T>(
    private val maxAgeMillis: Long = 5 * 60_000L,
) {
    private data class Entry<T>(
        val accountId: String,
        val session: Any,
        val revision: Long,
        val savedAtMillis: Long,
        val folders: List<T>,
    )

    @Volatile private var entry: Entry<T>? = null

    fun snapshot(
        accountId: String,
        session: Any,
        revision: Long,
        nowMillis: Long,
    ): List<T>? {
        val cached = entry ?: return null
        return cached.folders.takeIf {
            cached.accountId == accountId &&
                cached.session === session &&
                cached.revision == revision &&
                nowMillis >= cached.savedAtMillis &&
                nowMillis - cached.savedAtMillis < maxAgeMillis
        }
    }

    fun save(
        accountId: String,
        session: Any,
        revision: Long,
        nowMillis: Long,
        folders: List<T>,
    ) {
        entry = Entry(accountId, session, revision, nowMillis, folders.toList())
    }
}
