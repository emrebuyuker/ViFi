package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.ExamSubmissionService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.ExamSubmissionError
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * One attempt at sending a queued draft — the logic of [SubmissionUploadWorker], kept free of WorkManager so it is
 * unit-tested. Reads the [PendingSubmission] manifest and the pages, sends them through the resumable
 * [ExamSubmissionService] under the manifest's fixed submission id, and decides what the attempt means:
 * - success: the draft is deleted and `exam_submit` is logged;
 * - a retryable failure ([ExamSubmissionError.isRetryable]) before the last attempt: [SubmissionUploadOutcome.Retry],
 *   the uploaded files stay so the next attempt resumes;
 * - otherwise the submission is given up: its uploads are discarded, the manifest is removed (the pages stay, so an
 *   open form can submit again) and `exam_submit_failed` is logged.
 *
 * A cancelled attempt (the worker was stopped) changes nothing; WorkManager runs it again.
 */
class SubmissionUploader @Inject constructor(
    private val store: SubmissionDraftStore,
    private val service: ExamSubmissionService,
    private val analytics: AnalyticsTracker,
) {

    suspend fun upload(draftId: String, isLastAttempt: Boolean): SubmissionUploadOutcome {
        // No manifest: an earlier run already sent it and deleted the draft before WorkManager recorded the success.
        val pending = store.readManifest(draftId) ?: return SubmissionUploadOutcome.Succeeded
        val files = store.readPages(draftId)
        val submission = pending.submission(files)?.takeIf { files.size == pending.fileCount }
            ?: return giveUp(draftId, ExamSubmissionError.InvalidSubmission)
        try {
            service.submit(submission, pending.ownerId, pending.submissionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e as? ExamSubmissionError ?: ExamSubmissionError.Unknown(ExamSubmissionError.GENERIC_MESSAGE)
            if (error.isRetryable && !isLastAttempt) return SubmissionUploadOutcome.Retry
            service.discardUploads(submission, pending.ownerId, pending.submissionId)
            return giveUp(draftId, error)
        }
        store.delete(draftId)
        analytics.track(AnalyticsEvent.ExamSubmitted(submission.kind, files.size, pending.tier))
        return SubmissionUploadOutcome.Succeeded
    }

    // Helpers
    private suspend fun giveUp(draftId: String, error: ExamSubmissionError): SubmissionUploadOutcome {
        store.deleteManifest(draftId)
        analytics.track(AnalyticsEvent.ExamSubmitFailed(error.analyticsReason))
        return SubmissionUploadOutcome.Failed(error)
    }
}

/** What one upload attempt came to. */
sealed interface SubmissionUploadOutcome {
    /** The submission is stored as pending (now or by an earlier attempt). */
    data object Succeeded : SubmissionUploadOutcome

    /** A transient failure: try again later, resuming the uploads. */
    data object Retry : SubmissionUploadOutcome

    /** Given up; [error] is what the user is told. */
    data class Failed(val error: ExamSubmissionError) : SubmissionUploadOutcome
}
