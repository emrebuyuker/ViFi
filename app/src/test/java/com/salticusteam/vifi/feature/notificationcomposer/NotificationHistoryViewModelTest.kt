package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationStats
import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.core.model.SentNotification
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `NotificationHistoryViewModelTests` (7) and the Android additions. */
class NotificationHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val service = StubNotificationAdminService()
    private val analytics = FakeAnalyticsTracker()

    private fun row(id: String, status: NotificationStatus = NotificationStatus.SENT) = SentNotification(
        id = id,
        kind = NotificationKind.GENERAL,
        title = "Başlık $id",
        body = "Mesaj",
        audience = NotificationAudience.All,
        audienceLabel = "Tüm kullanıcılar",
        status = status,
        isTest = false,
        isAutomatic = false,
        deferredByQuietHours = false,
        createdAt = Instant.ofEpochSecond(1_761_000_000),
        scheduledAt = if (status == NotificationStatus.SCHEDULED) Instant.ofEpochSecond(1_761_100_000) else null,
        sentAt = null,
        stats = NotificationStats.ZERO,
    )

    private fun viewModel() = NotificationHistoryViewModel(service, analytics)

    @Test
    fun `loading lists the history once, a refresh reloads it`() = runTest {
        service.historyResult = Result.success(listOf(row("a"), row("b")))
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), viewModel.uiState.value.notifications?.map { it.id })
        service.historyResult = Result.success(listOf(row("c")))
        advanceUntilIdle()
        assertEquals("Nothing reloads by itself", 1, service.historyCalls)

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isRefreshing)
        advanceUntilIdle()
        assertEquals(listOf("c"), viewModel.uiState.value.notifications?.map { it.id })
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `a failed load shows an error, and a failed refresh keeps the rows`() = runTest {
        service.historyResult = Result.failure(NotificationAdminError.PermissionDenied)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(NotificationAdminError.PermissionDenied.userMessage, viewModel.uiState.value.loadError)
        assertNull(viewModel.uiState.value.notifications)

        service.historyResult = Result.success(listOf(row("a")))
        viewModel.refresh()
        assertTrue("The retry shows the loading state", viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        assertEquals(listOf("a"), viewModel.uiState.value.notifications?.map { it.id })
        assertNull(viewModel.uiState.value.loadError)

        service.historyResult = Result.failure(NotificationAdminError.Offline)
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals("The rows stay on screen", listOf("a"), viewModel.uiState.value.notifications?.map { it.id })
        assertEquals(NotificationAdminError.Offline.userMessage, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `canceling a scheduled notification calls the service and reloads`() = runTest {
        service.historyResult = Result.success(listOf(row("a", NotificationStatus.SCHEDULED)))
        val viewModel = viewModel()
        advanceUntilIdle()

        service.historyResult = Result.success(listOf(row("a", NotificationStatus.CANCELED)))
        viewModel.cancel(row("a", NotificationStatus.SCHEDULED))
        advanceUntilIdle()

        assertEquals(listOf("a"), service.canceledIds)
        assertEquals(NotificationStatus.CANCELED, viewModel.uiState.value.notifications?.first()?.status)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.cancelingId)
    }

    @Test
    fun `only a scheduled notification can be canceled`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.cancel(row("a", NotificationStatus.SENT))
        viewModel.cancel(row("b", NotificationStatus.CANCELED))
        advanceUntilIdle()

        assertTrue(service.canceledIds.isEmpty())
    }

    @Test
    fun `a failed cancellation reports the error and leaves the list alone`() = runTest {
        service.historyResult = Result.success(listOf(row("a", NotificationStatus.SCHEDULED)))
        service.cancelError = NotificationAdminError.Offline
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.cancel(row("a", NotificationStatus.SCHEDULED))
        advanceUntilIdle()

        assertEquals(NotificationAdminError.Offline.userMessage, viewModel.uiState.value.errorMessage)
        assertEquals(NotificationStatus.SCHEDULED, viewModel.uiState.value.notifications?.first()?.status)
        assertNull(viewModel.uiState.value.cancelingId)
        assertEquals("No reload", 1, service.historyCalls)
    }

    @Test
    fun `canceling one that already went out reloads to show where it is`() = runTest {
        service.historyResult = Result.success(listOf(row("a", NotificationStatus.SCHEDULED)))
        service.cancelError = NotificationAdminError.NotCancelable
        val viewModel = viewModel()
        advanceUntilIdle()

        service.historyResult = Result.success(listOf(row("a", NotificationStatus.SENT)))
        viewModel.cancel(row("a", NotificationStatus.SCHEDULED))
        advanceUntilIdle()

        assertEquals(NotificationAdminError.NotCancelable.userMessage, viewModel.uiState.value.errorMessage)
        assertEquals(NotificationStatus.SENT, viewModel.uiState.value.notifications?.first()?.status)
    }

    @Test
    fun `a second cancellation while one is in flight is ignored`() = runTest {
        service.historyResult = Result.success(listOf(row("a", NotificationStatus.SCHEDULED), row("b", NotificationStatus.SCHEDULED)))
        val viewModel = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        service.gate = gate

        viewModel.cancel(row("a", NotificationStatus.SCHEDULED))
        runCurrent()
        assertEquals("a", viewModel.uiState.value.cancelingId)
        assertFalse(viewModel.uiState.value.canCancel(row("b", NotificationStatus.SCHEDULED)))

        viewModel.cancel(row("b", NotificationStatus.SCHEDULED))
        runCurrent()
        assertEquals(listOf("a"), service.canceledIds)

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.cancelingId)
    }

    // --- Android ---

    @Test
    fun `a pull while a refresh is running is ignored`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        service.gate = gate

        viewModel.refresh()
        viewModel.refresh()
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, service.historyCalls)
    }

    @Test
    fun `the screen view is reported on each appearance`() = runTest {
        val viewModel = viewModel()

        viewModel.onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.NOTIFICATION_HISTORY)), analytics.events)
    }
}
