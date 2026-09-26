package dev.behradhz.meowzix.data.telegram

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository

class TelegramSendQueueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            TelegramSendWorkerEntryPoint::class.java,
        ).telegramForwardRepository()

        return runCatching {
            repository.processPendingSends()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TelegramSendWorkerEntryPoint {
    fun telegramForwardRepository(): TelegramForwardRepository
}
