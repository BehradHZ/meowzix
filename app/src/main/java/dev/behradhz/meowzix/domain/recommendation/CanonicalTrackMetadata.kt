package dev.behradhz.meowzix.domain.recommendation

import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * Phase 4: local, canonical item representation for ranking and future sequence models.
 *
 * Track identity is the library UUID, NEVER a filename/provider ID or a hash of its title.
 * The vocabulary is deliberately bounded and derived only from metadata already present
 * in the library. No inferred genre, release year, popularity, user tags or track number.
 */
data class CanonicalTrackMetadata(
    val trackId: UUID,
    val titleTokens: List<String>,
    val artistTokens: List<String>,
    val albumTokens: List<String>,
    val durationMs: Long?,
    /** First observed by the local library; NOT the original music release date. */
    val addedAt: Instant?,
) {
    companion object {
        const val MAX_TOKENS = 16
        private const val MAX_TEXT_LENGTH = 512
        private const val MAX_TOKEN_LENGTH = 48
        private val split = Regex("[^\\p{L}\\p{N}]+")

        fun fromLibrary(
            trackId: UUID,
            normalizedTitle: String?,
            normalizedArtist: String?,
            album: String?,
            durationMs: Long,
            createdAtEpochMs: Long?,
        ): CanonicalTrackMetadata = CanonicalTrackMetadata(
            trackId = trackId,
            titleTokens = tokens(normalizedTitle),
            artistTokens = tokens(normalizedArtist),
            albumTokens = tokens(album),
            durationMs = durationMs.takeIf { it > 0L },
            addedAt = createdAtEpochMs?.takeIf { it > 0L }?.let(Instant::ofEpochMilli),
        )

        fun tokens(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val normalized = Normalizer.normalize(raw.take(MAX_TEXT_LENGTH), Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
            return normalized.split(split).asSequence().filter(String::isNotBlank)
                .take(MAX_TOKENS).map { it.take(MAX_TOKEN_LENGTH) }.toList()
        }
    }
}
