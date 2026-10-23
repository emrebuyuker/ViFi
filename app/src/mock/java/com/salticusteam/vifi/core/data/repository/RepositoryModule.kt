package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Mock flavor bindings (iOS `AppEnvironment.mock()` parity): sample data only, no Firebase types in the graph.
 * Add a fake here whenever the live RepositoryModule gains a binding, or the mock build stops compiling.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideArchiveRepository(): ArchiveRepository = FakeArchiveRepository()

    @Provides
    @Singleton
    fun provideExamUnlockService(): ExamUnlockService = FakeExamUnlockService()

    /** Seeded with a sample profile for the mock user, so the gate opens straight into the archive. */
    @Provides
    @Singleton
    fun provideProfileService(): ProfileService =
        FakeProfileService(profiles = mapOf(FakeProfileService.SAMPLE_UID to FakeProfileService.SAMPLE_PROFILE))
}
