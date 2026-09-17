package com.behradhz.meowzix.di

import android.content.Context
import androidx.room.Room
import com.behradhz.meowzix.data.db.MeowzixDatabase
import com.behradhz.meowzix.data.db.dao.LocalMediaSourceDao
import com.behradhz.meowzix.data.db.dao.TrackDao
import com.behradhz.meowzix.data.db.dao.TrackSourceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MeowzixDatabase = Room.databaseBuilder(
        context,
        MeowzixDatabase::class.java,
        "meowzix.db",
    ).build()

    @Provides
    fun provideTrackDao(database: MeowzixDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideTrackSourceDao(database: MeowzixDatabase): TrackSourceDao = database.trackSourceDao()

    @Provides
    fun provideLocalMediaSourceDao(database: MeowzixDatabase): LocalMediaSourceDao =
        database.localMediaSourceDao()
}
