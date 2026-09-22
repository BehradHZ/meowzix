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
import dev.behradhz.meowzix.data.db.MIGRATION_2_3
import dev.behradhz.meowzix.data.db.MIGRATION_3_4
import dev.behradhz.meowzix.data.db.MIGRATION_4_5
import dev.behradhz.meowzix.data.db.MIGRATION_5_6
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.DownloadDao
import dev.behradhz.meowzix.data.db.PlaylistDao
import dev.behradhz.meowzix.data.db.HistoryDao
import dev.behradhz.meowzix.data.downloads.TdLibDownloadRepository
import dev.behradhz.meowzix.data.localmedia.LocalMediaScanner
import dev.behradhz.meowzix.data.localmedia.MediaStoreScanner
import dev.behradhz.meowzix.data.repository.LocalMusicLibraryRepository
import dev.behradhz.meowzix.data.repository.RoomPlaylistRepository
import dev.behradhz.meowzix.data.history.RoomListeningHistoryRepository
import dev.behradhz.meowzix.data.recommendation.HeuristicRecommendationEngine
import dev.behradhz.meowzix.data.recommendation.LocalLinearPersonalizationModel
import dev.behradhz.meowzix.data.settings.DataStoreSettingsRepository
import dev.behradhz.meowzix.data.telegram.TdLibRemoteTrackPlaybackResolver
import dev.behradhz.meowzix.data.telegram.TdLibTelegramForwardRepository
import dev.behradhz.meowzix.data.telegram.TdLibTelegramRepository
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.library.PlaylistRepository
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.playback.AudioVisualizerRepository
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.playback.RemoteTrackPlaybackResolver
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.history.ListeningHistoryRepository
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import dev.behradhz.meowzix.playback.AndroidAudioVisualizer
import dev.behradhz.meowzix.playback.ResolvingPlaybackController
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
    abstract fun bindPlaybackController(impl: ResolvingPlaybackController): PlaybackController

    @Binds
    @Singleton
    abstract fun bindQueueRepository(impl: ResolvingPlaybackController): QueueRepository

    @Binds
    @Singleton
    abstract fun bindAudioVisualizerRepository(impl: AndroidAudioVisualizer): AudioVisualizerRepository

    @Binds
    @Singleton
    abstract fun bindTelegramRepository(impl: TdLibTelegramRepository): TelegramRepository

    @Binds
    @Singleton
    abstract fun bindTelegramForwardRepository(impl: TdLibTelegramForwardRepository): TelegramForwardRepository

    @Binds
    @Singleton
    abstract fun bindRemoteTrackPlaybackResolver(impl: TdLibRemoteTrackPlaybackResolver): RemoteTrackPlaybackResolver

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: TdLibDownloadRepository): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: RoomPlaylistRepository): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindListeningHistoryRepository(impl: RoomListeningHistoryRepository): ListeningHistoryRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationEngine(impl: HeuristicRecommendationEngine): RecommendationEngine

    @Binds
    @Singleton
    abstract fun bindPersonalizationModel(impl: LocalLinearPersonalizationModel): PersonalizationModel

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideLibraryDao(database: MeowzixDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideTelegramDao(database: MeowzixDatabase): TelegramDao = database.telegramDao()

    @Provides
    fun provideDownloadDao(database: MeowzixDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun providePlaylistDao(database: MeowzixDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideHistoryDao(database: MeowzixDatabase): HistoryDao = database.historyDao()
}
