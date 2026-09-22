package dev.behradhz.meowzix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import javax.inject.Inject

@HiltAndroidApp
class MeowzixApplication : Application() {
    @Inject lateinit var downloadRepository: DownloadRepository

    override fun onCreate() {
        super.onCreate()
        // Reconciliation is entirely asynchronous. App startup must remain usable offline and must
        // never wait for Telegram/network merely because a previous download was interrupted.
        downloadRepository.resumeInterruptedDownloads()
    }
}
