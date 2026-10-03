package dev.behradhz.meowzix.domain.recommendation

import org.junit.Assert.*
import org.junit.Test

class ModelArtifactCodecTest {
    private fun artifact() = LearnedModelArtifact(PersonalizationModelState(trainingDataVersion = 90, sampleCount = 90, active = true), SharedLinUcb())
    @Test fun roundTripPreservesMatrixAndMetadata() {
        val original = artifact()
        val x = DoubleArray(original.model.dimension) { 0.5 }
        original.model.update(x, 0.8)
        val loaded = ModelArtifactCodec.decode(ModelArtifactCodec.encode(original))
        assertArrayEquals(original.model.a, loaded.model.a, 0.0)
        assertArrayEquals(original.model.b, loaded.model.b, 0.0)
        assertEquals(original.model.predict(x).first, loaded.model.predict(x).first, 1e-12)
        assertEquals(64, loaded.state.artifactChecksum!!.length)
    }
    @Test(expected = IllegalArgumentException::class) fun changedPayloadFailsChecksum() {
        val bytes = ModelArtifactCodec.encode(artifact())
        bytes[30] = (bytes[30].toInt() xor 1).toByte()
        ModelArtifactCodec.decode(bytes)
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedArtifactIsRejected() {
        ModelArtifactCodec.decode(ModelArtifactCodec.encode(artifact()).copyOf(30))
    }
    @Test(expected = IllegalArgumentException::class) fun incompatibleFeatureSchemaIsRejected() {
        val a = artifact()
        ModelArtifactCodec.decode(ModelArtifactCodec.encode(a.copy(state = a.state.copy(featureSchemaVersion = 999))))
    }
    @Test(expected = IllegalArgumentException::class) fun incompatibleRewardSchemaIsRejected() {
        val a = artifact()
        ModelArtifactCodec.decode(ModelArtifactCodec.encode(a.copy(state = a.state.copy(rewardSchemaVersion = 999))))
    }
    @Test(expected = IllegalArgumentException::class) fun incompatibleModelVersionIsRejected() {
        val a = artifact()
        ModelArtifactCodec.decode(ModelArtifactCodec.encode(a.copy(state = a.state.copy(modelVersion = "999"))))
    }
    @Test(expected = IllegalArgumentException::class) fun nonSpdArtifactIsRejectedEvenWithValidChecksum() {
        val a = artifact()
        a.model.a[0] = -1.0
        ModelArtifactCodec.decode(ModelArtifactCodec.encode(a))
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteCoefficientsAreRejected() {
        val a = artifact()
        a.model.b[0] = Double.NaN
        ModelArtifactCodec.decode(ModelArtifactCodec.encode(a))
    }
}
