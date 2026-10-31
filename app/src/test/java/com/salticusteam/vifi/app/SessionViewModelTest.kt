package com.salticusteam.vifi.app

import com.salticusteam.vifi.core.data.inbox.InboxStore
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.session.AnalyticsIdentity
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.data.session.SessionTeardown
import com.salticusteam.vifi.core.notifications.DeviceRegistrationStore
import com.salticusteam.vifi.core.notifications.NotificationRouter
import com.salticusteam.vifi.core.notifications.PushTopicSubscription
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

/** The app-wide followers of the session start once, when the app's root is built (NIT-01 wiring). */
class SessionViewModelTest {

    @Test
    fun `every session follower is started once, the session-bound ones with the app's session`() = runTest {
        val sessionStore = SessionStore(FakeAuthService(isSignedIn = true), backgroundScope)
        val teardown = mockk<SessionTeardown>(relaxed = true)
        val identity = mockk<AnalyticsIdentity>(relaxed = true)
        val registration = mockk<DeviceRegistrationStore>(relaxed = true)
        val topics = mockk<PushTopicSubscription>(relaxed = true)
        val router = mockk<NotificationRouter>(relaxed = true)
        val inbox = mockk<InboxStore>(relaxed = true)
        val consent = mockk<NotificationSettingsStore>(relaxed = true)

        val sut = SessionViewModel(sessionStore, teardown, identity, registration, topics, router, inbox, consent)

        assertSame(sessionStore.state, sut.session)
        verify(exactly = 1) {
            teardown.start()
            identity.start()
            topics.start()
            registration.start(sessionStore.state)
            router.start(sessionStore.state)
            inbox.start(sessionStore.state)
            consent.start(sessionStore.state)
        }
    }
}
