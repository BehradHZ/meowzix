package dev.behradhz.meowzix.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackInfrastructureModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackMonotonicClock(
        implementation: AndroidPlaybackMonotonicClock,
    ): PlaybackMonotonicClock
}
