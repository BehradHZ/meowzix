package dev.behradhz.meowzix.data.telegram

import android.net.Uri

/** A TDLib numeric file ID is not a durable song identity across Telegram sessions. */
internal const val TELEGRAM_FILE_UNIQUE_ID_QUERY = "fileUniqueId"

internal fun matchesTelegramFileIdentity(expectedUniqueId: String?, actualUniqueId: String?): Boolean =
    !expectedUniqueId.isNullOrBlank() && expectedUniqueId == actualUniqueId

/** Carry the stable Telegram file identity all the way to Media3's byte reader. */
internal fun telegramStreamUri(
    fileId: Int,
    trackId: String,
    fileUniqueId: String?,
): String = Uri.Builder()
    .scheme("meowzix-tdlib")
    .authority("audio")
    .appendPath(fileId.toString())
    .appendQueryParameter("trackId", trackId)
    .apply {
        fileUniqueId?.takeIf(String::isNotBlank)?.let {
            appendQueryParameter(TELEGRAM_FILE_UNIQUE_ID_QUERY, it)
        }
    }
    .build()
    .toString()
