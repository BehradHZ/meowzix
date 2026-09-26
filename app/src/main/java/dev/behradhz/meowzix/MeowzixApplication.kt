package dev.behradhz.meowzix

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import javax.inject.Inject

@HiltAndroidApp
class MeowzixApplication : Application() {
    @Inject lateinit var telegramForwardRepository: TelegramForwardRepository

    override fun onCreate() {
        super.onCreate()
        telegramForwardRepository.initialize()
    }
}
