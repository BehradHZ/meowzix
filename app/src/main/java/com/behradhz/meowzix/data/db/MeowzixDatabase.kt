package com.behradhz.meowzix.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.behradhz.meowzix.data.db.dao.LocalMediaSourceDao
import com.behradhz.meowzix.data.db.dao.TrackDao
import com.behradhz.meowzix.data.db.dao.TrackSourceDao
import com.behradhz.meowzix.data.db.entity.LocalMediaSourceEntity
import com.behradhz.meowzix.data.db.entity.TrackEntity
import com.behradhz.meowzix.data.db.entity.TrackSourceEntity

@Database(
    entities = [
        TrackEntity::class,
        TrackSourceEntity::class,
        LocalMediaSourceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class MeowzixDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun trackSourceDao(): TrackSourceDao
    abstract fun localMediaSourceDao(): LocalMediaSourceDao
}
