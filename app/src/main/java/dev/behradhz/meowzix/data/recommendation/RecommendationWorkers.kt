package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.domain.recommendation.TrainingScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Singleton
class RecommendationWorkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TrainingScheduler {
    override fun scheduleTraining(rebuild: Boolean) {
        val request = OneTimeWorkRequestBuilder<PersonalizationTrainingWorker>()
            .setInputData(workDataOf("rebuild" to rebuild))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag("personalization").build()
        // Append avoids losing an outcome that arrives while a previous worker is finishing.
        WorkManager.getInstance(context).enqueueUniqueWork("personalization-training", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun scheduleAudio(trackIds: Collection<UUID>, bulk: Boolean = false) {
        trackIds.distinct().chunked(100).forEach { ids ->
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true)
            if (bulk) constraints.setRequiresCharging(true).setRequiresDeviceIdle(true)
            val request = OneTimeWorkRequestBuilder<AudioFeatureExtractionWorker>()
                .setInputData(workDataOf("tracks" to ids.map(UUID::toString).toTypedArray()))
                .setConstraints(constraints.build()).addTag("audio-features").build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "audio-features-${ids.map(UUID::toString).sorted().joinToString().hashCode()}", ExistingWorkPolicy.KEEP, request)
        }
    }
}

class PersonalizationTrainingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, RecommendationWorkerEntryPoint::class.java)
            .trainer().trainPending(inputData.getBoolean("rebuild", false))
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

class AudioFeatureExtractionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val coordinator = EntryPointAccessors.fromApplication(applicationContext, RecommendationWorkerEntryPoint::class.java).audioCoordinator()
        for (id in inputData.getStringArray("tracks").orEmpty()) {
            currentCoroutineContext().ensureActive()
            coordinator.extractIfNeeded(UUID.fromString(id))
        }
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecommendationWorkerEntryPoint {
    fun trainer(): PersonalizationTrainer
    fun audioCoordinator(): AudioFeatureExtractionCoordinator
}
