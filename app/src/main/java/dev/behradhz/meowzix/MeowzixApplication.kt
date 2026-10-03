package dev.behradhz.meowzix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.behradhz.meowzix.data.downloads.ResilientDownloadRepository
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import javax.inject.Inject

@HiltAndroidApp
class MeowzixApplication : Application() {
    @Inject lateinit var telegramForwardRepository: TelegramForwardRepository
    @Inject lateinit var resilientDownloadRepository: ResilientDownloadRepository
    @Inject lateinit var personalizationTrainer: dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer

    override fun onCreate() {
        super.onCreate()
        telegramForwardRepository.initialize()
        resilientDownloadRepository.initialize()
        personalizationTrainer.refreshIfStale()
    }
}
