package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RecommendationFeatureSchemaTest {
    private val context = RecommendationContext(23, 7, true, TimeBucket.NIGHT, Instant.parse("2026-10-02T20:00:00Z"))
    private val track = TrackPersonalizationFeatures(UUID(0, 1), null, false, 0)
    @Test fun v2FeatureOrderIsAnImmutableArtifactContract() {
        assertEquals(2, RecommendationFeatureSchemaV2.VERSION)
        assertEquals(57, RecommendationFeatureSchemaV2.size)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(RecommendationFeatureSchemaV2.names.joinToString(",").toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("5df1974f44b22ce29407937b672500695ce41c74d327a2653817c8a7752d9f49", fingerprint)
    }

    @Test fun v3AddsOnlyAppendedContextAndVerifiedMetadataFeatures() {
        assertEquals(3, RecommendationFeatureSchemaV3.VERSION)
        assertEquals(68, RecommendationFeatureSchemaV3.size)
        assertEquals(RecommendationFeatureSchemaV2.names,
            RecommendationFeatureSchemaV3.names.take(RecommendationFeatureSchemaV2.size))
        val metadata = CanonicalTrackMetadata.fromLibrary(
            track.trackId, "  Night Drive  ", "Two Artists", "An Album",
            180_000, Instant.parse("2026-10-01T20:00:00Z").toEpochMilli(),
        )
        val v = PersonalizationFeatureVectorizer.vectorize(
            context.copy(sessionId = UUID(0, 2), sessionElapsedMs = 7_200_000L,
                recentTrackIds = listOf(UUID(0, 3)), outputClass = OutputClass.BLUETOOTH,
                offlineOnly = true, offlineOnlyKnown = true),
            track.copy(metadata = metadata),
        )
        RecommendationFeatureSchemaV3.validate(v)
        assertEquals(1.0, v[RecommendationFeature.TITLE_AVAILABLE.ordinal], 0.0)
        assertEquals(2.0 / 16.0, v[RecommendationFeature.TITLE_TOKEN_COUNT.ordinal], 0.0)
        assertEquals(1.0, v[RecommendationFeature.ALBUM_METADATA_AVAILABLE.ordinal], 0.0)
        assertEquals(1.0, v[RecommendationFeature.LIBRARY_ADDED_AVAILABLE.ordinal], 0.0)
        assertEquals(0.5, v[RecommendationFeature.SESSION_ELAPSED.ordinal], 0.0)
        assertEquals(1.0, v[RecommendationFeature.OUTPUT_BLUETOOTH.ordinal], 0.0)
        assertEquals(1.0, v[RecommendationFeature.OFFLINE_KNOWN.ordinal], 0.0)
        assertEquals(1.0, v[RecommendationFeature.OFFLINE_ONLY.ordinal], 0.0)
    }

    @Test fun missingOrFutureMetadataNeverFabricatesSignals() {
        val missing = PersonalizationFeatureVectorizer.vectorize(context, track)
        assertEquals(0.0, missing[RecommendationFeature.TITLE_AVAILABLE.ordinal], 0.0)
        assertEquals(0.0, missing[RecommendationFeature.ALBUM_METADATA_AVAILABLE.ordinal], 0.0)
        assertEquals(0.0, missing[RecommendationFeature.LIBRARY_ADDED_AVAILABLE.ordinal], 0.0)
        assertEquals(0.0, missing[RecommendationFeature.OFFLINE_KNOWN.ordinal], 0.0)
        val future = CanonicalTrackMetadata.fromLibrary(
            track.trackId, "Title", null, null, 0L,
            context.timestamp.plusSeconds(3600L).toEpochMilli(),
        )
        val vector = PersonalizationFeatureVectorizer.vectorize(context, track.copy(metadata = future))
        assertEquals(0.0, vector[RecommendationFeature.LIBRARY_ADDED_AVAILABLE.ordinal], 0.0)
    }

    @Test fun zeroAcousticValuesAndUnavailableAudioHaveDifferentMasks() {
        val missing = PersonalizationFeatureVectorizer.vectorize(context, track)
        val zero = PersonalizationFeatureVectorizer.vectorize(context, track.copy(audioFeatures = DoubleArray(6)))
        assertEquals(0.0, missing[RecommendationFeature.AUDIO_AVAILABLE.ordinal], 0.0)
        assertEquals(1.0, zero[RecommendationFeature.AUDIO_AVAILABLE.ordinal], 0.0)
        assertEquals(0.0, zero[RecommendationFeature.AUDIO_RMS.ordinal], 0.0)
        assertEquals(0.0, missing[RecommendationFeature.DURATION_AVAILABLE.ordinal], 0.0)
        assertEquals(0.0, missing[RecommendationFeature.LAST_PLAY_AVAILABLE.ordinal], 0.0)
    }
    @Test fun normalizationRemainsFiniteBoundedAndDeterministicAtExtremes() {
        val extreme = track.copy(durationMs = Long.MAX_VALUE, lastPlayedAt = Instant.EPOCH,
            global = PreferenceSnapshot(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        val vector = PersonalizationFeatureVectorizer.vectorize(context.copy(sessionPosition = Int.MAX_VALUE, recentSkipStreak = Int.MAX_VALUE), extreme)
        RecommendationFeatureSchemaV2.validate(vector)
        assertArrayEquals(vector, PersonalizationFeatureVectorizer.vectorize(context.copy(sessionPosition = Int.MAX_VALUE, recentSkipStreak = Int.MAX_VALUE), extreme), 0.0)
        assertEquals(1.0, vector[RecommendationFeature.PLAY_COUNT.ordinal], 0.0)
        assertEquals(1.0, vector[RecommendationFeature.DURATION.ordinal], 0.0)
    }
    @Test fun oneObservationCannotCreateAnExtremeBehavioralRate() {
        val vector = PersonalizationFeatureVectorizer.vectorize(context, track.copy(global = PreferenceSnapshot(starts = 1, earlySkips = 1)))
        assertTrue(vector[RecommendationFeature.EARLY_SKIP_RATE.ordinal] < 0.5)
        assertTrue(vector[RecommendationFeature.COMPLETION_RATE.ordinal] > 0.0)
    }
    @Test fun cyclicHourEncodingKeepsMidnightAdjacentToLateNight() {
        fun vector(hour: Int) = PersonalizationFeatureVectorizer.vectorize(context.copy(localHour = hour), track)
        fun distance(a: DoubleArray, b: DoubleArray) = listOf(RecommendationFeature.HOUR_SIN, RecommendationFeature.HOUR_COS).sumOf { f ->
            val difference = a[f.ordinal] - b[f.ordinal]; difference * difference
        }
        assertTrue(distance(vector(23), vector(0)) < distance(vector(12), vector(0)))
    }
}
