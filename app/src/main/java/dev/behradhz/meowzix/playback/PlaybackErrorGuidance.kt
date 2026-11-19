package dev.behradhz.meowzix.playback

import androidx.media3.common.PlaybackException

/**
 * Source error is Media3's generic description. Keep private paths and Telegram file IDs
 * out of the UI; the PlaybackService logs the full exception chain for diagnosis.
 */
internal fun userFacingPlaybackError(error: PlaybackException): String {
    val causeMessages = generateSequence(error.cause) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" | ")
    return when {
        causeMessages.contains("identity", ignoreCase = true) ||
            causeMessages.contains("stable identity", ignoreCase = true) ->
            "Telegram audio identity changed. Refresh the playlist and try again."
        causeMessages.contains("not ready", ignoreCase = true) ||
            causeMessages.contains("disconnected", ignoreCase = true) ->
            "Telegram is not connected. Reconnect and try again."
        causeMessages.contains("Timed out waiting for Telegram", ignoreCase = true) ||
            causeMessages.contains("download stalled", ignoreCase = true) ->
            "Telegram audio stopped buffering. Check your connection and retry."
        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "The audio file is missing. Refresh the source or download it again."
        error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "The audio file cannot be accessed. Check storage access and retry."
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            "The connection to the audio source failed. Check your network and retry."
        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "This audio could not be decoded. Try another copy of the track."
        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
            "The audio source could not be read. Retry or refresh its Telegram source."
        else -> "Playback failed (code ${error.errorCode})."
    }
}
