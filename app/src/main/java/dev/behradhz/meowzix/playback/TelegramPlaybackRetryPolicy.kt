package dev.behradhz.meowzix.playback

import java.util.UUID

/** Replacing the playable URI must not reset an occurrence's bounded error budget. */
internal fun keepTelegramPlaybackRetryForOccurrence(
    retryMediaId: String?,
    retryOccurrenceId: UUID?,
    currentMediaId: String?,
    currentOccurrenceId: UUID?,
): Boolean = retryMediaId != null &&
    retryMediaId == currentMediaId &&
    retryOccurrenceId == currentOccurrenceId
