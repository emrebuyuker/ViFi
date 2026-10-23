package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.data.preferences.fake.FakeOnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.fake.FakeUserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAppUpdateChecker
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
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

    @Provides
    @Singleton
    fun provideUserPreferences(): UserPreferencesRepository = FakeUserPreferencesRepository()

    @Provides
    @Singleton
    fun provideRecentExams(): RecentExamsRepository = FakeRecentExamsRepository()

    @Provides
    @Singleton
    fun provideOnboardingPreferences(): OnboardingPreferences = FakeOnboardingPreferences()

    @Provides
    @Singleton
    fun provideAnalyticsTracker(): AnalyticsTracker = FakeAnalyticsTracker()

    @Provides
    @Singleton
    fun provideAppUpdateChecker(): AppUpdateChecker = FakeAppUpdateChecker()
}
