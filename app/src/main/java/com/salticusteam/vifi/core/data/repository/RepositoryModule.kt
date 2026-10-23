package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository bindings (iOS `AppEnvironment.live()` parity).
 *
 * TEMPORARY: ArchiveRepository is bound to the sample-data fake until the live Firestore implementation
 * lands in the archive area (BE-15 / ARCH). No legacy screen uses it, so nothing user-facing reads fake data.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideArchiveRepository(): ArchiveRepository = FakeArchiveRepository()
}
