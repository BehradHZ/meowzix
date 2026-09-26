package dev.behradhz.meowzix.data.telegram

import android.content.Context
import androidx.hilt.EntryPoint
import androidx.hilt.InstallIn
import androidx.hilt.android.EntryPointAccessors
import androidx.hilt.components.SingletonComponent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
