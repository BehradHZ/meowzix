package dev.behradhz.meowzix.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.data.db.LibraryBrowseDao
import dev.behradhz.meowzix.data.db.MeowzixDatabase

@Module
@InstallIn(SingletonComponent::class)
object LibraryPerformanceModule {
    @Provides
    fun provideLibraryBrowseDao(database: MeowzixDatabase): LibraryBrowseDao =
        database.libraryBrowseDao()
}
