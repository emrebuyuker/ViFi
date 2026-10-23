package com.salticusteam.vifi.feature.moderation

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeModerationService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ApprovalResult
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ModerationError
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()
    private val service = FakeModerationService(latency = Duration.ZERO)
    private val queue = ModerationQueue(service)

    private fun detail(id: String = "sub-1") = ModerationDetailViewModel(id, queue, service, analytics)

    // --- Queue ---

    @Test
    fun `queue loads the pending submissions`() = runTest {
        val sut = ModerationViewModel(queue, analytics)
        assertTrue(sut.uiState.value.isLoading)

        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("sub-1", "sub-2"), state.records?.map { it.id })
        assertNull(state.error)
    }

    @Test
    fun `queue failure shows the error and retry recovers`() = runTest {
        service.failure = ModerationError.PermissionDenied
        val sut = ModerationViewModel(queue, analytics)
        advanceUntilIdle()
        assertEquals(ModerationError.PermissionDenied.userMessage, sut.uiState.value.error)
        assertNull(sut.uiState.value.records)

        service.failure = null
        sut.retry()
        advanceUntilIdle()

        assertNull(sut.uiState.value.error)
        assertEquals(2, sut.uiState.value.records?.size)
    }

    @Test
    fun `queue refresh failure keeps the list on screen`() = runTest {
        val sut = ModerationViewModel(queue, analytics)
        advanceUntilIdle()

        service.failure = ModerationError.Offline
        sut.refresh()
        advanceUntilIdle()

        assertEquals(2, sut.uiState.value.records?.size)
        assertNull(sut.uiState.value.error)
        assertFalse(sut.uiState.value.isRefreshing)
    }

    @Test
    fun `queue reports its screen view`() = runTest {
        ModerationViewModel(queue, analytics).onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.MODERATION)), analytics.events)
    }

    // --- Detail: approve ---

    @Test
    fun `approve removes the submission from the queue and reports the tier`() = runTest {
        val list = ModerationViewModel(queue, analytics)
        advanceUntilIdle()
        val sut = detail()

        sut.approve()
        assertTrue(sut.uiState.value.isWorking)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals(ApprovalResult(SubmissionTier.NEW_UNIVERSITY, 500, "pub-sub-1"), state.approval)
        assertEquals(listOf("sub-2"), list.uiState.value.records?.map { it.id })
        assertTrue(AnalyticsEvent.SubmissionApproved(SubmissionTier.NEW_UNIVERSITY) in analytics.events)
    }

    @Test
    fun `approve acknowledged finishes the detail`() = runTest {
        queue.reload()
        val sut = detail()
        sut.approve()
        advanceUntilIdle()

        sut.events.test {
            sut.onApprovalAcknowledged()
            assertEquals(ModerationDetailEvent.Finished, awaitItem())
        }
    }

    @Test
    fun `approve twice while working sends one request`() = runTest {
        queue.reload()
        val sut = detail()

        sut.approve()
        sut.approve()
        advanceUntilIdle()

        // A second request would have hit "no longer pending" in the fake and surfaced an error.
        assertNull(sut.uiState.value.error)
        assertEquals(500, sut.uiState.value.approval?.coins)
    }

    @Test
    fun `approve failure keeps the submission and shows the message`() = runTest {
        queue.reload()
        service.failure = ModerationError.AlreadyCredited
        val sut = detail()

        sut.approve()
        advanceUntilIdle()

        assertEquals(ModerationError.AlreadyCredited.userMessage, sut.uiState.value.error)
        assertNull(sut.uiState.value.approval)
        assertEquals(2, queue.records.value?.size)
        assertTrue(analytics.events.none { it is AnalyticsEvent.SubmissionApproved })
    }

    // --- Detail: reject ---

    @Test
    fun `reject with a reason finishes and removes the submission`() = runTest {
        queue.reload()
        val sut = detail("sub-2")

        sut.onRejectRequested()
        assertTrue(sut.uiState.value.isConfirmingReject)
        sut.onRejectReasonChange("  okunmuyor ")
        sut.events.test {
            sut.confirmReject()
            assertEquals(ModerationDetailEvent.Finished, awaitItem())
        }

        assertEquals(listOf("sub-2" to "okunmuyor"), service.rejections)
        assertEquals(listOf("sub-1"), queue.records.value?.map { it.id })
        assertTrue(AnalyticsEvent.SubmissionRejected in analytics.events)
    }

    @Test
    fun `reject with a blank reason sends none`() = runTest {
        queue.reload()
        val sut = detail()

        sut.onRejectRequested()
        sut.onRejectReasonChange("   ")
        sut.confirmReject()
        advanceUntilIdle()

        assertEquals(listOf("sub-1" to null), service.rejections)
    }

    @Test
    fun `reject failure shows the message and stays`() = runTest {
        queue.reload()
        service.failure = ModerationError.NotPending
        val sut = detail()

        sut.onRejectRequested()
        sut.confirmReject()
        advanceUntilIdle()

        assertEquals(ModerationError.NotPending.userMessage, sut.uiState.value.error)
        assertFalse(sut.uiState.value.isConfirmingReject)
        assertEquals(2, queue.records.value?.size)
    }

    @Test
    fun `detail for an unknown submission has no record and ignores actions`() = runTest {
        queue.reload()
        val sut = detail("missing")

        sut.approve()
        sut.onRejectRequested()
        advanceUntilIdle()

        assertNull(sut.uiState.value.record)
        assertFalse(sut.uiState.value.isWorking)
        assertFalse(sut.uiState.value.isConfirmingReject)
    }

    // --- Preview ---

    @Test
    fun `preview shapes the files as an exam document in reading order`() = runTest {
        queue.reload()
        val state = SubmissionPreviewViewModel("sub-1", queue, service).uiState.value

        assertEquals("2023 VİZE", state?.title)
        assertEquals("Boğaziçi Üniversitesi › Ayrık Matematik", state?.subtitle)
        assertEquals(ExamKind.IMAGES, state?.kind)
        assertEquals(
            listOf(
                "https://example.com/pending/mock-user/sub-1/page-1.jpg",
                "https://example.com/pending/mock-user/sub-1/page-2.jpg",
            ),
            state?.document?.fileUrls,
        )
    }

    @Test
    fun `preview of an unknown submission is empty`() = runTest {
        queue.reload()

        assertNull(SubmissionPreviewViewModel("missing", queue, service).uiState.value)
    }
}
