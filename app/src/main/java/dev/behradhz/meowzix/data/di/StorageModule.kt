package dev.behradhz.meowzix.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.data.downloads.ManagedStorageManager
import dev.behradhz.meowzix.domain.downloads.ManagedStorageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    @Singleton
    abstract fun bindManagedStorageRepository(implementation: ManagedStorageManager): ManagedStorageRepository
}
