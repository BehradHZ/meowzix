package dev.behradhz.meowzix.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.MIGRATION_1_2
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.localmedia.LocalMediaScanner
import dev.behradhz.meowzix.data.localmedia.MediaStoreScanner
import dev.behradhz.meowzix.data.repository.LocalMusicLibraryRepository
import dev.behradhz.meowzix.data.telegram.TdLibTelegramRepository
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.playback.AndroidAudioVisualizer
import dev.behradhz.meowzix.playback.AndroidPlaybackController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusicLibraryRepository(impl: LocalMusicLibraryRepository): MusicLibraryRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackCatalog(impl: LocalMusicLibraryRepository): PlaybackCatalog

    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: AndroidPlaybackController): PlaybackController

    @Binds
    @Singleton
    abstract fun bindQueueRepository(impl: AndroidPlaybackController): QueueRepository

    @Binds
    @Singleton
    abstract fun bindAudioVisualizerRepository(impl: AndroidAudioVisualizer): AudioVisualizerRepository

    @Binds
    @Singleton
    abstract fun bindTelegramRepository(impl: TdLibTelegramRepository): TelegramRepository

    @Binds
    @Singleton
    abstract fun bindLocalMediaScanner(impl: MediaStoreScanner): LocalMediaScanner
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeowzixDatabase =
        Room.databaseBuilder(context, MeowzixDatabase::class.java, "meowzix.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideLibraryDao(database: MeowzixDatabase): LibraryDao = database.libraryDao()
}
