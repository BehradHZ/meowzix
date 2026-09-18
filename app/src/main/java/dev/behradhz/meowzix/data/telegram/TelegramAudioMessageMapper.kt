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
    is TdApi.MessageAudio -> {
        val audio = body.audio
        TelegramAudioCandidate(
            messageId = id,
            title = bestTitle(audio.title, audio.fileName),
            artist = audio.performer.cleanOrNull(),
            durationMs = audio.duration.toLong().coerceAtLeast(0L) * 1_000L,
            fileName = audio.fileName.cleanOrNull(),
            mimeType = audio.mimeType.cleanOrNull(),
            fileId = audio.audio.id,
            persistentFileId = audio.audio.remote.uniqueId.cleanOrNull(),
            fileSizeBytes = audio.audio.size.toLong().takeIf { it > 0L },
        )
    }
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
        )
    }
    else -> null
}

private fun bestTitle(explicitTitle: String?, fileName: String?): String =
    explicitTitle.cleanOrNull()
        ?: fileName.cleanOrNull()?.substringBeforeLast('.', missingDelimiterValue = fileName.orEmpty())?.trim()?.takeIf(String::isNotBlank)
        ?: "Unknown Track"

private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf(String::isNotBlank)
