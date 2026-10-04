package dev.behradhz.meowzix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.behradhz.meowzix.data.downloads.ManagedStorageManager
import dev.behradhz.meowzix.data.downloads.ResilientDownloadRepository
import dev.behradhz.meowzix.data.recommendation.SmartOutcomeAdaptationObserver
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import dev.behradhz.meowzix.playback.SmartQueueLiveAdapter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MeowzixApplication : Application() {
    @Inject lateinit var telegramForwardRepository: TelegramForwardRepository
    @Inject lateinit var resilientDownloadRepository: ResilientDownloadRepository
    @Inject lateinit var personalizationTrainer: dev.behradhz.meowzix.data.recommendation.PersonalizationTrainer
    @Inject lateinit var managedStorageManager: ManagedStorageManager
    @Inject lateinit var smartQueueLiveAdapter: SmartQueueLiveAdapter
    @Inject lateinit var smartOutcomeAdaptationObserver: SmartOutcomeAdaptationObserver

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        telegramForwardRepository.initialize()
        resilientDownloadRepository.initialize()
        personalizationTrainer.refreshIfStale()
        smartQueueLiveAdapter.initialize()
        smartOutcomeAdaptationObserver.initialize()
        applicationScope.launch {
            managedStorageManager.reconcileAndEnforceBudget()
        }
    }
}
