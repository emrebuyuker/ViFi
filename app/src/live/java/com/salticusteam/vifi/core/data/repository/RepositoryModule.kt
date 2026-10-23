package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.core.data.firebase.FirebaseExamUnlockService
import com.salticusteam.vifi.core.data.firebase.RtdbAppConfigReader
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
    abstract fun bindAppConfigReader(impl: RtdbAppConfigReader): AppConfigReader

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
