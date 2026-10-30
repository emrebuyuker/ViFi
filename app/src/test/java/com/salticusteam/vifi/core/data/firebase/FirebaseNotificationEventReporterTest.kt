package com.salticusteam.vifi.core.data.firebase

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.salticusteam.vifi.core.model.NotificationEvent
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/** The `trackNotificationEvent` call (iOS `FirebaseNotificationEventReporter`, appendix A.8): its name and payload. */
class FirebaseNotificationEventReporterTest {

    private val functions = mockk<FirebaseFunctions>()
    private val callable = mockk<HttpsCallableReference>()
    private val reporter = FirebaseNotificationEventReporter(functions)

    init {
        every { functions.getHttpsCallable("trackNotificationEvent") } returns callable
    }

    @Test
    fun `each event is sent with the notification id under the server's event name`() = runTest {
        val sent = mutableListOf<Any?>()
        every { callable.call(any()) } answers {
            sent += firstArg<Any?>()
            Tasks.forResult(mockk<HttpsCallableResult>())
        }

        reporter.report(NotificationEvent.OPENED, "n-1")
        reporter.report(NotificationEvent.ACTION_TAPPED, "n-2")

        assertEquals(
            listOf<Any?>(
                mapOf("notificationId" to "n-1", "event" to "opened"),
                mapOf("notificationId" to "n-2", "event" to "actionTapped"),
            ),
            sent,
        )
    }

    @Test
    fun `a failed call is thrown, so the tracker sends it again next time`() = runTest {
        val failure = IOException("offline")
        every { callable.call(any()) } returns Tasks.forException(failure)

        try {
            reporter.report(NotificationEvent.OPENED, "n-1")
            fail("Expected the call's failure")
        } catch (e: IOException) {
            assertSame(failure, e)
        }
    }
}
