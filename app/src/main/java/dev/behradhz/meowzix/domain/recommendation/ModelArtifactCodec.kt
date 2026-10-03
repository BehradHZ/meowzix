package dev.behradhz.meowzix.domain.recommendation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

data class LearnedModelArtifact(val state: PersonalizationModelState, val model: SharedLinUcb)

/** Metadata and learned parameters are one checksummed, atomically committed artifact. */
object ModelArtifactCodec {
    private const val MAGIC = 0x4d5a5543
    private const val FORMAT = 1
    private const val HASH_BYTES = 32

    fun encode(artifact: LearnedModelArtifact): ByteArray {
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(FORMAT)
                out.writeUTF(artifact.state.modelType)
                out.writeUTF(artifact.state.modelVersion)
                out.writeInt(artifact.state.featureSchemaVersion)
                out.writeInt(artifact.state.rewardSchemaVersion)
                out.writeUTF(RecommendationFeatureSchemaV2.names.joinToString(","))
                out.writeLong(artifact.state.trainingDataVersion)
                out.writeLong(artifact.state.historyFloorVersion)
                out.writeLong(artifact.state.trainedAtEpochMs)
                out.writeLong(artifact.state.sampleCount)
                out.writeBoolean(artifact.state.active)
                out.writeDouble(artifact.model.regularization)
                out.writeDouble(RecommendationConfig.ALPHA)
                out.writeInt(artifact.model.dimension)
                artifact.model.a.forEach(out::writeDouble)
                artifact.model.b.forEach(out::writeDouble)
            }
        }.toByteArray()
        return payload + digest(payload)
    }

    fun decode(bytes: ByteArray): LearnedModelArtifact {
        require(bytes.size in (HASH_BYTES + 1)..RecommendationConfig.MODEL_ARTIFACT_LIMIT_BYTES)
        val payload = bytes.copyOfRange(0, bytes.size - HASH_BYTES)
        val checksum = bytes.copyOfRange(payload.size, bytes.size)
        require(MessageDigest.isEqual(digest(payload), checksum)) { "Artifact checksum mismatch" }
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == FORMAT)
            val type = input.readUTF()
            val version = input.readUTF()
            val feature = input.readInt()
            val reward = input.readInt()
            require(type == "shared-linucb" && version == RecommendationConfig.MODEL_VERSION)
            require(feature == PersonalizationFeatureVectorizer.SCHEMA_VERSION && reward == PersonalizationRewardBuilder.VERSION)
            require(input.readUTF() == RecommendationFeatureSchemaV2.names.joinToString(","))
            val dataVersion = input.readLong()
            val floor = input.readLong()
            val trainedAt = input.readLong()
            val count = input.readLong()
            val active = input.readBoolean()
            require(count >= 0L && floor >= 0L && dataVersion >= floor && trainedAt >= 0L)
            require(active == (count >= RecommendationConfig.MIN_TRAINING_SAMPLES))
            val lambda = input.readDouble()
            require(lambda == RecommendationConfig.REGULARIZATION && input.readDouble() == RecommendationConfig.ALPHA)
            val dimension = input.readInt()
            require(dimension == PersonalizationFeatureVectorizer.FEATURE_COUNT)
            val matrix = DoubleArray(dimension * dimension) { input.readDouble() }
            val b = DoubleArray(dimension) { input.readDouble() }
            require(input.available() == 0 && b.all(Double::isFinite))
            PositiveDefiniteSolver.cholesky(matrix, dimension)
            return LearnedModelArtifact(
                PersonalizationModelState(type, version, feature, reward, dataVersion, floor, trainedAt, count, active,
                    checksum.joinToString("") { "%02x".format(it.toInt() and 0xff) }),
                SharedLinUcb(dimension, lambda, matrix, b),
            )
        }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
