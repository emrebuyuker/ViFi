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
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.data.repository.fake.FakeRewardsConfigReader
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.repository.fake.FakeWalletReader
import com.salticusteam.vifi.core.model.RewardsConfig
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
    fun provideOnboardingPreferences(): OnboardingPreferences = FakeOnboardingPreferences()

    @Provides
    @Singleton
    fun provideAnalyticsTracker(): AnalyticsTracker = FakeAnalyticsTracker()

    @Provides
    @Singleton
    fun provideAppUpdateChecker(): AppUpdateChecker = FakeAppUpdateChecker()

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

    /** Signed in as the sample user unless launched with `vifi_signed_out` (read after MainActivity parsed it). */
    @Provides
    @Singleton
    fun provideAuthService(): AuthService = FakeAuthService(isSignedIn = !LaunchArgs.isSet(LaunchArg.SIGNED_OUT))

    @Provides
    @Singleton
    fun provideLoginDiagnosticsReporter(): LoginDiagnosticsReporter = NoopLoginDiagnosticsReporter
}
