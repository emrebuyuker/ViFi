package com.salticusteam.vifi.core.data.billing

import com.salticusteam.vifi.core.data.repository.ExamPurchaseService
import com.salticusteam.vifi.core.data.repository.SubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamPurchaseService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Billing bindings of the mock flavor: in-memory, no Google Play (iOS `-ViFiMockData`). `vifi_subscribed` starts
 * with an active Premium subscription (iOS `-ViFiSubscribed`).
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideSubscriptionService(): SubscriptionService =
        FakeSubscriptionService(subscribed = LaunchArgs.isSet(LaunchArg.SUBSCRIBED))

    @Provides
    @Singleton
    fun provideExamPurchaseService(): ExamPurchaseService = FakeExamPurchaseService()

    /** The real process lifecycle: it needs no Google Play, so the mock app re-reads on foregrounding too. */
    @Provides
    fun provideAppForegroundEvents(): AppForegroundEvents = ProcessForegroundEvents()
}
