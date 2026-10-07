package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.data.db.AudioFeatureDao
import dev.behradhz.meowzix.data.db.LoudnessAnalysisDao
import dev.behradhz.meowzix.data.db.LoudnessAnalysisEntity
import dev.behradhz.meowzix.domain.playback.LoudnessAlgorithm
import dev.behradhz.meowzix.domain.playback.LoudnessAnalysis
import dev.behradhz.meowzix.domain.playback.LoudnessNormalizationRepository
import dev.behradhz.meowzix.domain.playback.RmsLoudnessEstimator
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureSchema
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureVectorCodec
import dev.behradhz.meowzix.domain.recommendation.AudioVectorFormat
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLoudnessNormalizationRepository @Inject constructor(
    private val loudnessDao: LoudnessAnalysisDao,
    private val audioFeatureDao: AudioFeatureDao,
    private val extractor: AudioFeatureExtractor,
    private val extractionCoordinator: AudioFeatureExtractionCoordinator,
) : LoudnessNormalizationRepository {
    override suspend fun fallbackAnalysis(trackId: UUID): LoudnessAnalysis {
        extractionCoordinator.extractIfNeeded(trackId)
        val existing = loudnessDao.latest(trackId.toString(), ANALYSIS_VERSION)
        val feature = audioFeatureDao.compatibleVector(
            trackId.toString(),
            extractor.extractorName,
            extractor.extractorVersion,
            extractor.schemaVersion,
        )
        if (existing != null && existing.algorithmVersion == RmsLoudnessEstimator.VERSION &&
            existing.sourceContentHash == feature?.sourceContentHash
        ) {
            return existing.toDomain()
        }
        if (feature == null) return NEUTRAL

        val values = runCatching {
            AudioFeatureVectorCodec.decode(feature.vectorBlob, AudioVectorFormat.valueOf(feature.vectorFormat))
        }.getOrNull()
        if (values == null || !AudioFeatureSchema.isCompatible(values)) return NEUTRAL

        val analysis = RmsLoudnessEstimator.estimate(values[AudioFeatureSchema.RMS])
        loudnessDao.deleteStaleVersions(trackId.toString(), ANALYSIS_VERSION)
        loudnessDao.put(
            LoudnessAnalysisEntity(
                id = UUID.randomUUID().toString(),
                trackId = trackId.toString(),
                sourceIdUsed = feature.sourceIdUsed,
                analysisVersion = ANALYSIS_VERSION,
                algorithm = analysis.algorithm.name,
                algorithmVersion = analysis.algorithmVersion,
                measuredValueDb = analysis.measuredDb,
                suggestedGainDb = analysis.suggestedGainDb,
                confidence = analysis.confidence,
                sourceContentHash = feature.sourceContentHash,
                sourceProvenance = "audio_feature:" + feature.extractorName + ":" + feature.extractorVersion,
                generatedAtEpochMs = Instant.now().toEpochMilli(),
            ),
        )
        return analysis
    }

    override suspend fun persistReplayGain(trackId: UUID, analysis: LoudnessAnalysis): LoudnessAnalysis {
        require(analysis.algorithm == LoudnessAlgorithm.REPLAY_GAIN_TRACK || analysis.algorithm == LoudnessAlgorithm.REPLAY_GAIN_ALBUM)
        loudnessDao.put(
            LoudnessAnalysisEntity(
                id = UUID.randomUUID().toString(),
                trackId = trackId.toString(),
                sourceIdUsed = null,
                analysisVersion = ANALYSIS_VERSION,
                algorithm = analysis.algorithm.name,
                algorithmVersion = analysis.algorithmVersion,
                measuredValueDb = analysis.measuredDb,
                suggestedGainDb = analysis.suggestedGainDb,
                confidence = analysis.confidence,
                sourceContentHash = null,
                sourceProvenance = "embedded-playback-metadata",
                generatedAtEpochMs = Instant.now().toEpochMilli(),
            ),
        )
        return analysis
    }

    private fun LoudnessAnalysisEntity.toDomain() = LoudnessAnalysis(
        measuredDb = measuredValueDb,
        suggestedGainDb = suggestedGainDb,
        algorithm = runCatching { LoudnessAlgorithm.valueOf(algorithm) }.getOrDefault(LoudnessAlgorithm.NEUTRAL),
        algorithmVersion = algorithmVersion,
        confidence = confidence.coerceIn(0f, 1f),
    )

    private companion object {
        const val ANALYSIS_VERSION = 1
        val NEUTRAL = LoudnessAnalysis(null, 0f, LoudnessAlgorithm.NEUTRAL, "neutral-v1", 0f)
    }
}
