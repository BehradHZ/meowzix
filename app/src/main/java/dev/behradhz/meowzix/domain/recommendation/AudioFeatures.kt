package dev.behradhz.meowzix.domain.recommendation

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID

enum class AudioVectorFormat { FLOAT64_LE }

data class AudioFeatureSource(
    val sourceId: UUID,
    val contentUri: String?,
    val localPath: String?,
    val mimeType: String?,
    val fileSizeBytes: Long?,
    val contentHashSha256: String?,
)

data class AudioFeatureVector(
    val id: UUID,
    val trackId: UUID,
    val sourceIdUsed: UUID,
    val extractorName: String,
    val extractorVersion: String,
    val schemaVersion: Int,
    val vectorFormat: AudioVectorFormat,
    val values: DoubleArray,
    val generatedAt: Instant,
    val sourceContentHash: String?,
)

interface AudioFeatureExtractor {
    val extractorName: String
    val extractorVersion: String
    val schemaVersion: Int

    /**
     * Returns a source-independent feature vector, or null when this readable source cannot be
     * decoded safely. Implementations must do all decoding/analysis away from the main thread.
     */
    suspend fun extract(trackId: UUID, source: AudioFeatureSource): AudioFeatureVector?
}

object AudioFeatureVectorCodec {
    fun encode(values: DoubleArray, format: AudioVectorFormat = AudioVectorFormat.FLOAT64_LE): ByteArray = when (format) {
        AudioVectorFormat.FLOAT64_LE -> ByteBuffer.allocate(values.size * Double.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .also { buffer -> values.forEach(buffer::putDouble) }
            .array()
    }

    fun decode(blob: ByteArray, format: AudioVectorFormat): DoubleArray = when (format) {
        AudioVectorFormat.FLOAT64_LE -> {
            require(blob.size % Double.SIZE_BYTES == 0) { "Corrupt audio feature vector length." }
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            DoubleArray(blob.size / Double.SIZE_BYTES) { buffer.double }
        }
    }
}
