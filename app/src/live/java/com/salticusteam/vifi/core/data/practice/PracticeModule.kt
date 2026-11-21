package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.data.repository.PracticeService
import com.salticusteam.vifi.core.data.repository.SubscriptionProofProvider
import com.salticusteam.vifi.core.model.PoolIndex
import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeError
import com.salticusteam.vifi.core.model.PracticeRequest
import com.salticusteam.vifi.core.model.SubscriptionProof
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Sınava gir" bindings of the live flavor (4.2.0). SCT-01 wires safe placeholders (iOS's environment defaults) so the
 * graph builds; Hat C replaces them: `FirestorePoolIndexReader` + `FirebasePracticeService` (PRC-01) and the Play
 * Billing proof (PRC-03, `PlayBillingSubscriptionService`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PracticeModule {

    @Binds
    @Singleton
    abstract fun bindPoolIndexReader(impl: EmptyPoolIndexReader): PoolIndexReader

    @Binds
    @Singleton
    abstract fun bindPracticeService(impl: UnavailablePracticeService): PracticeService

    @Binds
    @Singleton
    abstract fun bindSubscriptionProofProvider(impl: NoSubscriptionProofProvider): SubscriptionProofProvider
}

/** Placeholder until PRC-01: no subject has an index, so "Sınava gir" stays hidden (iOS `EmptyPracticeIndexReader`). */
class EmptyPoolIndexReader @Inject constructor() : PoolIndexReader {
    override suspend fun index(subjectId: String): PoolIndex? = null
}

/** Placeholder until PRC-01: every call fails readably (iOS `UnavailablePracticeService`). */
class UnavailablePracticeService @Inject constructor() : PracticeService {
    override suspend fun nextQuestions(request: PracticeRequest): PracticeBatch = throw PracticeError.InvalidResponse
}

/** Placeholder until PRC-03: no proof, so the server applies the free rules (never an error). */
class NoSubscriptionProofProvider @Inject constructor() : SubscriptionProofProvider {
    override suspend fun currentProof(): SubscriptionProof? = null
}
