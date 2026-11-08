package dev.behradhz.meowzix.domain.playback

import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random

data class ArtistShuffleItem<T>(val id: T, val artist: String?)

/** Seeded artist-weighted shuffle with per-artist track bags and no duplicates within a queue cycle. */
object ArtistBalancedShuffleEngine {
    const val DEFAULT_ALPHA = 0.8
    private const val ARTIST_COOLDOWN = 3
    private const val TRACK_COOLDOWN = 50
    private val secureRandom = SecureRandom()

    fun <T> newCycle(
        items: List<ArtistShuffleItem<T>>,
        previousTrackIds: List<T> = emptyList(),
        seed: Long = secureRandom.nextLong(),
        alpha: Double = DEFAULT_ALPHA,
        recentArtistKeys: List<String> = emptyList(),
    ): ShuffleCycle<T> {
        require(items.map { it.id }.distinct().size == items.size) { "Shuffle requires unique track IDs" }
        require(alpha.isFinite() && alpha in 0.0..1.0)
        if (items.isEmpty()) return ShuffleCycle(emptyList(), seed)
        val random = Random(seed)
        val bags = linkedMapOf<String, MutableList<T>>()
        val artistById = items.associate { it.id to artistKey(it) }
        items.forEach { bags.getOrPut(artistKey(it)) { arrayListOf() }.add(it.id) }
        bags.values.forEach { it.shuffle(random) }
        val lastArtists = ArrayDeque<String>(ARTIST_COOLDOWN)
        (recentArtistKeys + previousTrackIds.mapNotNull(artistById::get))
            .take(ARTIST_COOLDOWN).asReversed().forEach(lastArtists::addLast)
        val cooldown = previousTrackIds.take(TRACK_COOLDOWN).toHashSet()
        val output = ArrayList<T>(items.size)
        while (output.size < items.size) {
            val remaining = bags.filterValues { it.isNotEmpty() }
            val fresh = remaining.filterValues { bag -> bag.any { it !in cooldown } }
            val counts = (if (fresh.isNotEmpty()) fresh else remaining).mapValues { it.value.size }
            val artist = weightedArtist(counts, lastArtists.toList(), random, alpha)
            val bag = bags.getValue(artist)
            val index = bag.indices.lastOrNull { bag[it] !in cooldown } ?: bag.lastIndex
            val track = bag.removeAt(index)
            output += track
            cooldown.remove(track)
            lastArtists.addLast(artist)
            if (lastArtists.size > ARTIST_COOLDOWN) lastArtists.removeFirst()
        }
        return ShuffleCycle(output, seed)
    }

    fun <T> newCycleStartingWith(
        items: List<ArtistShuffleItem<T>>,
        selectedTrackId: T,
        previousTrackIds: List<T> = emptyList(),
        seed: Long = secureRandom.nextLong(),
        alpha: Double = DEFAULT_ALPHA,
    ): ShuffleCycle<T> {
        require(items.any { it.id == selectedTrackId }) { "Selected track must be eligible" }
        require(items.map { it.id }.distinct().size == items.size) { "Shuffle requires unique track IDs" }
        val remaining = items.filterNot { it.id == selectedTrackId }
        val selectedArtist = artistKey(items.first { it.id == selectedTrackId })
        return ShuffleCycle(listOf(selectedTrackId) + newCycle(
            remaining, listOf(selectedTrackId) + previousTrackIds, seed, alpha, listOf(selectedArtist)
        ).order, seed)
    }

    /** Smart may use the same artist policy for exploratory candidates, never exploitation. */
    fun <T> pickExploration(
        items: List<ArtistShuffleItem<T>>,
        recentArtists: List<String?> = emptyList(),
        seed: Long,
        alpha: Double = DEFAULT_ALPHA,
    ): T {
        require(items.isNotEmpty())
        require(alpha.isFinite() && alpha in 0.0..1.0)
        val random = Random(seed)
        val groups = items.groupBy(::artistKey)
        val recent = recentArtists.take(ARTIST_COOLDOWN).mapNotNull {
            it?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotBlank)
        }
        val artist = weightedArtist(groups.mapValues { it.value.size }, recent, random, alpha)
        return groups.getValue(artist).random(random).id
    }

    private fun <T> artistKey(item: ArtistShuffleItem<T>) =
        item.artist?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() && it != "unknown artist" }
            ?: "\u0000${item.id}"

    private fun weightedArtist(
        counts: Map<String, Int>, recent: List<String>, rng: Random, alpha: Double,
    ): String {
        val hasAlternative = counts.keys.any { it !in recent }
        val weights = counts.map { (artist, count) ->
            val distance = recent.asReversed().indexOf(artist)
            val cooldownFactor = if (hasAlternative && distance >= 0) when (distance) {
                0 -> 0.06
                1 -> 0.25
                else -> 0.60
            } else 1.0
            artist to count.toDouble().pow(alpha) * cooldownFactor
        }
        var value = rng.nextDouble() * weights.sumOf { it.second }
        for ((artist, weight) in weights) {
            value -= weight
            if (value < 0) return artist
        }
        return weights.last().first
    }
}
