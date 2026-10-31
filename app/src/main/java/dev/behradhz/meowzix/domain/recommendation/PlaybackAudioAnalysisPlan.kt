package dev.behradhz.meowzix.domain.recommendation

import java.util.UUID

/** A bounded, distinct listening window. It does not trigger decoding itself. */
object PlaybackAudioAnalysisPlan {
    const val MAX_NEXT_TRACKS = 2

    fun select(current: UUID?, upcoming: Sequence<UUID>): List<UUID> {
        if (current == null) return emptyList()
        return (sequenceOf(current) + upcoming)
            .distinct()
            .take(1 + MAX_NEXT_TRACKS)
            .toList()
    }
}
