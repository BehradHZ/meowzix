package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class QueueItemOrigin {
    GENERATED,
    MANUAL,
}

enum class QueueGroupKind { ARTIST, ALBUM }

data class QueueGroup(val id: UUID, val kind: QueueGroupKind, val title: String)

enum class SmartFutureRevisionResult {
    APPLIED,
    NO_CHANGE,
    STALE,
    NOT_SMART,
}

/**
 * Owns the complete lightweight logical queue while Media3 only materializes a small sliding
 * window. Track ids identify canonical tracks, while the logical queue may intentionally contain
 * more than one occurrence of the same track (for example Play Next on the current/next item).
 *
 * Smart Shuffle provenance lives beside the logical queue, not in Compose/Media3. This lets a live
 * rerank mutate only automatically generated future entries while explicit user edits remain fixed.
 * [revision] changes only for logical queue/context mutations; Media3 window refill bookkeeping does
 * not invalidate an otherwise valid ranking result.
 */
@Singleton
class ProgressiveQueue @Inject constructor() {
    private val tracks = mutableListOf<PlayableTrack>()
    private val origins = mutableListOf<QueueItemOrigin>()
    private val occurrenceIds = mutableListOf<UUID>()
    private val groupIds = mutableListOf<UUID?>()
    private val groups = linkedMapOf<UUID, QueueGroup>()
    private val trackIds = hashSetOf<UUID>()
    private var currentIndex = -1
    private var materializedStartIndex = 0
    private var materializedEndExclusive = 0
    private var playbackMode = PlaybackMode.ORDERED
    private var repeatMode = RepeatMode.OFF
    private var shuffleSeed: Long? = null
    // Candidate pool is lightweight; Media3 receives its own, smaller materialization window.
    private var sourceTrackIds: List<UUID> = emptyList()
    private var pendingGeneratedIds: List<UUID> = emptyList()
    private var generatedStarted = 0
    private var nextGenerationAt = 90
    private var generationEpoch = 0L
    private var generatedRefillInFlight: GeneratedQueueBatch? = null
    private var generationEnabled = false
    private var revision = 0L

    private var cachedSnapshot: ProgressiveQueueSnapshot? = null

    @Synchronized
    fun start(
        orderedTracks: List<PlayableTrack>,
        requestedStartIndex: Int,
        mode: PlaybackMode,
        repeat: RepeatMode,
        seed: Long? = null,
        sourceIds: List<UUID> = emptyList(),
        pendingIds: List<UUID> = emptyList(),
    ): QueueWindowPlan {
        require(orderedTracks.isNotEmpty()) { "Progressive queue requires at least one track" }
        tracks.clear()
        tracks.addAll(orderedTracks)
        occurrenceIds.clear()
        occurrenceIds.addAll(List(orderedTracks.size) { UUID.randomUUID() })
        groupIds.clear()
        groupIds.addAll(List(orderedTracks.size) { null })
        groups.clear()
        origins.clear()
        generationEnabled = sourceIds.isNotEmpty()
        sourceTrackIds = sourceIds.toList()
        pendingGeneratedIds = pendingIds.toList()
        generatedStarted = 0
        nextGenerationAt = 90
        generationEpoch++
        generatedRefillInFlight = null
        val generated = generationEnabled || mode == PlaybackMode.SMART_SHUFFLE
        origins.addAll(List(orderedTracks.size) { if (generated) QueueItemOrigin.GENERATED else QueueItemOrigin.MANUAL })
        trackIds.clear()
        trackIds.addAll(orderedTracks.map(PlayableTrack::id))
        currentIndex = requestedStartIndex.coerceIn(tracks.indices)
        origins[currentIndex] = QueueItemOrigin.MANUAL
        playbackMode = mode
        repeatMode = repeat
        shuffleSeed = seed
        bumpRevision()
        return resetWindowLocked()
    }

    @Synchronized
    fun restore(
        orderedTrackIds: List<UUID>,
        availableTracks: Map<UUID, PlayableTrack>,
        requestedCurrentIndex: Int,
        requestedMaterializedStartIndex: Int,
        requestedMaterializedEndExclusive: Int,
        mode: PlaybackMode,
        repeat: RepeatMode,
        seed: Long?,
        requestedOrigins: List<QueueItemOrigin>? = null,
        sourceIds: List<UUID> = emptyList(),
        pendingIds: List<UUID> = emptyList(),
        generatedStartedCount: Int = 0,
        nextGenerationThreshold: Int = 90,
        requestedOccurrenceIds: List<UUID>? = null,
        requestedGroupIds: List<UUID?>? = null,
        requestedGroups: List<QueueGroup> = emptyList(),
    ): Boolean {
        val restoredOrigins = requestedOrigins ?: QueueProvenanceRestoreHints.consume(orderedTrackIds)
        val fallbackOrigin = if (mode == PlaybackMode.SMART_SHUFFLE) QueueItemOrigin.GENERATED else QueueItemOrigin.MANUAL
        val restored = orderedTrackIds.mapIndexedNotNull { index, id ->
            availableTracks[id]?.let { track ->
                RestoredEntry(
                    track,
                    restoredOrigins?.getOrNull(index) ?: fallbackOrigin,
                    requestedOccurrenceIds?.getOrNull(index) ?: UUID.randomUUID(),
                    requestedGroupIds?.getOrNull(index),
                )
            }
        }
        if (restored.isEmpty()) {
            clearLocked()
            return false
        }
        tracks.clear()
        tracks.addAll(restored.map { it.track })
        origins.clear()
        origins.addAll(restored.map { it.origin })
        occurrenceIds.clear()
        occurrenceIds.addAll(restored.map { it.occurrenceId })
        groupIds.clear()
        groupIds.addAll(restored.map { it.groupId })
        groups.clear()
        requestedGroups.filter { it.id in groupIds }.forEach { groups[it.id] = it }
        trackIds.clear()
        trackIds.addAll(tracks.map(PlayableTrack::id))
        currentIndex = requestedCurrentIndex.coerceIn(tracks.indices)
        origins[currentIndex] = QueueItemOrigin.MANUAL
        materializedStartIndex = requestedMaterializedStartIndex.coerceIn(0, currentIndex)
        materializedEndExclusive = requestedMaterializedEndExclusive
            .coerceIn(currentIndex + 1, tracks.size)
        playbackMode = mode
        repeatMode = repeat
        shuffleSeed = seed
        generationEnabled = sourceIds.isNotEmpty()
        sourceTrackIds = sourceIds.toList()
        pendingGeneratedIds = pendingIds.toList()
        generatedStarted = generatedStartedCount.coerceAtLeast(0)
        nextGenerationAt = nextGenerationThreshold.coerceAtLeast(90)
        generationEpoch++
        generatedRefillInFlight = null
        bumpRevision()
        return true
    }

    private data class RestoredEntry(
        val track: PlayableTrack,
        val origin: QueueItemOrigin,
        val occurrenceId: UUID,
        val groupId: UUID?,
    )

    @Synchronized
    fun clear() = clearLocked()

    @Synchronized
    fun snapshot(): ProgressiveQueueSnapshot? = cachedSnapshot ?: snapshotLocked()?.also {
        cachedSnapshot = it
    }

    @Synchronized
    fun updateCurrent(mediaId: String?): Boolean {
        val id = mediaId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return false
        if (tracks.getOrNull(currentIndex)?.id == id) return true
        val forwardIndex = (currentIndex + 1 until tracks.size).firstOrNull { tracks[it].id == id }
        val backwardIndex = (currentIndex - 1 downTo 0).firstOrNull { tracks[it].id == id }
        val index = forwardIndex ?: backwardIndex ?: return false
        if (index != currentIndex) {
            noteGeneratedTransition(index)
            currentIndex = index
            if (!generationEnabled) origins[currentIndex] = QueueItemOrigin.MANUAL
            bumpRevision()
        }
        return true
    }

    @Synchronized
    fun updateCurrentFromWindowIndex(windowIndex: Int): Boolean {
        if (windowIndex < 0) return false
        val logicalIndex = materializedStartIndex + windowIndex
        if (logicalIndex !in tracks.indices || logicalIndex >= materializedEndExclusive) return false
        if (logicalIndex != currentIndex) {
            noteGeneratedTransition(logicalIndex)
            currentIndex = logicalIndex
            if (!generationEnabled) origins[currentIndex] = QueueItemOrigin.MANUAL
            bumpRevision()
        }
        return true
    }

    @Synchronized
    fun takeForwardBatchIfNeeded(): List<PlayableTrack> {
        if (tracks.isEmpty() || currentIndex < 0) return emptyList()
        val remainingAhead = materializedEndExclusive - currentIndex - 1
        if (remainingAhead > REFILL_THRESHOLD || materializedEndExclusive >= tracks.size) return emptyList()
        val oldEnd = materializedEndExclusive
        val newEnd = (oldEnd + REFILL_BATCH_SIZE).coerceAtMost(tracks.size)
        materializedEndExclusive = newEnd
        invalidateSnapshot()
        return tracks.subList(oldEnd, newEnd).toList()
    }

    @Synchronized
    fun trimMaterializedHistory(): Int {
        if (tracks.isEmpty() || currentIndex < 0) return 0
        val desiredStart = (currentIndex - PREVIOUS_WINDOW_SIZE).coerceAtLeast(0)
        if (desiredStart <= materializedStartIndex) return 0
        val removed = desiredStart - materializedStartIndex
        materializedStartIndex = desiredStart
        invalidateSnapshot()
        return removed
    }

    @Synchronized
    fun takeBackwardBatchIfNeeded(): List<PlayableTrack> {
        if (tracks.isEmpty() || currentIndex < 0 || materializedStartIndex == 0) return emptyList()
        val remainingBehind = currentIndex - materializedStartIndex
        if (remainingBehind > BACKWARD_REFILL_THRESHOLD) return emptyList()
        val oldStart = materializedStartIndex
        val newStart = (oldStart - REFILL_BATCH_SIZE).coerceAtLeast(0)
        materializedStartIndex = newStart
        invalidateSnapshot()
        return tracks.subList(newStart, oldStart).toList()
    }

    @Synchronized
    fun resetWindow(): QueueWindowPlan {
        check(tracks.isNotEmpty() && currentIndex >= 0) { "No active progressive queue" }
        return resetWindowLocked()
    }

    @Synchronized
    fun jumpTo(index: Int): QueueWindowPlan? {
        if (index !in tracks.indices) return null
        if (currentIndex != index) {
            noteGeneratedTransition(index)
            currentIndex = index
            if (!generationEnabled) origins[currentIndex] = QueueItemOrigin.MANUAL
            bumpRevision()
        }
        return resetWindowLocked()
    }

    @Synchronized
    fun insertNext(track: PlayableTrack): Boolean {
        if (tracks.isEmpty()) {
            tracks += track
            origins += QueueItemOrigin.MANUAL
            occurrenceIds += UUID.randomUUID()
            groupIds += null
            trackIds += track.id
            currentIndex = 0
            bumpRevision()
            return true
        }
        if (currentIndex !in tracks.indices) return false

        val insertionIndex = currentIndex + 1
        val duplicateRequested =
            tracks[currentIndex].id == track.id || tracks.getOrNull(insertionIndex)?.id == track.id
        if (duplicateRequested) {
            tracks.add(insertionIndex.coerceAtMost(tracks.size), track)
            origins.add(insertionIndex.coerceAtMost(origins.size), QueueItemOrigin.MANUAL)
            occurrenceIds.add(insertionIndex.coerceAtMost(occurrenceIds.size), UUID.randomUUID())
            groupIds.add(insertionIndex.coerceAtMost(groupIds.size), null)
            trackIds += track.id
            bumpRevision()
            return true
        }

        val existingIndex = tracks.indexOfFirst { it.id == track.id }
        val occurrence = if (existingIndex >= 0) occurrenceIds.removeAt(existingIndex) else UUID.randomUUID()
        val item = if (existingIndex >= 0) {
            origins.removeAt(existingIndex)
            groupIds.removeAt(existingIndex)
            tracks.removeAt(existingIndex).also {
                if (existingIndex < currentIndex) currentIndex -= 1
            }
        } else {
            trackIds += track.id
            track
        }
        val destination = (currentIndex + 1).coerceAtMost(tracks.size)
        tracks.add(destination, item)
        origins.add(destination, QueueItemOrigin.MANUAL)
        occurrenceIds.add(destination, occurrence)
        groupIds.add(destination, null)
        pruneGroups()
        if (generationEnabled) pendingGeneratedIds = pendingGeneratedIds.filterNot { it == track.id }
        bumpRevision()
        return true
    }

    @Synchronized
    fun append(track: PlayableTrack): Boolean {
        if (tracks.isEmpty()) {
            tracks += track
            origins += QueueItemOrigin.MANUAL
            occurrenceIds += UUID.randomUUID()
            groupIds += null
            trackIds += track.id
            currentIndex = 0
            bumpRevision()
            return true
        }
        if (currentIndex !in tracks.indices || tracks[currentIndex].id == track.id) return false

        val existingIndex = tracks.indexOfFirst { it.id == track.id }
        val occurrence = if (existingIndex >= 0) occurrenceIds.removeAt(existingIndex) else UUID.randomUUID()
        val item = if (existingIndex >= 0) {
            origins.removeAt(existingIndex)
            groupIds.removeAt(existingIndex)
            tracks.removeAt(existingIndex).also {
                if (existingIndex < currentIndex) currentIndex -= 1
            }
        } else {
            trackIds += track.id
            track
        }
        val destination = if (generationEnabled) {
            (currentIndex + 1 until origins.size).firstOrNull { origins[it] == QueueItemOrigin.GENERATED }
                ?: tracks.size
        } else tracks.size
        tracks.add(destination, item)
        origins.add(destination, QueueItemOrigin.MANUAL)
        occurrenceIds.add(destination, occurrence)
        groupIds.add(destination, null)
        pruneGroups()
        if (generationEnabled) pendingGeneratedIds = pendingGeneratedIds.filterNot { it == track.id }
        bumpRevision()
        return true
    }

    @Synchronized
    fun removeAt(index: Int): Boolean {
        if (index !in tracks.indices) return false
        val removingCurrent = index == currentIndex
        val removed = tracks.removeAt(index)
        origins.removeAt(index)
        occurrenceIds.removeAt(index)
        groupIds.removeAt(index)
        pruneGroups()
        if (tracks.none { it.id == removed.id }) trackIds.remove(removed.id)
        if (tracks.isEmpty()) {
            clearLocked()
            return true
        }
        // Keep the logical-to-Media3 window mapping aligned after an in-place removal.
        // A removed off-window item must not require rebuilding the playing MediaItem.
        when {
            index < materializedStartIndex -> {
                materializedStartIndex--
                materializedEndExclusive--
            }
            index < materializedEndExclusive -> materializedEndExclusive--
        }
        currentIndex = when {
            removingCurrent -> index.coerceAtMost(tracks.lastIndex)
            index < currentIndex -> currentIndex - 1
            else -> currentIndex
        }
        if (!generationEnabled) origins[currentIndex] = QueueItemOrigin.MANUAL
        bumpRevision()
        return true
    }

    @Synchronized
    fun move(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) return false
        val movingCurrent = fromIndex == currentIndex
        // A moved future occurrence is user-prioritized. Never strand it below generated entries.
        val initialGeneratedBoundary = (currentIndex + 1 until origins.size)
            .firstOrNull { origins[it] == QueueItemOrigin.GENERATED } ?: tracks.size
        val manualTarget = if (generationEnabled && !movingCurrent) {
            toIndex.coerceAtMost(initialGeneratedBoundary)
        } else toIndex
        val item = tracks.removeAt(fromIndex)
        origins.removeAt(fromIndex)
        val occurrence = occurrenceIds.removeAt(fromIndex)
        val originalGroup = groupIds.removeAt(fromIndex)
        val desired = manualTarget.coerceIn(0, tracks.size)
        val targetGroup = groupIds.getOrNull(desired)
        val destination = if (targetGroup != null && targetGroup != originalGroup) {
            if (desired < fromIndex) groupIds.indexOfFirst { it == targetGroup }
            else groupIds.indexOfLast { it == targetGroup } + 1
        } else desired
        val to = destination.coerceIn(0, tracks.size)
        tracks.add(to, item)
        origins.add(to, QueueItemOrigin.MANUAL)
        occurrenceIds.add(to, occurrence)
        val stillGrouped = originalGroup != null &&
            (groupIds.getOrNull(to - 1) == originalGroup || groupIds.getOrNull(to) == originalGroup)
        groupIds.add(to, originalGroup.takeIf { stillGrouped })
        pruneGroups()
        currentIndex = if (movingCurrent) {
            to
        } else {
            var adjusted = currentIndex
            if (fromIndex < adjusted) adjusted -= 1
            if (to <= adjusted) adjusted += 1
            adjusted
        }
        if (!generationEnabled) origins[currentIndex] = QueueItemOrigin.MANUAL
        bumpRevision()
        return true
    }

    @Synchronized
    fun retainCurrentOnly(): Boolean {
        val current = tracks.getOrNull(currentIndex) ?: return false
        val currentOccurrence = occurrenceIds[currentIndex]
        tracks.clear()
        tracks += current
        origins.clear()
        origins += QueueItemOrigin.MANUAL
        occurrenceIds.clear()
        occurrenceIds += currentOccurrence
        groupIds.clear()
        groupIds += null
        groups.clear()
        trackIds.clear()
        trackIds += current.id
        currentIndex = 0
        materializedStartIndex = 0
        materializedEndExclusive = 1
        playbackMode = PlaybackMode.ORDERED
        repeatMode = RepeatMode.OFF
        shuffleSeed = null
        generationEnabled = false
        sourceTrackIds = emptyList()
        pendingGeneratedIds = emptyList()
        generationEpoch++
        generatedRefillInFlight = null
        generatedStarted = 0
        nextGenerationAt = 90
        bumpRevision()
        return true
    }

    @Synchronized
    fun replaceFuture(
        future: List<PlayableTrack>,
        mode: PlaybackMode,
        seed: Long? = null,
        sourceIds: List<UUID>? = null,
        pendingIds: List<UUID>? = null,
    ) {
        if (tracks.isEmpty() || currentIndex < 0) return
        val fixedTracks = tracks.take(currentIndex + 1)
        val fixedOrigins = origins.take(currentIndex + 1)
        val fixedOccurrences = occurrenceIds.take(currentIndex + 1)
        val fixedGroups = groupIds.take(currentIndex + 1)
        val manualFuture = if (generationEnabled) {
            (currentIndex + 1 until tracks.size).filter { origins[it] == QueueItemOrigin.MANUAL }.map { tracks[it] }
        } else emptyList()
        val manualPositions = if (generationEnabled) (currentIndex + 1 until tracks.size)
            .filter { origins[it] == QueueItemOrigin.MANUAL } else emptyList()
        val manualOccurrences = manualPositions.map { occurrenceIds[it] }
        val manualGroups = manualPositions.map { groupIds[it] }
        tracks.clear()
        tracks.addAll(fixedTracks)
        tracks.addAll(manualFuture)
        tracks.addAll(future)
        origins.clear()
        origins.addAll(fixedOrigins)
        occurrenceIds.clear()
        occurrenceIds.addAll(fixedOccurrences)
        occurrenceIds.addAll(manualOccurrences)
        occurrenceIds.addAll(List(future.size) { UUID.randomUUID() })
        groupIds.clear()
        groupIds.addAll(fixedGroups)
        groupIds.addAll(manualGroups)
        groupIds.addAll(List(future.size) { null })
        pruneGroups()
        origins.addAll(List(manualFuture.size) { QueueItemOrigin.MANUAL })
        val futureOrigin = if (generationEnabled || mode == PlaybackMode.SMART_SHUFFLE) QueueItemOrigin.GENERATED else QueueItemOrigin.MANUAL
        origins.addAll(List(future.size) { futureOrigin })
        trackIds.clear()
        trackIds.addAll(tracks.map(PlayableTrack::id))
        playbackMode = mode
        shuffleSeed = seed
        if (sourceIds != null) {
            sourceTrackIds = sourceIds.toList()
            generationEnabled = sourceIds.isNotEmpty()
        }
        if (pendingIds != null) pendingGeneratedIds = pendingIds.toList()
        generationEpoch++
        generatedRefillInFlight = null
        generatedStarted = 0
        nextGenerationAt = 90
        materializedStartIndex = materializedStartIndex.coerceIn(0, currentIndex)
        materializedEndExclusive = (currentIndex + 1 + INITIAL_FORWARD_COUNT).coerceAtMost(tracks.size)
        bumpRevision()
    }

    @Synchronized
    fun rerankGeneratedFuture(
        rankedTrackIds: List<UUID>,
        expectedRevision: Long,
        windowLimit: Int = SMART_RERANK_WINDOW,
    ): SmartFutureRevisionResult {
        if (playbackMode != PlaybackMode.SMART_SHUFFLE) return SmartFutureRevisionResult.NOT_SMART
        if (revision != expectedRevision) return SmartFutureRevisionResult.STALE
        if (currentIndex !in tracks.indices || windowLimit <= 0) return SmartFutureRevisionResult.NO_CHANGE

        val generatedPositions = (currentIndex + 1 until tracks.size)
            .filter { origins[it] == QueueItemOrigin.GENERATED }
            .take(windowLimit)
        if (generatedPositions.size <= 1) return SmartFutureRevisionResult.NO_CHANGE

        val currentGenerated = generatedPositions.map { tracks[it] }
        val byId = currentGenerated.associateBy(PlayableTrack::id)
        val ranked = buildList(currentGenerated.size) {
            rankedTrackIds.distinct().forEach { id -> byId[id]?.let { add(it) } }
            val selected = mapTo(hashSetOf(), PlayableTrack::id)
            currentGenerated.forEach { if (it.id !in selected) add(it) }
        }
        if (ranked.map(PlayableTrack::id) == currentGenerated.map(PlayableTrack::id)) {
            return SmartFutureRevisionResult.NO_CHANGE
        }

        val occurrencesById = generatedPositions.associate { position ->
            tracks[position].id to occurrenceIds[position]
        }
        generatedPositions.forEachIndexed { index, position ->
            tracks[position] = ranked[index]
            occurrenceIds[position] = occurrencesById.getValue(ranked[index].id)
        }
        bumpRevision()
        return SmartFutureRevisionResult.APPLIED
    }

    @Synchronized
    fun updateRepeatMode(mode: RepeatMode) {
        if (repeatMode != mode) {
            repeatMode = mode
            bumpRevision()
        }
    }

    @Synchronized
    fun updatePlaybackMode(mode: PlaybackMode, seed: Long? = shuffleSeed) {
        if (playbackMode != mode || shuffleSeed != seed) {
            playbackMode = mode
            shuffleSeed = seed
            if (!generationEnabled && mode != PlaybackMode.SMART_SHUFFLE) {
                for (index in origins.indices) origins[index] = QueueItemOrigin.MANUAL
            }
            bumpRevision()
        }
    }

    private fun noteGeneratedTransition(destination: Int) {
        if (!generationEnabled || destination <= currentIndex ||
            origins[destination] != QueueItemOrigin.GENERATED) return
        // The threshold is based on position, not on how many transitions Media3 emitted.
        // Jumping directly to item 95 must trigger the same refill as playing 1..95.
        val generatedBefore = (0..destination).count { origins[it] == QueueItemOrigin.GENERATED }
        generatedStarted = maxOf(generatedStarted, generatedBefore)
    }

    @Synchronized
    fun claimGeneratedRefill(): GeneratedQueueBatch? {
        if (!generationEnabled || generatedRefillInFlight != null ||
            generatedStarted < nextGenerationAt || pendingGeneratedIds.isEmpty()) return null
        return GeneratedQueueBatch(
            generationEpoch,
            pendingGeneratedIds.take(GENERATED_BATCH_SIZE),
        ).also { generatedRefillInFlight = it }
    }

    @Synchronized
    fun appendGeneratedBatch(batch: GeneratedQueueBatch, resolved: List<PlayableTrack>): Boolean {
        if (batch.epoch != generationEpoch || !generationEnabled ||
            generatedRefillInFlight != batch) return false
        generatedRefillInFlight = null
        val selected = batch.ids.toSet()
        pendingGeneratedIds = pendingGeneratedIds.filterNot { it in selected }
        nextGenerationAt += GENERATED_BATCH_SIZE
        val byId = resolved.associateBy { it.id }
        val existing = tracks.mapTo(hashSetOf()) { it.id }
        val accepted = batch.ids.mapNotNull(byId::get).filter { existing.add(it.id) }
        if (accepted.isNotEmpty()) {
            tracks.addAll(accepted)
            origins.addAll(List(accepted.size) { QueueItemOrigin.GENERATED })
            occurrenceIds.addAll(List(accepted.size) { UUID.randomUUID() })
            groupIds.addAll(List(accepted.size) { null })
            trackIds.addAll(accepted.map { it.id })
            bumpRevision()
        } else {
            // Persist the claimed cursor even when the entire batch became unavailable.
            bumpRevision()
        }
        return true
    }

    @Synchronized
    fun insertGroup(members: List<PlayableTrack>, kind: QueueGroupKind, title: String, playNext: Boolean): UUID? {
        if (tracks.isEmpty() || currentIndex !in tracks.indices || members.isEmpty()) return null
        val selected = members.distinctBy { it.id }.filter { it.id != tracks[currentIndex].id }
        if (selected.isEmpty()) return null
        val selectedIds = selected.mapTo(hashSetOf()) { it.id }
        for (index in tracks.lastIndex downTo currentIndex + 1) {
            if (origins[index] == QueueItemOrigin.GENERATED && tracks[index].id in selectedIds) removeAt(index)
        }
        pendingGeneratedIds = pendingGeneratedIds.filterNot { it in selectedIds }
        val destination = if (playNext) currentIndex + 1 else
            (currentIndex + 1 until origins.size).firstOrNull { origins[it] == QueueItemOrigin.GENERATED }
                ?: tracks.size
        val id = UUID.randomUUID()
        groups[id] = QueueGroup(id, kind, title)
        tracks.addAll(destination, selected)
        origins.addAll(destination, List(selected.size) { QueueItemOrigin.MANUAL })
        occurrenceIds.addAll(destination, List(selected.size) { UUID.randomUUID() })
        groupIds.addAll(destination, List(selected.size) { id })
        trackIds.addAll(selected.map { it.id })
        bumpRevision()
        return id
    }

    @Synchronized
    fun removeGroup(id: UUID): Boolean {
        val positions = (currentIndex + 1 until tracks.size).filter { groupIds[it] == id }
        if (positions.isEmpty()) return false
        positions.asReversed().forEach(::removeAt)
        groups.remove(id)
        return true
    }

    @Synchronized
    fun moveGroup(id: UUID, target: Int): Boolean {
        val positions = (currentIndex + 1 until tracks.size).filter { groupIds[it] == id }
        if (positions.isEmpty() || target in positions) return false
        if (positions.last() - positions.first() + 1 != positions.size) return false
        val first = positions.first()
        val count = positions.size
        val firstGenerated = (currentIndex + 1 until origins.size)
            .firstOrNull { origins[it] == QueueItemOrigin.GENERATED } ?: tracks.size
        var destination = target.coerceIn(currentIndex + 1, firstGenerated)
        val targetGroup = groupIds.getOrNull(destination)
        if (targetGroup != null && targetGroup != id) {
            destination = if (destination < first) {
                groupIds.indexOfFirst { it == targetGroup }.coerceAtLeast(currentIndex + 1)
            } else {
                groupIds.indexOfLast { it == targetGroup } + 1
            }
        }
        val tracksBlock = tracks.subList(first, first + count).toList()
        val originsBlock = origins.subList(first, first + count).toList()
        val occurrencesBlock = occurrenceIds.subList(first, first + count).toList()
        val groupsBlock = groupIds.subList(first, first + count).toList()
        repeat(count) {
            tracks.removeAt(first)
            origins.removeAt(first)
            occurrenceIds.removeAt(first)
            groupIds.removeAt(first)
        }
        val at = (destination - if (destination > first) count else 0)
            .coerceIn(currentIndex + 1, tracks.size)
        tracks.addAll(at, tracksBlock)
        origins.addAll(at, originsBlock)
        occurrenceIds.addAll(at, occurrencesBlock)
        groupIds.addAll(at, groupsBlock)
        bumpRevision()
        return true
    }

    private fun pruneGroups() {
        val alive = groupIds.filterNotNull().toSet()
        groups.keys.retainAll(alive)
    }

    @Synchronized
    fun contains(trackId: UUID): Boolean = trackId in trackIds

    @Synchronized
    fun hasPrevious(): Boolean = currentIndex > 0

    @Synchronized
    fun hasNext(): Boolean = currentIndex >= 0 && currentIndex < tracks.lastIndex

    private fun resetWindowLocked(): QueueWindowPlan {
        val newStart = (currentIndex - PREVIOUS_WINDOW_SIZE).coerceAtLeast(0)
        val newEnd = (currentIndex + 1 + INITIAL_FORWARD_COUNT).coerceAtMost(tracks.size)
        if (materializedStartIndex != newStart || materializedEndExclusive != newEnd) {
            materializedStartIndex = newStart
            materializedEndExclusive = newEnd
            invalidateSnapshot()
        }
        return QueueWindowPlan(
            tracks = tracks.subList(materializedStartIndex, materializedEndExclusive).toList(),
            startIndexInWindow = currentIndex - materializedStartIndex,
        )
    }

    private fun snapshotLocked(): ProgressiveQueueSnapshot? {
        if (tracks.isEmpty() || currentIndex !in tracks.indices) return null
        return ProgressiveQueueSnapshot(
            tracks = tracks.toList(),
            origins = origins.toList(),
            occurrenceIds = occurrenceIds.toList(),
            groupIds = groupIds.toList(),
            groups = groups.toMap(),
            currentIndex = currentIndex,
            materializedStartIndex = materializedStartIndex,
            materializedEndExclusive = materializedEndExclusive,
            playbackMode = playbackMode,
            repeatMode = repeatMode,
            shuffleSeed = shuffleSeed,
            sourceTrackIds = sourceTrackIds,
            pendingGeneratedIds = pendingGeneratedIds,
            generatedStarted = generatedStarted,
            nextGenerationAt = nextGenerationAt,
            revision = revision,
        )
    }

    private fun clearLocked() {
        val hadState = tracks.isNotEmpty() || currentIndex >= 0
        tracks.clear()
        origins.clear()
        occurrenceIds.clear()
        groupIds.clear()
        groups.clear()
        trackIds.clear()
        currentIndex = -1
        materializedStartIndex = 0
        materializedEndExclusive = 0
        playbackMode = PlaybackMode.ORDERED
        repeatMode = RepeatMode.OFF
        shuffleSeed = null
        generationEnabled = false
        sourceTrackIds = emptyList()
        pendingGeneratedIds = emptyList()
        generationEpoch++
        generatedRefillInFlight = null
        generatedStarted = 0
        nextGenerationAt = 90
        QueueProvenanceRestoreHints.clear()
        if (hadState) bumpRevision() else invalidateSnapshot()
    }

    private fun bumpRevision() {
        revision = if (revision == Long.MAX_VALUE) 1L else revision + 1L
        invalidateSnapshot()
    }

    private fun invalidateSnapshot() {
        cachedSnapshot = null
    }

    companion object {
        const val INITIAL_FORWARD_COUNT = 10
        const val REFILL_THRESHOLD = 3
        const val REFILL_BATCH_SIZE = 10
        const val PREVIOUS_WINDOW_SIZE = 3
        const val BACKWARD_REFILL_THRESHOLD = 1
        const val SMART_RERANK_WINDOW = 20
        const val GENERATED_BATCH_SIZE = 100
    }
}

data class GeneratedQueueBatch(val epoch: Long, val ids: List<UUID>)

data class QueueWindowPlan(
    val tracks: List<PlayableTrack>,
    val startIndexInWindow: Int,
)

data class ProgressiveQueueSnapshot(
    val tracks: List<PlayableTrack>,
    val origins: List<QueueItemOrigin>,
    val occurrenceIds: List<UUID> = emptyList(),
    val groupIds: List<UUID?> = emptyList(),
    val groups: Map<UUID, QueueGroup> = emptyMap(),
    val currentIndex: Int,
    val materializedStartIndex: Int,
    val materializedEndExclusive: Int,
    val playbackMode: PlaybackMode,
    val repeatMode: RepeatMode,
    val shuffleSeed: Long?,
    val sourceTrackIds: List<UUID> = emptyList(),
    val pendingGeneratedIds: List<UUID> = emptyList(),
    val generatedStarted: Int = 0,
    val nextGenerationAt: Int = 90,
    val revision: Long,
)
