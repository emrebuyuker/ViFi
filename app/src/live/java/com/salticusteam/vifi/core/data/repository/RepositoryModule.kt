package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.data.firebase.FirebaseAuthService
import com.salticusteam.vifi.core.data.firebase.FirebaseExamUnlockService
import com.salticusteam.vifi.core.data.firebase.FirebaseLoginDiagnosticsReporter
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
    abstract fun bindAuthService(impl: FirebaseAuthService): AuthService

    @Binds
    @Singleton
    abstract fun bindLoginDiagnosticsReporter(impl: FirebaseLoginDiagnosticsReporter): LoginDiagnosticsReporter

    companion object {
        @Provides
        @Singleton
        fun provideArchiveRepository(): ArchiveRepository = FakeArchiveRepository()
    }
}
