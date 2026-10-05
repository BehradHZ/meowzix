package dev.behradhz.meowzix.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.data.backup.RoomLocalBackupRepository
import dev.behradhz.meowzix.domain.backup.LocalBackupRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    @Singleton
    abstract fun bindLocalBackupRepository(impl: RoomLocalBackupRepository): LocalBackupRepository
}
