package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.data.repository.SubscriptionService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Billing bindings of the live flavor: unavailable until Google Play Billing lands (4.0.0 BIL-02…BIL-05). */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionService(impl: UnavailableSubscriptionService): SubscriptionService

    @Binds
    @Singleton
    abstract fun bindExamPurchaseService(impl: UnavailableExamPurchaseService): ExamPurchaseService
}
