package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.data.db.AudioFeatureDao
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.domain.recommendation.*
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Aggregate/redacted local report, evaluated against temporally held-out causal examples. */
@Singleton
class PersonalizationDebugReportBuilder @Inject constructor(
    private val model: PersonalizationModel,
    private val datasetBuilder: TrainingDatasetBuilder,
    private val historyDao: HistoryDao,
    private val libraryDao: LibraryDao,
    private val audioFeatureDao: AudioFeatureDao,
    private val audioFeatureExtractor: AudioFeatureExtractor,
) {
    suspend fun buildText(): String = withContext(Dispatchers.Default) {
        val state = model.state()
        val samples = datasetBuilder.buildAll(state.historyFloorVersion)
        val evaluation = RecommendationEvaluation.temporalHoldout(samples)
        val events = historyDao.recommendationWindow(
            Instant.now().minusSeconds(28L * 86_400).toEpochMilli(),
            state.historyFloorVersion,
            50_000,
        ).sortedBy { it.eventSequence }
        val playbackGroups = events.groupBy { it.playbackInstanceId }.values
            .filter { rows -> rows.any { it.type == "PLAY_STARTED" } }
        val outcomes = playbackGroups.mapNotNull { rows -> rows.lastOrNull { it.type in CausalTrainingDataset.finalTypes } }
        val tracks = libraryDao.allTracks().associateBy { it.id }
        val starts = events.filter { it.type == "PLAY_STARTED" }
        val repeatedArtist = starts.zipWithNext().count { (a, b) ->
            val artist = tracks[a.trackId]?.normalizedArtist
            !artist.isNullOrBlank() && artist == tracks[b.trackId]?.normalizedArtist
        }
        val compatibleAudioVectors = runCatching {
            audioFeatureDao.compatibleVectors(
                audioFeatureExtractor.extractorName,
                audioFeatureExtractor.extractorVersion,
                audioFeatureExtractor.schemaVersion,
            ).map { it.trackId }.distinct().size
        }.getOrDefault(0)
        val modelState = when {
            state.requiresRebuild -> "fallback-rebuild-required"
            state.active -> "learned-active"
            state.sampleCount < RecommendationConfig.COLD_START_SAMPLES -> "cold-start-heuristic"
            else -> "heuristic-fallback"
        }
        buildString {
            appendLine("Meowzix local personalization insights")
            appendLine("generatedAt=${Instant.now()}")
            appendLine("window=behavior metrics use latest 28 days, capped at 50000 events")
            appendLine("modelState=$modelState")
            appendLine("modelType=${state.modelType} modelVersion=${state.modelVersion}")
            appendLine("featureSchema=${state.featureSchemaVersion} rewardSchema=${state.rewardSchemaVersion}")
            appendLine("trainingDataVersion=${state.trainingDataVersion} samples=${state.sampleCount} active=${state.active}")
            appendLine("trainedAt=${state.trainedAtEpochMs} artifactChecksum=${state.artifactChecksum ?: "none"}")
            appendLine("audioExtractor=${audioFeatureExtractor.extractorName}/${audioFeatureExtractor.extractorVersion}")
            appendLine("audioSchema=${audioFeatureExtractor.schemaVersion} audioFeatureTracks=$compatibleAudioVectors/${tracks.size}")
            appendLine("evaluation=frozen temporal 80/20 holdout; at most 10000 latest causal samples")
            appendLine("trainSamples=${evaluation.trainCount} holdoutSamples=${evaluation.testCount}")
            appendLine("holdoutMAE=${evaluation.overall.meanAbsoluteError.format()} heuristicMAE=${evaluation.overall.heuristicMeanAbsoluteError.format()}")
            appendLine("heuristicBaseline=uncalibrated global/time affinity mapped to reward range")
            appendLine("meanPredictedReward=${evaluation.overall.meanPredictedReward.format()} meanObservedReward=${evaluation.overall.meanObservedReward.format()}")
            appendLine("pairwiseAccuracy=${evaluation.overall.pairwiseAccuracy.format()}")
            evaluation.byTimeBucket.toSortedMap().forEach { (bucket, metrics) ->
                appendLine("bucket=$bucket n=${metrics.sampleCount} MAE=${metrics.meanAbsoluteError.format()} pairwise=${metrics.pairwiseAccuracy.format()}")
            }
            outcomes.groupBy { it.playbackMode }.toSortedMap().forEach { (mode, rows) ->
                val denominator = rows.size.coerceAtLeast(1).toDouble()
                appendLine("mode=$mode outcomes=${rows.size} completionRate=${(rows.count { it.type == "PLAY_COMPLETED" } / denominator).format()}")
                appendLine("mode=$mode earlySkipRate=${(rows.count { it.type == "SKIPPED_EARLY" } / denominator).format()}")
                val finalizedIds = rows.mapTo(hashSetOf()) { it.playbackInstanceId }
                val modeEvents = events.filter { it.playbackMode == mode && it.playbackInstanceId in finalizedIds }
                appendLine("mode=$mode manualOverrideRate=${(modeEvents.filter { it.type == "QUEUE_OVERRIDDEN" }.map { it.playbackInstanceId }.distinct().size / denominator).format()}")
                appendLine("mode=$mode replayRate=${(modeEvents.filter { it.type == "REPLAYED" }.map { it.playbackInstanceId }.distinct().size / denominator).format()}")
            }
            appendLine("artistRepeatRate=${(repeatedArtist.toDouble() / (starts.size - 1).coerceAtLeast(1)).format()}")
            appendLine("trackCoverage=${(starts.map { it.trackId }.distinct().size.toDouble() / tracks.size.coerceAtLeast(1)).format()}")
            appendLine("explorationCoverage=${(starts.map { it.trackId }.distinct().size.toDouble() / tracks.size.coerceAtLeast(1)).format()}")
            appendLine("replayRate=${(events.count { it.type == "REPLAYED" }.toDouble() / starts.size.coerceAtLeast(1)).format()}")
            appendLine("limitations=observational single-user data; no unbiased off-policy or causal uplift estimate")
            appendLine("privacy=no raw audio, titles, track IDs, chat IDs, source paths, or individual events")
        }
    }
}

private fun Double?.format(): String = this?.let { String.format(Locale.US, "%.4f", it) } ?: "n/a"

@Singleton
class LocalPersonalizationMaintenance @Inject constructor(
    private val trainer: PersonalizationTrainer,
    private val audio: AudioFeatureExtractionCoordinator,
    private val report: PersonalizationDebugReportBuilder,
) : PersonalizationMaintenance {
    override suspend fun rebuild() = trainer.rebuildFromStoredHistory()
    override suspend fun analyzeAvailableAudio() = audio.scheduleBulk()
    override suspend fun debugReport(): String = report.buildText()
}
