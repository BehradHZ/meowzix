package dev.behradhz.meowzix.data.telegram

import java.util.Locale
import org.drinkless.tdlib.TdApi

internal data class TelegramAudioCandidate(
    val messageId: Long,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val fileName: String?,
    val mimeType: String?,
    val fileId: Int,
    val persistentFileId: String?,
    val fileSizeBytes: Long?,
    val artworkMinithumbnail: ByteArray?,
    val artworkFileId: Int?,
)

private val supportedAudioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

internal fun isSupportedAudioDocument(mimeType: String?, fileName: String?): Boolean {
    if (mimeType?.trim()?.lowercase(Locale.ROOT)?.startsWith("audio/") == true) return true
    val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
    return extension in supportedAudioExtensions
}

internal fun TdApi.Message.toAudioCandidate(): TelegramAudioCandidate? = when (val body = content) {
    is TdApi.MessageAudio -> body.audio.toAudioCandidate(id)
    is TdApi.MessageDocument -> {
        val document = body.document
        if (!isSupportedAudioDocument(document.mimeType, document.fileName)) return null
        TelegramAudioCandidate(
            messageId = id,
            title = bestTitle(null, document.fileName),
            artist = null,
            durationMs = 0L,
            fileName = document.fileName.cleanOrNull(),
            mimeType = document.mimeType.cleanOrNull(),
            fileId = document.document.id,
            persistentFileId = document.document.remote.uniqueId.cleanOrNull(),
            fileSizeBytes = document.document.size.toLong().takeIf { it > 0L },
            artworkMinithumbnail = null,
            artworkFileId = null,
        )
    }
    else -> null
}

/** Profile music has no message ID. Keep a deterministic negative ID per audio identity. */
internal fun telegramProfileMessageId(identity: String): Long =
    java.util.UUID.nameUUIDFromBytes(("telegram-profile:" + identity).toByteArray(Charsets.UTF_8))
        .mostSignificantBits or Long.MIN_VALUE

internal fun TdApi.Audio.profileIdentity(): String =
    audio.remote.uniqueId.cleanOrNull()
        ?: audio.remote.id.cleanOrNull()
        ?: listOf(title, performer, duration.toString(), fileName).joinToString("|")

internal fun TdApi.Audio.toAudioCandidate(messageId: Long): TelegramAudioCandidate =
    TelegramAudioCandidate(
        messageId = messageId,
        title = bestTitle(title, fileName),
        artist = performer.cleanOrNull(),
        durationMs = duration.toLong().coerceAtLeast(0L) * 1_000L,
        fileName = fileName.cleanOrNull(),
        mimeType = mimeType.cleanOrNull(),
        fileId = audio.id,
        persistentFileId = audio.remote.uniqueId.cleanOrNull(),
        fileSizeBytes = audio.size.toLong().takeIf { it > 0L },
        artworkMinithumbnail = albumCoverMinithumbnail?.data?.copyOf(),
        artworkFileId = albumCoverThumbnail?.file?.id,
    )

private fun bestTitle(explicitTitle: String?, fileName: String?): String =
    explicitTitle.cleanOrNull()
        ?: fileName.cleanOrNull()?.substringBeforeLast('.', missingDelimiterValue = fileName.orEmpty())?.trim()?.takeIf(String::isNotBlank)
        ?: "Unknown Track"

private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)
