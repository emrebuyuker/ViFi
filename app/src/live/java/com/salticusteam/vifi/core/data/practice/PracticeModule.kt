package com.salticusteam.vifi.core.data.practice

import com.google.firebase.firestore.FirebaseFirestore
import com.salticusteam.vifi.core.data.billing.PlaySubscriptionProofProvider
import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.data.repository.PracticeService
import com.salticusteam.vifi.core.data.repository.SubscriptionProofProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * "Sınava gir" bindings of the live flavor (4.2.0): the pool index from the named 'vifi' Firestore (cached for the
 * session), questions from the `nextPracticeQuestions` callable, and the Play subscription as the Premium proof.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PracticeModule {

    @Binds
    @Singleton
    abstract fun bindPracticeService(impl: FirebasePracticeService): PracticeService

    @Binds
    @Singleton
    abstract fun bindSubscriptionProofProvider(impl: PlaySubscriptionProofProvider): SubscriptionProofProvider

    companion object {
        @Provides
        @Singleton
        fun providePoolIndexReader(firestore: FirebaseFirestore): PoolIndexReader =
            CachingPoolIndexReader(FirestorePoolIndexReader(firestore))
    }
}
