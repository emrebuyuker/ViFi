package com.salticusteam.vifi.core.data.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.salticusteam.vifi.core.data.repository.NotificationEventReporter
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationEventReporter
import com.salticusteam.vifi.core.model.NotificationEvent
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** iOS `NotificationEventTrackerTests` (11). */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationEventTrackerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val reporter = FakeNotificationEventReporter()
    private val file by lazy { tmp.newFile("events.preferences_pb").also { it.delete() } }

    private fun TestScope.store(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        produceFile = { file },
    )

    private fun TestScope.sut(
        dataStore: DataStore<Preferences> = store(),
        reporter: NotificationEventReporter = this@NotificationEventTrackerTest.reporter,
    ) = NotificationEventTracker(reporter, dataStore, backgroundScope)

    @Test
    fun `a report is sent to the server`() = runTest(UnconfinedTestDispatcher()) {
        sut().report(NotificationEvent.OPENED, "n-1", "u-1")?.join()

        assertEquals(listOf(NotificationEvent.OPENED to "n-1"), reporter.reports)
    }

    @Test
    fun `an acknowledged report is not sent again`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.report(NotificationEvent.OPENED, "n-1", "u-1")?.join()
        sut.report(NotificationEvent.OPENED, "n-1", "u-1")?.join()

        assertEquals(1, reporter.reports.size)
    }

    @Test
    fun `other notifications and the other event still report`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.report(NotificationEvent.OPENED, "n-1", "u-1")?.join()
        sut.report(NotificationEvent.ACTION_TAPPED, "n-1", "u-1")?.join()
        sut.report(NotificationEvent.OPENED, "n-2", "u-1")?.join()

        assertEquals(
            listOf(NotificationEvent.OPENED to "n-1", NotificationEvent.ACTION_TAPPED to "n-1", NotificationEvent.OPENED to "n-2"),
            reporter.reports,
        )
    }

    @Test
    fun `another account on the device still counts`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.report(NotificationEvent.OPENED, "n-1", "u-1")?.join()
        sut.report(NotificationEvent.OPENED, "n-1", "u-2")?.join()

        assertEquals(2, reporter.reports.size)
    }

    @Test
    fun `what was acknowledged is remembered on the device`() = runTest(UnconfinedTestDispatcher()) {
        val dataStore = store()
        sut(dataStore).report(NotificationEvent.OPENED, "n-1", "u-1")?.join()

        sut(dataStore).report(NotificationEvent.OPENED, "n-1", "u-1")?.join()

        assertEquals(1, reporter.reports.size)
    }

    @Test
    fun `a failed report is sent again next time`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reporter.failure = IOException("offline")
        sut.report(NotificationEvent.OPENED, "n-1", "u-1")?.join()

        reporter.failure = null
        sut.report(NotificationEvent.OPENED, "n-1", "u-1")?.join()

        assertEquals(listOf(NotificationEvent.OPENED to "n-1"), reporter.reports)
    }

    @Test
    fun `reporting does not block and a repeat while in flight is dropped`() = runTest(UnconfinedTestDispatcher()) {
        val answer = CompletableDeferred<Unit>()
        var calls = 0
        val slow = object : NotificationEventReporter {
            override suspend fun report(event: NotificationEvent, notificationId: String) {
                calls++
                answer.await()
            }
        }
        val sut = sut(reporter = slow)

        val first = sut.report(NotificationEvent.OPENED, "n-1", "u-1")
        assertNull(sut.report(NotificationEvent.OPENED, "n-1", "u-1"))
        answer.complete(Unit)
        first?.join()

        assertEquals(1, calls)
    }

    @Test
    fun `the in-flight mark is released on failure`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        reporter.failure = IOException("offline")
        sut.report(NotificationEvent.ACTION_TAPPED, "n-1", "u-1")?.join()
        reporter.failure = null

        val retry = sut.report(NotificationEvent.ACTION_TAPPED, "n-1", "u-1")

        assertTrue(retry != null)
        retry?.join()
        assertEquals(1, reporter.reports.size)
    }

    @Test
    fun `the memory keeps the newest 500 and forgets the oldest`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()
        (0..NotificationEventTracker.MEMORY_LIMIT).forEach { sut.report(NotificationEvent.OPENED, "n-$it", "u-1")?.join() }
        assertEquals(NotificationEventTracker.MEMORY_LIMIT + 1, reporter.reports.size)

        sut.report(NotificationEvent.OPENED, "n-${NotificationEventTracker.MEMORY_LIMIT}", "u-1")?.join()
        sut.report(NotificationEvent.OPENED, "n-0", "u-1")?.join()

        assertEquals(NotificationEventTracker.MEMORY_LIMIT + 2, reporter.reports.size)
        assertEquals(NotificationEvent.OPENED to "n-0", reporter.reports.last())
    }

    @Test
    fun `the fake reporter records instead of calling the server`() = runTest {
        val fake = FakeNotificationEventReporter()

        fake.report(NotificationEvent.ACTION_TAPPED, "n-1")

        assertEquals(listOf(NotificationEvent.ACTION_TAPPED to "n-1"), fake.reports)
    }

    @Test
    fun `the event names are the server's`() {
        assertEquals(listOf("opened", "actionTapped"), NotificationEvent.entries.map { it.raw })
    }
}
