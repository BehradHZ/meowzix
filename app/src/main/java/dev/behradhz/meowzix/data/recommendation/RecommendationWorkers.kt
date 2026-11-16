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

    // Acoustic extraction is intentionally paused until recommendation work resumes.
    @Suppress("UNUSED_PARAMETER")
    fun scheduleAudio(trackIds: Collection<UUID>, bulk: Boolean = false) = Unit

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

// Previously queued audio tasks now finish without reading or decoding audio files.
class AudioFeatureExtractionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecommendationWorkerEntryPoint {
    fun trainer(): PersonalizationTrainer
}
