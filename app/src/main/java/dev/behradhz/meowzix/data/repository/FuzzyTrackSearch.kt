package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.common.TextNormalizer

/** Conservative typo matching used only after exact/FTS/contains search has produced no result. */
internal object FuzzyTrackSearch {
    fun score(query: String, title: String, artist: String?, album: String?): Int? {
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty() || queryTokens.any { it.length < MIN_FUZZY_TOKEN_LENGTH }) return null
        val fields = listOf(
            0 to tokens(title),
            1 to tokens(artist),
            2 to tokens(album),
        )
        var total = 0
        for (queryToken in queryTokens) {
            val maxDistance = if (queryToken.length >= LONG_TOKEN_LENGTH) 2 else 1
            var best: Int? = null
            for ((fieldPenalty, fieldTokens) in fields) {
                for (candidate in fieldTokens) {
                    val distance = boundedLevenshtein(queryToken, candidate, maxDistance) ?: continue
                    val score = distance * DISTANCE_WEIGHT + fieldPenalty
                    if (best == null || score < best) best = score
                }
            }
            total += best ?: return null
        }
        return total
    }

    fun anchor(query: String): String? = tokens(query)
        .filter { it.length >= MIN_FUZZY_TOKEN_LENGTH }
        .maxByOrNull(String::length)
        ?.take(1)

    internal fun boundedLevenshtein(left: String, right: String, maxDistance: Int): Int? {
        if (kotlin.math.abs(left.length - right.length) > maxDistance) return null
        if (left == right) return 0
        var previous = IntArray(right.length + 1) { it }
        for (i in 1..left.length) {
            val current = IntArray(right.length + 1)
            current[0] = i
            var rowMin = current[0]
            for (j in 1..right.length) {
                val substitution = previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > maxDistance) return null
            previous = current
        }
        return previous[right.length].takeIf { it <= maxDistance }
    }

    private fun tokens(value: String?): List<String> = TextNormalizer.normalize(value)
        ?.split(' ')
        ?.filter(String::isNotBlank)
        .orEmpty()

    private const val MIN_FUZZY_TOKEN_LENGTH = 4
    private const val LONG_TOKEN_LENGTH = 8
    private const val DISTANCE_WEIGHT = 4
}
