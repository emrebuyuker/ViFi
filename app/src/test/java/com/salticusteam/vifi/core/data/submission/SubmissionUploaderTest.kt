package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeExamSubmissionService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.testing.MutableClock
import kotlin.time.Duration
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubmissionUploaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FileSubmissionDraftStore
    private lateinit var service: FakeExamSubmissionService
    private lateinit var analytics: FakeAnalyticsTracker
    private lateinit var sut: SubmissionUploader

    private val pages = listOf(SubmissionFile(byteArrayOf(1), 1), SubmissionFile(byteArrayOf(2), 2))
    private val pending = PendingSubmission(
        submissionId = "sub1", ownerId = "uid", university = "Ege", faculty = "Müh", department = "Bil",
        lesson = "BLG 101", examName = "Vize", year = 2024, kind = ExamKind.IMAGES.raw, fileCount = 2,
        estimatedTier = SubmissionTier.NEW_EXAM.raw,
    )
    private val draftId = "draft-1"

    @Before
    fun setUp() {
        store = FileSubmissionDraftStore(folder.newFolder("drafts"), MutableClock(), dispatcher)
        service = FakeExamSubmissionService(latency = Duration.ZERO)
        analytics = FakeAnalyticsTracker()
        sut = SubmissionUploader(store, service, analytics)
    }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        store.writePages(draftId, pages)
        store.writeManifest(draftId, pending)
        body()
    }

    @Test
    fun `upload sends the queued submission under its id, deletes the draft and logs it`() = test {
        val outcome = sut.upload(draftId, isLastAttempt = false)

        assertEquals(SubmissionUploadOutcome.Succeeded, outcome)
        assertEquals(listOf("sub1"), service.submissionIds)
        val (owner, submission) = service.submissions.single()
        assertEquals("uid", owner)
        assertEquals(pages, submission.files)
        assertTrue(store.readPages(draftId).isEmpty())
        assertNull(store.readManifest(draftId))
        assertEquals(listOf(AnalyticsEvent.ExamSubmitted(ExamKind.IMAGES, 2, SubmissionTier.NEW_EXAM)), analytics.events)
    }

    @Test
    fun `upload run again after it succeeded sends nothing more`() = test {
        sut.upload(draftId, isLastAttempt = false)

        val outcome = sut.upload(draftId, isLastAttempt = false)

        assertEquals(SubmissionUploadOutcome.Succeeded, outcome)
        assertEquals(1, service.submitCallCount)
        assertEquals(1, analytics.events.size)
    }

    @Test
    fun `upload when offline asks for a retry and keeps everything for it`() = test {
        service.failure = ExamSubmissionError.Offline

        val outcome = sut.upload(draftId, isLastAttempt = false)

        assertEquals(SubmissionUploadOutcome.Retry, outcome)
        assertEquals(pending, store.readManifest(draftId))
        assertTrue(service.discardedIds.isEmpty())
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `retries reuse the same submission id, so the submission is stored once`() = test {
        service.failure = ExamSubmissionError.Offline
        sut.upload(draftId, isLastAttempt = false)
        sut.upload(draftId, isLastAttempt = false)
        service.failure = null

        sut.upload(draftId, isLastAttempt = false)

        assertEquals(3, service.submitCallCount)
        assertEquals(listOf("sub1"), service.submissionIds)
    }

    @Test
    fun `upload when offline on the last attempt gives up and reports it`() = test {
        service.failure = ExamSubmissionError.Offline

        val outcome = sut.upload(draftId, isLastAttempt = true)

        assertEquals(SubmissionUploadOutcome.Failed(ExamSubmissionError.Offline), outcome)
        assertEquals(listOf("sub1"), service.discardedIds)
        assertNull(store.readManifest(draftId))
        // The pages stay, so an open form can submit again.
        assertEquals(pages, store.readPages(draftId))
        assertEquals(listOf(AnalyticsEvent.ExamSubmitFailed("offline")), analytics.events)
    }

    @Test
    fun `upload when the rules deny it gives up at once`() = test {
        service.failure = ExamSubmissionError.PermissionDenied

        val outcome = sut.upload(draftId, isLastAttempt = false)

        assertEquals(SubmissionUploadOutcome.Failed(ExamSubmissionError.PermissionDenied), outcome)
        assertEquals(listOf("sub1"), service.discardedIds)
    }

    @Test
    fun `upload when the draft lost pages gives up without sending`() = test {
        store.clearPages(draftId)
        store.writePages(draftId, pages.take(1))

        val outcome = sut.upload(draftId, isLastAttempt = false)

        assertEquals(SubmissionUploadOutcome.Failed(ExamSubmissionError.InvalidSubmission), outcome)
        assertEquals(0, service.submitCallCount)
        assertEquals(listOf(AnalyticsEvent.ExamSubmitFailed("invalid_submission")), analytics.events)
    }

    @Test
    fun `a failure reason round-trips to the error the form shows`() = test {
        for (error in listOf(
            ExamSubmissionError.NotSignedIn,
            ExamSubmissionError.InvalidSubmission,
            ExamSubmissionError.PermissionDenied,
            ExamSubmissionError.Offline,
        )) {
            assertEquals(error, ExamSubmissionError.fromAnalyticsReason(error.analyticsReason))
        }
        assertEquals(
            ExamSubmissionError.Unknown(ExamSubmissionError.GENERIC_MESSAGE),
            ExamSubmissionError.fromAnalyticsReason(null),
        )
    }
}
