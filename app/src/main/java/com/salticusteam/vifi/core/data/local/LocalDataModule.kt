package com.salticusteam.vifi.core.data.local

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Device-local cleanup; the same file-system implementation serves both flavors (it touches no Firebase). */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalDataModule {

    @Binds
    @Singleton
    abstract fun bindLocalDataCleaner(impl: FileSystemLocalDataCleaner): LocalDataCleaner
}
