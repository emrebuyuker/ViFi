package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamSubmissionError
import kotlinx.coroutines.flow.Flow

/**
 * Sends drafts in the background, so a submission completes — or fails and says so — even when the user leaves the
 * form, the app is backgrounded or its process is killed (iOS keeps the upload in-process; Android needs a
 * durable job for the same promise). The draft's pages must already be in the [SubmissionDraftStore].
 */
interface SubmissionQueue {
    /**
     * Queues [pending] for upload from draft [draftId]'s pages. Returns once the job is durably recorded; throws
     * `ExamSubmissionError` when it cannot be (the manifest could not be stored). Queuing a draft whose upload is
     * still running does nothing.
     */
    suspend fun enqueue(draftId: String, pending: PendingSubmission)

    /**
     * The status of submit [submissionId] of draft [draftId], while collected. Collecting marks the draft as
     * watched: while its form is on screen, the form reports the outcome instead of a notification.
     */
    fun status(draftId: String, submissionId: String): Flow<SubmissionStatus>
}

/** Where a queued submit stands. */
sealed interface SubmissionStatus {
    /** Nothing is known about this submit (never queued, or its record was pruned). */
    data object Unknown : SubmissionStatus

    /** Waiting (for a connection or a retry) or uploading. */
    data object Sending : SubmissionStatus

    data object Sent : SubmissionStatus

    data class Failed(val error: ExamSubmissionError) : SubmissionStatus
}
