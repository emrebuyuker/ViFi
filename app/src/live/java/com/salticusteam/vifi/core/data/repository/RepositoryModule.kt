package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.core.data.firebase.FirebaseAnalyticsTracker
import com.salticusteam.vifi.core.data.firebase.FirebaseAuthService
import com.salticusteam.vifi.core.data.firebase.FirebaseExamSubmissionService
import com.salticusteam.vifi.core.data.firebase.FirebaseExamUnlockService
import com.salticusteam.vifi.core.data.firebase.FirebaseLoginDiagnosticsReporter
import com.salticusteam.vifi.core.data.firebase.FirebaseModerationBackend
import com.salticusteam.vifi.core.data.firebase.FirebaseNotificationService
import com.salticusteam.vifi.core.data.firebase.FirebaseProfileService
import com.salticusteam.vifi.core.data.firebase.FirestoreDeviceTokenStore
import com.salticusteam.vifi.core.data.firebase.FirestoreAnnouncementsReader
import com.salticusteam.vifi.core.data.firebase.FirestoreArchiveRepository
import com.salticusteam.vifi.core.data.firebase.FirestoreUnlocksReader
import com.salticusteam.vifi.core.data.firebase.FirestoreWalletReader
import com.salticusteam.vifi.core.data.firebase.RtdbAppConfigReader
import com.salticusteam.vifi.core.data.firebase.RtdbRewardsConfigReader
import com.salticusteam.vifi.core.data.moderation.AdminModerationService
import com.salticusteam.vifi.core.data.moderation.ModerationBackend
import com.salticusteam.vifi.core.data.preferences.AnnouncementReadPreferences
import com.salticusteam.vifi.core.data.preferences.DataStoreAnnouncementReadPreferences
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

    /** Uploads to Storage `pending/<uid>/<id>/` and creates Firestore `submissions/<id>` (BE-14). */
    @Binds
    @Singleton
    abstract fun bindExamSubmissionService(impl: FirebaseExamSubmissionService): ExamSubmissionService

    /** Serverless admin moderation: the admin's device publishes and credits, gated by the rules' isAdmin(). */
    @Binds
    @Singleton
    abstract fun bindModerationService(impl: AdminModerationService): ModerationService

    @Binds
    @Singleton
    abstract fun bindModerationBackend(impl: FirebaseModerationBackend): ModerationBackend

    @Binds
    @Singleton
    abstract fun bindWalletReader(impl: FirestoreWalletReader): WalletReader

    @Binds
    @Singleton
    abstract fun bindUnlocksReader(impl: FirestoreUnlocksReader): UnlocksReader

    /** `config/rewards/android`, falling back to the iOS node (BE-07). */
    @Binds
    @Singleton
    abstract fun bindRewardsConfigReader(impl: RtdbRewardsConfigReader): RewardsConfigReader

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

    @Binds
    @Singleton
    abstract fun bindAuthService(impl: FirebaseAuthService): AuthService

    @Binds
    @Singleton
    abstract fun bindLoginDiagnosticsReporter(impl: FirebaseLoginDiagnosticsReporter): LoginDiagnosticsReporter

    @Binds
    @Singleton
    abstract fun bindAnnouncementsReader(impl: FirestoreAnnouncementsReader): AnnouncementsReader

    @Binds
    @Singleton
    abstract fun bindAnnouncementReadPreferences(impl: DataStoreAnnouncementReadPreferences): AnnouncementReadPreferences

    @Binds
    @Singleton
    abstract fun bindNotificationService(impl: FirebaseNotificationService): NotificationService

    /** `users/<uid>/devices/<token>` in the named 'vifi' database (N11). */
    @Binds
    @Singleton
    abstract fun bindDeviceTokenStore(impl: FirestoreDeviceTokenStore): DeviceTokenStore

    companion object {
        @Provides
        @Singleton
        fun provideAppUpdateChecker(reader: AppConfigReader): AppUpdateChecker =
            RemoteAppUpdateChecker(reader, installedVersion = BuildConfig.VERSION_NAME)
    }
}
