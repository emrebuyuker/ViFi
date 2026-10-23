package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.data.preferences.AnnouncementReadPreferences
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.data.preferences.fake.FakeAnnouncementReadPreferences
import com.salticusteam.vifi.core.data.preferences.fake.FakeOnboardingPreferences
import com.salticusteam.vifi.core.data.preferences.fake.FakeRecentExamsRepository
import com.salticusteam.vifi.core.data.preferences.fake.FakeUserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAnnouncementsReader
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.repository.fake.FakeAppUpdateChecker
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamSubmissionService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.data.repository.fake.FakeRewardsConfigReader
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.repository.fake.FakeWalletReader
import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
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

    /** 120 coins (one unlock) unless launched with `vifi_no_coins`, which shows the earn-coins branch. */
    /** Submissions stay on the device; nothing is written to Firebase. */
    @Provides
    @Singleton
    fun provideExamSubmissionService(): ExamSubmissionService = FakeExamSubmissionService()

    @Provides
    @Singleton
    fun provideWalletReader(): WalletReader =
        FakeWalletReader(balance = if (LaunchArgs.isSet(LaunchArg.NO_COINS)) 0 else FakeWalletReader.SAMPLE_BALANCE)

    @Provides
    @Singleton
    fun provideUnlocksReader(): UnlocksReader = FakeUnlocksReader()

    @Provides
    @Singleton
    fun provideRewardsConfigReader(): RewardsConfigReader = FakeRewardsConfigReader(
        RewardsConfig(examUploadCoins = 10, tiers = FakeRewardsConfigReader.SAMPLE_TIERS),
    )

    @Provides
    @Singleton
    fun provideUserPreferences(): UserPreferencesRepository = FakeUserPreferencesRepository()

    @Provides
    @Singleton
    fun provideRecentExams(): RecentExamsRepository = FakeRecentExamsRepository()

    @Provides
    @Singleton
    fun provideOnboardingPreferences(): OnboardingPreferences = // Mock launches skip the tour (other scenarios start on the archive) unless `vifi_show_onboarding` is set.
        FakeOnboardingPreferences(seen = !LaunchArgs.isSet(LaunchArg.SHOW_ONBOARDING))

    @Provides
    @Singleton
    fun provideAnalyticsTracker(): AnalyticsTracker = FakeAnalyticsTracker()

    @Provides
    @Singleton
    fun provideAppUpdateChecker(): AppUpdateChecker = FakeAppUpdateChecker()

    @Provides
    @Singleton
    fun provideAnnouncementsReader(): AnnouncementsReader = FakeAnnouncementsReader()

    @Provides
    @Singleton
    fun provideAnnouncementReadPreferences(): AnnouncementReadPreferences = FakeAnnouncementReadPreferences()

    /** Already allowed unless launched with `vifi_notifications_prompt`, which shows the permission prompt. */
    @Provides
    @Singleton
    fun provideNotificationService(): NotificationService = FakeNotificationService(
        initial = if (LaunchArgs.isSet(LaunchArg.NOTIFICATIONS_PROMPT)) {
            NotificationPermission.NOT_DETERMINED
        } else {
            NotificationPermission.AUTHORIZED
        },
    )

    /**
     * Seeded with a sample profile for the mock user, so the gate opens straight into the archive; empty when
     * launched with `vifi_profile_incomplete`, which shows the completion screen.
     */
    @Provides
    @Singleton
    fun provideProfileService(): ProfileService =
        if (LaunchArgs.isSet(LaunchArg.PROFILE_INCOMPLETE)) {
            FakeProfileService()
        } else {
            FakeProfileService(profiles = mapOf(FakeProfileService.SAMPLE_UID to FakeProfileService.SAMPLE_PROFILE))
        }

    /**
     * Signed in as the sample user unless launched with `vifi_signed_out` (read after MainActivity parsed it);
     * `vifi_delete_requires_reauth` makes account deletion ask for the number's code first (code 111111).
     */
    @Provides
    @Singleton
    fun provideAuthService(): AuthService = FakeAuthService(
        isSignedIn = !LaunchArgs.isSet(LaunchArg.SIGNED_OUT),
        requiresRecentLoginForDeletion = LaunchArgs.isSet(LaunchArg.DELETE_REQUIRES_REAUTH),
    )

    @Provides
    @Singleton
    fun provideLoginDiagnosticsReporter(): LoginDiagnosticsReporter = NoopLoginDiagnosticsReporter
}
