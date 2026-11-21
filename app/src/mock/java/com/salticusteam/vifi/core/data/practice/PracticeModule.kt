package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.data.repository.PracticeService
import com.salticusteam.vifi.core.data.repository.SubscriptionProofProvider
import com.salticusteam.vifi.core.data.repository.fake.FakePoolIndexReader
import com.salticusteam.vifi.core.data.repository.fake.FakePracticeService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionProofProvider
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * "Sınava gir" bindings of the mock flavor (4.2.0): TOPOLOJİ 1's sample index, the sample questions as a pool with the
 * in-memory free limit (5 a day; already used up under `vifi_practice_limit_reached`, iOS `-ViFiPracticeLimitReached`),
 * and a sample proof under `vifi_subscribed` (unlimited, iOS `-ViFiSubscribed`).
 */
@Module
@InstallIn(SingletonComponent::class)
object PracticeModule {

    @Provides
    @Singleton
    fun providePoolIndexReader(): PoolIndexReader = FakePoolIndexReader(latency = 100.milliseconds)

    @Provides
    @Singleton
    fun providePracticeService(): PracticeService =
        FakePracticeService(
            isSubscribed = LaunchArgs.isSet(LaunchArg.SUBSCRIBED),
            limitReached = LaunchArgs.isSet(LaunchArg.PRACTICE_LIMIT_REACHED),
            latency = 150.milliseconds,
        )

    @Provides
    @Singleton
    fun provideSubscriptionProofProvider(): SubscriptionProofProvider = FakeSubscriptionProofProvider(
        proof = FakeSubscriptionProofProvider.SAMPLE.takeIf { LaunchArgs.isSet(LaunchArg.SUBSCRIBED) },
    )
}
