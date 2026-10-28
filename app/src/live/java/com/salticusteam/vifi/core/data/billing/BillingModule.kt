package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.data.repository.SubscriptionService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Billing bindings of the live flavor: Google Play Billing (4.0.0 BIL-02…BIL-05). Premium is read from the device's
 * Play purchases; a paid exam unlock is verified and recorded by the shared `purchaseExamUnlock` callable.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindPlayBillingClient(impl: BillingClientConnection): PlayBillingClient

    @Binds
    @Singleton
    abstract fun bindSubscriptionService(impl: PlayBillingSubscriptionService): SubscriptionService

    @Binds
    @Singleton
    abstract fun bindExamPurchaseService(impl: PlayBillingExamPurchaseService): ExamPurchaseService

    @Binds
    abstract fun bindExamPurchaseBackend(impl: FirebaseExamPurchaseBackend): ExamPurchaseBackend

    @Binds
    abstract fun bindPendingExamUnlockStore(impl: DataStorePendingExamUnlockStore): PendingExamUnlockStore

    @Binds
    abstract fun bindAppForegroundEvents(impl: ProcessForegroundEvents): AppForegroundEvents
}
