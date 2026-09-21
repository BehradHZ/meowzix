package dev.behradhz.meowzix.data.repository

import kotlin.math.abs
import kotlin.math.max

data class TrackMatchCandidate(
    val trackId: String,
    val normalizedTitle: String,
    val normalizedArtist: String?,
    val durationMs: Long,
    val contentHashes: Set<String> = emptySet(),
)

data class IncomingTrackIdentity(
    val normalizedTitle: String,
    val normalizedArtist: String?,
    val durationMs: Long,
    val contentHashSha256: String? = null,
)

object UnifiedTrackMatcher {
    fun match(
        incoming: IncomingTrackIdentity,
        candidates: List<TrackMatchCandidate>,
    ): String? {
        incoming.contentHashSha256?.let { hash ->
            candidates.singleOrNull { hash in it.contentHashes }?.let { return it.trackId }
        }

        val artist = incoming.normalizedArtist ?: return null
        return candidates.singleOrNull { candidate ->
            candidate.normalizedTitle == incoming.normalizedTitle &&
                candidate.normalizedArtist == artist &&
                durationsMatch(incoming.durationMs, candidate.durationMs)
        }?.trackId
    }

    private fun durationsMatch(leftMs: Long, rightMs: Long): Boolean {
        if (leftMs <= 0L || rightMs <= 0L) return false
        val toleranceMs = max(2_000L, max(leftMs, rightMs) / 50L)
        return abs(leftMs - rightMs) <= toleranceMs
    }
}
