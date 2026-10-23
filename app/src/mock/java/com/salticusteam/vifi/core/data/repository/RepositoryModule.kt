package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
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

    /** Signed in as the sample user unless launched with `vifi_signed_out` (read after MainActivity parsed it). */
    @Provides
    @Singleton
    fun provideAuthService(): AuthService = FakeAuthService(isSignedIn = !LaunchArgs.isSet(LaunchArg.SIGNED_OUT))

    @Provides
    @Singleton
    fun provideLoginDiagnosticsReporter(): LoginDiagnosticsReporter = NoopLoginDiagnosticsReporter
}
