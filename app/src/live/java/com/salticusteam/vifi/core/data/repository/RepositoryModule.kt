package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.core.data.firebase.FirebaseAnalyticsTracker
import com.salticusteam.vifi.core.data.firebase.FirebaseExamUnlockService
import com.salticusteam.vifi.core.data.firebase.FirebaseProfileService
import com.salticusteam.vifi.core.data.firebase.FirestoreArchiveRepository
import com.salticusteam.vifi.core.data.firebase.RtdbAppConfigReader
import com.salticusteam.vifi.core.data.preferences.DataStoreOnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.DataStoreRecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.DataStoreUserPreferencesRepository
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Repository bindings (iOS `AppEnvironment.live()` parity). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExamUnlockService(impl: FirebaseExamUnlockService): ExamUnlockService

    /** The archive reads the named 'vifi' Firestore database (BE-15); the RTDB `Universitiess` tree is unused. */
    @Binds
    @Singleton
    abstract fun bindArchiveRepository(impl: FirestoreArchiveRepository): ArchiveRepository

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
    }
}
