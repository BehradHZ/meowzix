package dev.behradhz.meowzix.domain.recommendation

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationBasicsTest {
    private val track = TrackPersonalizationFeatures(
        trackId = UUID.nameUUIDFromBytes("evening-track".toByteArray()),
        normalizedArtist = "artist",
        favorite = false,
        durationMs = 240_000,
    )

    @Test
    fun repeatedPositiveEveningOutcomesRaiseEveningSuitability() {
        val evening = PersonalizationFeatureVectorizer.vectorize(
            RecommendationContext(19, 2, false, TimeBucket.EVENING),
            track,
        )
        val morning = PersonalizationFeatureVectorizer.vectorize(
            RecommendationContext(9, 2, false, TimeBucket.MORNING),
            track,
        )
        val model = SharedLinUcb()
        repeat(80) { model.update(evening, 0.95) }
        assertTrue(model.predict(evening).first > model.predict(morning).first)
    }

    @Test
    fun repeatedEarlySkipSignalLowersPreference() {
        val context = PersonalizationFeatureVectorizer.vectorize(
            RecommendationContext(14, 4, false, TimeBucket.AFTERNOON),
            track,
        )
        val model = SharedLinUcb()
        val neutral = model.predict(context).first
        repeat(30) { model.update(context, -0.70) }
        assertTrue(model.predict(context).first < neutral)
    }

    @Test
    fun audioFeatureCodecRoundTripsWithoutRawAudio() {
        val values = doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6)
        val blob = AudioFeatureVectorCodec.encode(values)
        val decoded = AudioFeatureVectorCodec.decode(blob, AudioVectorFormat.FLOAT64_LE)

        assertArrayEquals(values, decoded, 0.0)
        assertEquals(values.size * Double.SIZE_BYTES, blob.size)
    }

    @Test
    fun missingAudioFeaturesRemainRankableWithExplicitMaskDifference() {
        val context = RecommendationContext(22, 5, false, TimeBucket.NIGHT)
        val withoutAudio = PersonalizationFeatureVectorizer.vectorize(context, track)
        val withAudio = PersonalizationFeatureVectorizer.vectorize(
            context,
            track.copy(audioFeatures = DoubleArray(AudioFeatureSchema.names.size) { 0.25 }),
        )

        assertEquals(PersonalizationFeatureVectorizer.FEATURE_COUNT, withoutAudio.size)
        assertEquals(PersonalizationFeatureVectorizer.FEATURE_COUNT, withAudio.size)
        assertTrue(!withoutAudio.contentEquals(withAudio))
        assertEquals(0.0, SharedLinUcb().predict(withoutAudio).first, 0.0)
    }
}
