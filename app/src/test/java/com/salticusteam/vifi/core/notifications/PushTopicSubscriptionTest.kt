package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushTopicSubscriptionTest {

    private val service = FakeNotificationService()

    private fun TestScope.sut() = PushTopicSubscription(service, TestScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `start joins the all topic once per process`() = runTest {
        val sut = sut()

        sut.start()
        sut.start()

        assertEquals(setOf("all"), service.subscribedTopics)
        assertEquals(1, service.subscribeCount)
    }

    @Test
    fun `refresh joins again for a new token, started or not`() = runTest {
        val sut = sut()

        sut.refresh()
        sut.start()
        sut.refresh()

        assertEquals(3, service.subscribeCount)
    }

    @Test
    fun `a failed join is logged, not thrown, and the next refresh retries`() = runTest {
        val sut = sut()
        service.subscribeFailure = IOException("offline")

        sut.start()
        service.subscribeFailure = null
        sut.refresh()

        assertEquals(2, service.subscribeCount)
        assertTrue("all" in service.subscribedTopics)
    }
}
