package dev.behradhz.meowzix.domain.playback

import java.security.SecureRandom
import kotlin.random.Random

data class ShuffleCycle<T>(
    val order: List<T>,
    val seed: Long,
)

object PureShuffleEngine {
    private val secureRandom = SecureRandom()

    fun <T> newCycle(
        eligibleItems: List<T>,
        previousLastItem: T? = null,
        seed: Long = secureRandom.nextLong(),
    ): ShuffleCycle<T> {
        require(eligibleItems.distinct().size == eligibleItems.size) {
            "Pure Shuffle requires unique eligible items"
        }
        val shuffled = eligibleItems.toMutableList()
        val random = Random(seed)
        for (index in shuffled.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val item = shuffled[index]
            shuffled[index] = shuffled[swapIndex]
            shuffled[swapIndex] = item
        }
        if (shuffled.size > 1 && shuffled.first() == previousLastItem) {
            val swapIndex = random.nextInt(1, shuffled.size)
            val first = shuffled[0]
            shuffled[0] = shuffled[swapIndex]
            shuffled[swapIndex] = first
        }
        return ShuffleCycle(order = shuffled, seed = seed)
    }

    /** A direct selection starts an independent cycle: the chosen song plays first. */
    fun <T> newCycleStartingWith(
        eligibleItems: List<T>,
        selectedItem: T,
        seed: Long = secureRandom.nextLong(),
    ): ShuffleCycle<T> {
        require(eligibleItems.distinct().size == eligibleItems.size) {
            "Pure Shuffle requires unique eligible items"
        }
        require(selectedItem in eligibleItems) { "Selected item must be eligible" }
        val remainder = eligibleItems.filterNot { it == selectedItem }
        return ShuffleCycle(
            order = listOf(selectedItem) + newCycle(remainder, seed = seed).order,
            seed = seed,
        )
    }
}
