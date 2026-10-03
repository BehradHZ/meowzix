package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.*
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CausalTrainingDatasetTest {
    private val trackId = UUID(0, 1)
    private val session = UUID(0, 2)
    private val timestamp = Instant.parse("2026-10-01T20:00:00Z")
    private val tracks = mapOf(trackId to TrainingTrack(trackId, "artist", "album", 200_000))
    private fun event(sequence: Long, playback: Long, type: ListeningEventType, id: UUID = trackId, ratio: Double? = null) =
        TrainingEvent(sequence, ListeningEvent(UUID(1, sequence), id, type, timestamp, 23, DayOfWeek.THURSDAY,
            TimeBucket.NIGHT, null, 200_000, ratio, PlaybackInitiator.USER, PlaybackMode.SMART_SHUFFLE), session, UUID(0, playback))
    private fun playback(start: Long, instance: Long, id: UUID = trackId) = listOf(
        event(start, instance, ListeningEventType.MANUAL_SELECTED, id),
        event(start + 1, instance, ListeningEventType.PLAY_STARTED, id),
        event(start + 2, instance, ListeningEventType.PLAY_COMPLETED, id, 0.95),
    )

    @Test fun ownOutcomeDoesNotLeakIntoFeatures() {
        val sample = CausalTrainingDataset.build(playback(1, 10), tracks).single()
        assertEquals(0.5, sample.features[RecommendationFeature.COMPLETION_RATE.ordinal], 0.0)
        assertEquals(0.0, sample.features[RecommendationFeature.PLAY_COUNT.ordinal], 0.0)
        assertEquals(0.95, sample.reward, 1e-12)
        assertEquals(3L, sample.dataVersion)
        assertEquals(3, sample.eventIds.size)
    }
    @Test fun laterDecisionUsesEarlierCompletionHistory() {
        val samples = CausalTrainingDataset.build(playback(1, 10) + playback(4, 11), tracks)
        assertTrue(samples[1].features[RecommendationFeature.COMPLETION_RATE.ordinal] > samples[0].features[RecommendationFeature.COMPLETION_RATE.ordinal])
        assertTrue(samples[1].features[RecommendationFeature.MANUAL_RATE.ordinal] > 0.0)
    }
    @Test fun tiedTimestampsStillHaveDistinctMonotonicDataVersions() {
        val samples = CausalTrainingDataset.build(playback(1, 10) + playback(4, 11), tracks)
        assertEquals(listOf(3L, 6L), samples.map { it.dataVersion })
    }
    @Test fun futureFavoriteAndAudioCannotLeakIntoEarlierTrainingDecision() {
        val audio = mapOf(trackId to TrainingAudio(DoubleArray(6) { 0.5 }, timestamp.plusSeconds(1), UUID(0, 20), "2"))
        val rows = playback(1, 10) + event(4, 11, ListeningEventType.FAVORITED)
        val sample = CausalTrainingDataset.build(rows, tracks, audio).single()
        assertEquals(0.0, sample.features[RecommendationFeature.FAVORITE.ordinal], 0.0)
        assertEquals(0.0, sample.features[RecommendationFeature.AUDIO_AVAILABLE.ordinal], 0.0)
    }
    @Test fun earlierAudioIsOptionalAndKeepsSourceProvenance() {
        val source = UUID(0, 99)
        val audio = mapOf(trackId to TrainingAudio(DoubleArray(6) { 0.5 }, timestamp.minusSeconds(1), source, "2"))
        val sample = CausalTrainingDataset.build(playback(1, 10), tracks, audio).single()
        assertEquals(1.0, sample.features[RecommendationFeature.AUDIO_AVAILABLE.ordinal], 0.0)
        assertEquals(source, sample.sourceIdUsedForAudio)
    }
    @Test fun canonicalIdDoesNotChangeFeaturesForOtherwiseEquivalentTracks() {
        val other = UUID(0, 100)
        val allTracks = tracks + (other to tracks.getValue(trackId).copy(trackId = other))
        val a = CausalTrainingDataset.build(playback(1, 10), allTracks).single()
        val b = CausalTrainingDataset.build(playback(1, 11, other), allTracks).single()
        assertArrayEquals(a.features, b.features, 0.0)
    }
    @Test fun resetFloorRejectsAnInstanceStartedBeforeReset() {
        assertTrue(CausalTrainingDataset.build(playback(1, 10), tracks, floor = 2).isEmpty())
    }
    @Test fun resetExcludesOldBehaviorFromFutureFeatures() {
        val samples = CausalTrainingDataset.build(playback(1, 10) + playback(4, 11), tracks, floor = 3)
        assertEquals(1, samples.size)
        assertEquals(0.5, samples.single().features[RecommendationFeature.COMPLETION_RATE.ordinal], 0.0)
    }
    @Test fun incrementalMaterializationKeepsCausalPastButOnlyReturnsNewOutcomes() {
        val sample = CausalTrainingDataset.build(playback(1, 10) + playback(4, 11), tracks, after = 3).single()
        assertEquals(6L, sample.dataVersion)
        assertTrue(sample.features[RecommendationFeature.COMPLETION_RATE.ordinal] > 0.5)
    }
    @Test fun aLegacySecondTerminalNeverDuplicatesTrainingOrPastStatistics() {
        val duplicate = event(4, 10, ListeningEventType.SKIPPED_EARLY, ratio = 0.05)
        val rows = playback(1, 10) + duplicate + playback(5, 11)
        val samples = CausalTrainingDataset.build(rows, tracks)
        assertEquals(2, samples.size)
        assertEquals(0.2, samples[1].features[RecommendationFeature.EARLY_SKIP_RATE.ordinal], 0.0)
        assertTrue(CausalTrainingDataset.build(playback(1, 10) + duplicate, tracks, after = 3).isEmpty())
    }
    @Test fun favoriteStateAvailabilityDoesNotPretendUnknownPastStateIsFalse() {
        val unknown = CausalTrainingDataset.build(playback(1, 10), tracks).single()
        val known = CausalTrainingDataset.build(listOf(event(1, 9, ListeningEventType.UNFAVORITED)) + playback(2, 10), tracks).single()
        assertEquals(0.0, unknown.features[RecommendationFeature.FAVORITE_AVAILABLE.ordinal], 0.0)
        assertEquals(1.0, known.features[RecommendationFeature.FAVORITE_AVAILABLE.ordinal], 0.0)
        assertEquals(0.0, known.features[RecommendationFeature.FAVORITE.ordinal], 0.0)
    }
    @Test fun pagedReplayMatchesThePureListAdapter() {
        val rows = playback(1, 10) + playback(4, 11) + playback(7, 12)
        val accumulator = CausalTrainingDataset.Accumulator(tracks, emptyMap(), setOf(UUID(0, 11), UUID(0, 12)), 0)
        rows.chunked(2).forEach { page -> page.forEach(accumulator::accept) }
        val expected = CausalTrainingDataset.build(rows, tracks, after = 3)
        assertEquals(expected.map { it.dataVersion }, accumulator.samples().map { it.dataVersion })
        expected.zip(accumulator.samples()).forEach { (a, b) -> assertArrayEquals(a.features, b.features, 0.0); assertEquals(a.reward, b.reward, 0.0) }
    }
    @Test fun orphanOutcomeAndUnfinalizedStartAreNotTrainingSamples() {
        assertTrue(CausalTrainingDataset.build(listOf(event(1, 10, ListeningEventType.PLAY_COMPLETED)), tracks).isEmpty())
        assertTrue(CausalTrainingDataset.build(listOf(event(1, 10, ListeningEventType.PLAY_STARTED)), tracks).isEmpty())
    }
    @Test fun automaticStopWithoutMeaningfulListeningIsNotPositiveFeedback() {
        val rows = listOf(event(1, 10, ListeningEventType.AUTO_SELECTED), event(2, 10, ListeningEventType.PLAY_STARTED), event(3, 10, ListeningEventType.PLAY_STOPPED))
        assertTrue(CausalTrainingDataset.build(rows, tracks).isEmpty())
    }
}
