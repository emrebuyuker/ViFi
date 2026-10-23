package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.core.data.firebase.FirebaseAnalyticsTracker
import com.salticusteam.vifi.core.data.firebase.FirebaseExamUnlockService
import com.salticusteam.vifi.core.data.firebase.FirebaseProfileService
import com.salticusteam.vifi.core.data.firebase.RtdbAppConfigReader
import com.salticusteam.vifi.core.data.preferences.DataStoreOnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.DataStoreRecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.DataStoreUserPreferencesRepository
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository bindings (iOS `AppEnvironment.live()` parity).
 *
 * TEMPORARY: ArchiveRepository is bound to the sample-data fake until the live Firestore implementation
 * lands in the archive area (BE-15 / ARCH), so the live build shows sample data until then. The app has no
 * users and is not released, which is why this is acceptable for now.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExamUnlockService(impl: FirebaseExamUnlockService): ExamUnlockService

    @Binds
    @Singleton
    abstract fun bindUserPreferences(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindRecentExams(impl: DataStoreRecentExamsRepository): RecentExamsRepository

    @Binds
    @Singleton
    abstract fun bindOnboardingPreferences(impl: DataStoreOnboardingPreferences): OnboardingPreferences

    @Binds
    @Singleton
    abstract fun bindAnalyticsTracker(impl: FirebaseAnalyticsTracker): AnalyticsTracker

    @Binds
    @Singleton
    abstract fun bindAppConfigReader(impl: RtdbAppConfigReader): AppConfigReader

    @Binds
    @Singleton
    abstract fun bindProfileService(impl: FirebaseProfileService): ProfileService

    companion object {
        @Provides
        @Singleton
        fun provideAppUpdateChecker(reader: AppConfigReader): AppUpdateChecker =
            RemoteAppUpdateChecker(reader, installedVersion = BuildConfig.VERSION_NAME)

        @Provides
        @Singleton
        fun provideArchiveRepository(): ArchiveRepository = FakeArchiveRepository()
    }
}
