package dev.behradhz.meowzix.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable clock for time-sensitive smart-playlist membership.
 * Tests can subclass it and advance time without polling or sleeping.
 */
@Singleton
open class RulePlaylistClock @Inject constructor() {
    open fun millis(): Long = System.currentTimeMillis()
}

internal fun ruleBoundaryDelayMillis(
    clock: RulePlaylistClock,
    nextBoundaryEpochMs: Long,
    minimumDelayMs: Long = 50L,
): Long = (nextBoundaryEpochMs - clock.millis()).coerceAtLeast(minimumDelayMs)
