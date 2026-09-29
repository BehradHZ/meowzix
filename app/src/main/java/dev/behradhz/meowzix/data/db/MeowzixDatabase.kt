package dev.behradhz.meowzix.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackEntity::class,
        TrackSourceEntity::class,
        LocalMediaSourceEntity::class,
        TelegramTrackSourceEntity::class,
        TelegramSelectedSourceEntity::class,
        TelegramSendJobEntity::class,
        DownloadRecordEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        ListeningSessionEntity::class,
        ListeningEventEntity::class,
        TrackPreferenceStatsEntity::class,
        TrackTimePreferenceEntity::class,
        AudioFeatureVectorEntity::class,
        TrackSearchFtsEntity::class,
    ],
    version = 12,
    exportSchema = true,
)
@TypeConverters(DbConverters::class)
abstract class MeowzixDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun libraryBrowseDao(): LibraryBrowseDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun telegramDao(): TelegramDao
    abstract fun telegramSendDao(): TelegramSendDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun audioFeatureDao(): AudioFeatureDao
}
