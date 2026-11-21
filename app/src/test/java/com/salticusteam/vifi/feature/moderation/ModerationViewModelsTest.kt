package com.salticusteam.vifi.feature.moderation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeModerationService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubmitterProfileReader
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ApprovalResult
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ModerationError
import com.salticusteam.vifi.core.model.ProfileError
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

/** iOS `ModerationViewModelTests` + the Android queue / detail / preview split. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val analytics = FakeAnalyticsTracker()
    private val service = FakeModerationService(latency = Duration.ZERO)
    private val queue = ModerationQueue(service)
    private val reader = FakeSubmitterProfileReader()
    private val directory = SubmitterDirectory(reader)

    private fun list() = ModerationViewModel(queue, directory, analytics)

    private fun detail(
        id: String = "sub-1",
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        queue: ModerationQueue = this.queue,
    ) = ModerationDetailViewModel(id, savedStateHandle, queue, service, directory, analytics)

    // --- Queue ---

    @Test
    fun `queue loads the pending submissions`() = runTest {
        val sut = list()
        assertTrue(sut.uiState.value.isLoading)

        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("sub-1", "sub-dup", "sub-2"), state.records?.map { it.id })
        assertNull(state.error)
    }

    @Test
    fun `queue failure shows the error and retry recovers`() = runTest {
        service.failure = ModerationError.PermissionDenied
        val sut = list()
        advanceUntilIdle()
        assertEquals(ModerationError.PermissionDenied.userMessage, sut.uiState.value.error)
        assertNull(sut.uiState.value.records)

        service.failure = null
        sut.retry()
        advanceUntilIdle()

        assertNull(sut.uiState.value.error)
        assertEquals(3, sut.uiState.value.records?.size)
    }

    @Test
    fun `queue refresh failure keeps the list on screen`() = runTest {
        val sut = list()
        advanceUntilIdle()

        service.failure = ModerationError.Offline
        sut.refresh()
        advanceUntilIdle()

        assertEquals(3, sut.uiState.value.records?.size)
        assertNull(sut.uiState.value.error)
        assertFalse(sut.uiState.value.isRefreshing)
    }

    @Test
    fun `queue reports its screen view`() = runTest {
        list().onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.MODERATION)), analytics.events)
    }

    // --- Queue: submitters (ADM-03) ---

    @Test
    fun `queue rows show the submitter or the missing note, each uid read once`() = runTest {
        val sut = list()
        advanceUntilIdle()

        sut.onRowShown("mock-user") // sub-1
        sut.onRowShown("mock-user") // sub-dup, same owner
        sut.onRowShown("mock-user-2") // sub-2, no profile
        advanceUntilIdle()

        val submitters = sut.uiState.value.submitters
        assertEquals(SubmitterState.Loaded(FakeSubmitterProfileReader.SAMPLE), submitters["mock-user"])
        assertEquals(SubmitterState.Missing, submitters["mock-user-2"])
        assertEquals(listOf("mock-user", "mock-user-2"), reader.readUids)
    }

    // --- Detail: approve (ADM-01) ---

    @Test
    fun `approve removes the submission from the queue and reports the tier`() = runTest {
        val list = list()
        advanceUntilIdle()
        val sut = detail()
        advanceUntilIdle()

        sut.approve(AIProvider.CLAUDE)
        assertTrue(sut.uiState.value.isWorking)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals(ApprovalResult(SubmissionTier.NEW_UNIVERSITY, 500, "pub-sub-1"), state.approval)
        assertEquals(listOf("sub-dup", "sub-2"), list.uiState.value.records?.map { it.id })
        assertEquals(listOf("sub-1" to AIProvider.CLAUDE), service.approvals)
        assertTrue(AnalyticsEvent.SubmissionApproved(SubmissionTier.NEW_UNIVERSITY) in analytics.events)
    }

    @Test
    fun `each approval button sends its own provider`() = runTest {
        queue.reload()
        detail("sub-1").approve(AIProvider.CLAUDE)
        detail("sub-2").approve(AIProvider.GEMINI)
        advanceUntilIdle()

        assertEquals(listOf("sub-1" to AIProvider.CLAUDE, "sub-2" to AIProvider.GEMINI), service.approvals)
    }

    @Test
    fun `approve acknowledged finishes the detail`() = runTest {
        queue.reload()
        val sut = detail()
        sut.approve(AIProvider.GEMINI)
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

        sut.approve(AIProvider.CLAUDE)
        sut.approve(AIProvider.GEMINI)
        advanceUntilIdle()

        // A second request would have hit "no longer pending" in the fake and surfaced an error.
        assertNull(sut.uiState.value.error)
        assertEquals(500, sut.uiState.value.approval?.coins)
        assertEquals(listOf("sub-1" to AIProvider.CLAUDE), service.approvals)
    }

    @Test
    fun `approve failure keeps the submission and shows the message`() = runTest {
        queue.reload()
        service.failure = ModerationError.AlreadyCredited
        val sut = detail()

        sut.approve(AIProvider.CLAUDE)
        advanceUntilIdle()

        assertEquals(ModerationError.AlreadyCredited.userMessage, sut.uiState.value.error)
        assertNull(sut.uiState.value.approval)
        assertEquals(3, queue.records.value?.size)
        assertTrue(analytics.events.none { it is AnalyticsEvent.SubmissionApproved })
    }

    // --- Detail: duplicate (ADM-02, iOS `duplicateWarning` / `approveDuplicate`) ---

    @Test
    fun `a duplicate submission resolves to the existing exam, a new one to nil`() = runTest {
        queue.reload()
        val duplicate = detail("sub-dup")
        val fresh = detail("sub-1")
        advanceUntilIdle()

        assertEquals("mock-existing-exam", duplicate.uiState.value.duplicateExamId)
        assertNull(fresh.uiState.value.duplicateExamId)
    }

    @Test
    fun `approving a duplicate reports the existing exam id`() = runTest {
        queue.reload()
        val sut = detail("sub-dup")
        advanceUntilIdle()

        sut.approve(AIProvider.GEMINI)
        advanceUntilIdle()

        assertEquals(ApprovalResult(SubmissionTier.DUPLICATE, 25, "mock-existing-exam"), sut.uiState.value.approval)
        assertEquals(listOf("sub-dup" to AIProvider.GEMINI), service.approvals)
    }

    // --- Detail: submitter (ADM-03) ---

    @Test
    fun `detail shows the submitter of its record`() = runTest {
        queue.reload()
        val sut = detail("sub-1")
        advanceUntilIdle()

        assertEquals(SubmitterState.Loaded(FakeSubmitterProfileReader.SAMPLE), sut.uiState.value.submitter)
    }

    @Test
    fun `detail without a profile shows it missing and still approves`() = runTest {
        queue.reload()
        val sut = detail("sub-2")
        advanceUntilIdle()
        assertEquals(SubmitterState.Missing, sut.uiState.value.submitter)

        sut.approve(AIProvider.CLAUDE)
        advanceUntilIdle()

        assertEquals("pub-sub-2", sut.uiState.value.approval?.publishedExamId)
    }

    @Test
    fun `detail with a failing profile read shows the failure and still approves`() = runTest {
        reader.error = ProfileError.Offline
        queue.reload()
        val sut = detail("sub-1")
        advanceUntilIdle()
        assertEquals(SubmitterState.Failed, sut.uiState.value.submitter)

        sut.approve(AIProvider.CLAUDE)
        advanceUntilIdle()

        assertEquals(500, sut.uiState.value.approval?.coins)
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
        assertEquals(listOf("sub-1", "sub-dup"), queue.records.value?.map { it.id })
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
        assertEquals(3, queue.records.value?.size)
    }

    @Test
    fun `detail for an unknown submission has no record and ignores actions`() = runTest {
        queue.reload()
        val sut = detail("missing")

        sut.approve(AIProvider.CLAUDE)
        sut.onRejectRequested()
        advanceUntilIdle()

        assertNull(sut.uiState.value.record)
        assertFalse(sut.uiState.value.isWorking)
        assertFalse(sut.uiState.value.isConfirmingReject)
        assertTrue(service.approvals.isEmpty())
    }

    @Test
    fun `detail after process death reloads the queue to restore its record and the typed rejection`() = runTest {
        queue.reload()
        val handle = SavedStateHandle()
        val before = detail("sub-2", handle)
        before.onRejectRequested()
        before.onRejectReasonChange("okunmuyor")

        // A recreated process: the singleton queue starts empty, the route id and saved state survive.
        val sut = detail("sub-2", SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }), ModerationQueue(service))
        assertTrue(sut.uiState.value.isLoading)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isLoading)
        assertEquals("sub-2", state.record?.id)
        assertTrue(state.isConfirmingReject)
        assertEquals("okunmuyor", state.rejectReason)
        // The submitter lookup and duplicate check run once the record is back.
        assertEquals(SubmitterState.Missing, state.submitter)
        assertNull(state.duplicateExamId)
    }

    @Test
    fun `detail after process death for a submission that is gone shows it missing`() = runTest {
        val sut = detail("missing", queue = ModerationQueue(service))
        advanceUntilIdle()

        assertFalse(sut.uiState.value.isLoading)
        assertNull(sut.uiState.value.record)
    }

    @Test
    fun `detail with the queue already loaded does not reload it`() = runTest {
        queue.reload()
        service.failure = ModerationError.Offline

        val sut = detail("missing")

        assertFalse(sut.uiState.value.isLoading)
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
