package com.behradhz.meowzix.di

import com.behradhz.meowzix.data.localmedia.LocalMusicScanner
import com.behradhz.meowzix.data.localmedia.MediaStoreLocalMusicScanner
import com.behradhz.meowzix.data.repository.DefaultMusicLibraryRepository
import com.behradhz.meowzix.domain.library.MusicLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindLocalMusicScanner(implementation: MediaStoreLocalMusicScanner): LocalMusicScanner

    @Binds
    @Singleton
    abstract fun bindMusicLibraryRepository(
        implementation: DefaultMusicLibraryRepository,
    ): MusicLibraryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object LibraryRuntimeModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
