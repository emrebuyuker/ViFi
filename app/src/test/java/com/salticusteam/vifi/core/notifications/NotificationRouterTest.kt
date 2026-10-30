package com.salticusteam.vifi.core.notifications

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.navigation.ModalPresence
import com.salticusteam.vifi.core.notifications.NotificationRouter.Request
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `NotificationRouterTests` (14) plus the Android waits: the tour, a modal, the launch intent. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRouterTest {

    private val modalPresence = ModalPresence()
    private val signedIn = SessionState.SignedIn(FakeAuthService.DEFAULT_USER)

    private fun TestScope.sut() = NotificationRouter(modalPresence, backgroundScope)

    // --- Queueing and delivery ---

    @Test
    fun `receive only queues the tap`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.receive("n-1")

        assertEquals(Request("n-1"), sut.pending.value)
        sut.deliveries(ready = MutableStateFlow(false)).test { expectNoEvents() }
        assertEquals(Request("n-1"), sut.pending.value)
    }

    @Test
    fun `delivery opens the inbox on the tapped notification and clears the tap`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.receive("n-1")

        sut.deliveries(ready = MutableStateFlow(true)).test {
            assertEquals(Request("n-1"), awaitItem())
            expectNoEvents()
        }
        assertNull(sut.pending.value)
    }

    @Test
    fun `a push without an id opens the inbox with no highlight`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.receive(null)

        sut.deliveries(ready = MutableStateFlow(true)).test {
            assertEquals(Request(null), awaitItem())
        }
    }

    @Test
    fun `nothing is delivered without a tap`() = runTest(UnconfinedTestDispatcher()) {
        sut().deliveries(ready = MutableStateFlow(true)).test { expectNoEvents() }
    }

    @Test
    fun `a newer tap replaces one still waiting`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        val ready = MutableStateFlow(false)

        sut.deliveries(ready).test {
            sut.receive("n-1")
            sut.receive("n-2")
            ready.value = true

            assertEquals(Request("n-2"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a tap while the inbox is open is delivered again to move the highlight`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.deliveries(ready = MutableStateFlow(true)).test {
            sut.receive("n-1")
            assertEquals(Request("n-1"), awaitItem())
            sut.receive("n-2")
            assertEquals(Request("n-2"), awaitItem())
            sut.receive("n-2")
            assertEquals(Request("n-2"), awaitItem())
        }
    }

    @Test
    fun `reset drops the waiting tap`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.receive("n-1")

        sut.reset()

        assertNull(sut.pending.value)
        sut.deliveries(ready = MutableStateFlow(true)).test { expectNoEvents() }
    }

    // --- Android waits ---

    @Test
    fun `delivery waits for the tour to finish`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        val tourOver = MutableStateFlow(false)
        sut.receive("n-1")

        sut.deliveries(tourOver).test {
            expectNoEvents()
            tourOver.value = true
            assertEquals(Request("n-1"), awaitItem())
        }
    }

    @Test
    fun `delivery waits for a modal to close and never closes it`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        modalPresence.opened()
        sut.receive("n-1")

        sut.deliveries(ready = MutableStateFlow(true)).test {
            expectNoEvents()
            assertEquals(1, modalPresence.count.value)
            sut.receive("n-2")
            modalPresence.closed()

            assertEquals(Request("n-2"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a tap stays waiting when its delivery is not collected`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        modalPresence.opened()
        sut.receive("n-1")

        sut.deliveries(ready = MutableStateFlow(true)).test { cancelAndIgnoreRemainingEvents() }

        assertEquals(Request("n-1"), sut.pending.value)
    }

    // --- Session rules ---

    @Test
    fun `a cold-start tap survives the session being restored signed in`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.receive("n-1")

        sut.sessionDidChange(SessionState.Unknown, signedIn)

        assertEquals(Request("n-1"), sut.pending.value)
    }

    @Test
    fun `a tap made while signed out is dropped on sign-in`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.receive("n-1")

        sut.sessionDidChange(SessionState.SignedOut, signedIn)

        assertNull(sut.pending.value)
    }

    @Test
    fun `signing out drops the waiting tap`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        sut.receive("n-1")

        sut.sessionDidChange(signedIn, SessionState.SignedOut)

        assertNull(sut.pending.value)
    }

    @Test
    fun `a cold start signed out drops the tap so a later sign-in opens nothing`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        val sessions = MutableStateFlow<SessionState>(SessionState.Unknown)
        sut.start(sessions)
        sut.receive("n-1")

        sessions.value = SessionState.SignedOut
        sessions.value = signedIn

        assertNull(sut.pending.value)
    }

    @Test
    fun `a sign-in with nothing waiting changes nothing`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.sessionDidChange(SessionState.SignedOut, signedIn)

        assertNull(sut.pending.value)
        sut.deliveries(ready = MutableStateFlow(true)).test { expectNoEvents() }
    }

    @Test
    fun `start follows the session once and keeps a cold-start tap`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        val sessions = MutableStateFlow<SessionState>(SessionState.Unknown)
        sut.start(sessions)
        sut.start(MutableStateFlow(SessionState.SignedOut))
        sut.receive("n-1")

        sessions.value = signedIn

        assertEquals(Request("n-1"), sut.pending.value)
    }

    // --- The launch intent ---

    @Test
    fun `a tap is read from our flag or a system tray launch, with a safe id only`() {
        assertEquals(Request("n-1"), NotificationRouter.tapRequest(setOf("open_inbox"), true, "n-1"))
        assertEquals(
            Request("n-1"),
            NotificationRouter.tapRequest(setOf("google.message_id", "notificationId", "kind"), false, "n-1"),
        )
        assertEquals(Request(null), NotificationRouter.tapRequest(setOf("google.message_id"), false, null))
        assertEquals(Request(null), NotificationRouter.tapRequest(setOf("open_inbox"), true, "../inbox"))
        assertNull(NotificationRouter.tapRequest(setOf("notificationId", "vifi_signed_out"), false, "n-1"))
        assertNull(NotificationRouter.tapRequest(emptySet(), false, null))
    }
}
