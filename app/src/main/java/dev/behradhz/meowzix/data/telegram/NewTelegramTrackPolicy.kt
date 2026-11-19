package dev.behradhz.meowzix.data.telegram

/** Avoids highlighting the full pre-existing chat history on the first synchronization. */
internal object NewTelegramTrackPolicy {
    fun isNewAfterBaseline(initialScanComplete: Boolean, newestMessageId: Long?, messageId: Long): Boolean =
        initialScanComplete && (messageId < 0L || messageId > (newestMessageId ?: 0L))
}
