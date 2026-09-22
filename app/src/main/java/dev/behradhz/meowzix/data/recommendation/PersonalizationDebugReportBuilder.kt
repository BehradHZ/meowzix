package dev.behradhz.meowzix.data.recommendation

import dev.behradhz.meowzix.data.db.AudioFeatureDao
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.domain.recommendation.AudioFeatureExtractor
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.PersonalizationRewardBuilder
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Builds a redacted, text-only local evaluation report. No raw audio, paths, chat IDs, or titles. */
@Singleton
class PersonalizationDebugReportBuilder @Inject constructor(
    private val model: PersonalizationModel,
    private val datasetBuilder: TrainingDatasetBuilder,
    private val historyDao: HistoryDao,
    private val libraryDao: LibraryDao,
    private val audioFeatureDao: AudioFeatureDao,
    private val extractor: AudioFeatureExtractor,
) {
    suspend fun buildText(): String {
        val state = model.state()
        val samples = datasetBuilder.buildAll()
        val events = historyDao.allEventsChronological()
        val tracks = libraryDao.allTracks()
        val audioVectors = audioFeatureDao.compatibleVectors(
            extractor.extractorName,
            extractor.extractorVersion,
            extractor.schemaVersion,
        )

        val outcomes = events.groupBy { it.playbackInstanceId }.values.mapNotNull { playbackEvents ->
            val outcome = playbackEvents.lastOrNull { it.type in FINAL_OUTCOMES } ?: return@mapNotNull null
            Outcome(
                type = outcome.type,
                timeBucket = outcome.timeBucket,
                reward = PersonalizationRewardBuilder.reward(playbackEvents.mapTo(linkedSetOf()) { it.type }),
            )
        }
        val predictions = if (state.active && samples.isNotEmpty()) {
            val evaluationInputs = samples.mapIndexed { index, sample ->
                evaluationKey(index, sample.trackId, sample.dataVersion) to sample.features
            }.toMap()
            val scores = runCatching { model.scoreBatch(evaluationInputs) }.getOrDefault(emptyMap())
            samples.mapIndexedNotNull { index, sample ->
                val key = evaluationKey(index, sample.trackId, sample.dataVersion)
                scores[key]?.let { score -> Prediction(sample.reward, score * 2.0 - 1.0) }
            }
        } else {
            emptyList()
        }

        val mae = predictions.takeIf { it.isNotEmpty() }?.map { abs(it.observed - it.predicted) }?.average()
        val pairwise = pairwiseAccuracy(predictions.take(MAX_PAIRWISE_SAMPLES))
        val completed = outcomes.count { it.type == "PLAY_COMPLETED" }
        val early = outcomes.count { it.type == "SKIPPED_EARLY" }
        val late = outcomes.count { it.type == "SKIPPED_LATE" }
        val stopped = outcomes.count { it.type == "PLAY_STOPPED" }
        val denominator = outcomes.size.coerceAtLeast(1).toDouble()

        return buildString {
            appendLine("Meowzix personalization debug report")
            appendLine("generatedAt=${Instant.now()}")
            appendLine("modelType=${state.modelType}")
            appendLine("modelVersion=${state.modelVersion}")
            appendLine("featureSchemaVersion=${state.featureSchemaVersion}")
            appendLine("modelActive=${state.active}")
            appendLine("modelSampleCount=${state.sampleCount}")
            appendLine("trainingDataVersion=${state.trainingDataVersion}")
            appendLine("trainedAt=${state.trainedAtEpochMs.takeIf { it > 0L }?.let(Instant::ofEpochMilli) ?: "never"}")
            appendLine("extractor=${extractor.extractorName}/${extractor.extractorVersion}")
            appendLine("extractorSchemaVersion=${extractor.schemaVersion}")
            appendLine("canonicalTrackCount=${tracks.size}")
            appendLine("audioFeatureTrackCount=${audioVectors.map { it.trackId }.distinct().size}")
            appendLine("datasetSampleCount=${samples.size}")
            appendLine("meanObservedReward=${samples.map { it.reward }.averageOrZero().format4()}")
            appendLine("positiveSamples=${samples.count { it.reward > 0.0 }}")
            appendLine("negativeSamples=${samples.count { it.reward < 0.0 }}")
            appendLine("neutralSamples=${samples.count { it.reward == 0.0 }}")
            appendLine("finalizedOutcomes=${outcomes.size}")
            appendLine("completionRate=${(completed / denominator).format4()}")
            appendLine("earlySkipRate=${(early / denominator).format4()}")
            appendLine("lateSkipRate=${(late / denominator).format4()}")
            appendLine("stoppedRate=${(stopped / denominator).format4()}")
            appendLine("predictionMae=${mae?.format4() ?: "n/a"}")
            appendLine("pairwiseRankingAccuracy=${pairwise?.format4() ?: "n/a"}")
            appendLine("timeBuckets:")
            outcomes.groupBy { it.timeBucket }.toSortedMap().forEach { (bucket, bucketOutcomes) ->
                appendLine(
                    "  $bucket count=${bucketOutcomes.size} meanReward=${bucketOutcomes.map { it.reward }.averageOrZero().format4()} " +
                        "earlySkips=${bucketOutcomes.count { it.type == "SKIPPED_EARLY" }} " +
                        "completions=${bucketOutcomes.count { it.type == "PLAY_COMPLETED" }}",
                )
            }
            appendLine("privacy=redacted:no_raw_audio,no_paths,no_track_titles,no_telegram_identifiers")
        }
    }

    private fun pairwiseAccuracy(predictions: List<Prediction>): Double? {
        var comparable = 0L
        var correct = 0L
        for (left in predictions.indices) {
            for (right in left + 1 until predictions.size) {
                val observedDelta = predictions[left].observed - predictions[right].observed
                if (abs(observedDelta) < 1e-9) continue
                val predictedDelta = predictions[left].predicted - predictions[right].predicted
                comparable++
                if (observedDelta * predictedDelta > 0.0) correct++
            }
        }
        return if (comparable == 0L) null else correct.toDouble() / comparable
    }

    private fun evaluationKey(index: Int, trackId: UUID, dataVersion: Long): UUID =
        UUID.nameUUIDFromBytes("eval:$index:$trackId:$dataVersion".toByteArray())

    private data class Outcome(val type: String, val timeBucket: String, val reward: Double)
    private data class Prediction(val observed: Double, val predicted: Double)

    private companion object {
        val FINAL_OUTCOMES = setOf("PLAY_COMPLETED", "PLAY_STOPPED", "SKIPPED_EARLY", "SKIPPED_LATE")
        const val MAX_PAIRWISE_SAMPLES = 200
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
private fun Double.format4(): String = String.format(Locale.US, "%.4f", this)
