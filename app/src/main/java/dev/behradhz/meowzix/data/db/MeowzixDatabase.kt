package dev.behradhz.meowzix.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TrackEntity::class, TrackSourceEntity::class, LocalMediaSourceEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DbConverters::class)
abstract class MeowzixDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
