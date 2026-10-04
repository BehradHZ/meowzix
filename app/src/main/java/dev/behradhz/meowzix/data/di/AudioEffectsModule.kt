package dev.behradhz.meowzix.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.domain.playback.EqualizerRepository
import dev.behradhz.meowzix.playback.AndroidEqualizerRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioEffectsModule {
    @Binds
    @Singleton
    abstract fun bindEqualizerRepository(impl: AndroidEqualizerRepository): EqualizerRepository
}
