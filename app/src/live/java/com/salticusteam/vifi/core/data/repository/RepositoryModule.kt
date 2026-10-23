package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.data.firebase.FirebaseExamUnlockService
import com.salticusteam.vifi.core.data.firebase.FirestoreArchiveRepository
import dagger.Binds
import dagger.Module
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
}
