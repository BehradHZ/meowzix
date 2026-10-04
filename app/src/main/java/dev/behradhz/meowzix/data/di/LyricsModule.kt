package dev.behradhz.meowzix.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.data.lyrics.RoomLyricsRepository
import dev.behradhz.meowzix.domain.lyrics.LyricsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsModule {
    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: RoomLyricsRepository): LyricsRepository
}
