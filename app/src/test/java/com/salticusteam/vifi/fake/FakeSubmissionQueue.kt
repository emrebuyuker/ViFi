package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.submission.PendingSubmission
import com.salticusteam.vifi.core.data.submission.SubmissionDraftStore
import com.salticusteam.vifi.core.data.submission.SubmissionQueue
import com.salticusteam.vifi.core.data.submission.SubmissionStatus
import com.salticusteam.vifi.core.data.submission.SubmissionUploadOutcome
import com.salticusteam.vifi.core.data.submission.SubmissionUploader
import com.salticusteam.vifi.core.model.ExamSubmissionError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs a queued submit in-process on [scope] with the real [SubmissionUploader] as a single, last attempt (no
 * backoff), so a ViewModel test sees the same outcome a worker would report.
 */
class FakeSubmissionQueue(
    private val store: SubmissionDraftStore,
    private val uploader: SubmissionUploader,
    private val scope: CoroutineScope,
) : SubmissionQueue {

    /** Thrown by the next [enqueue] instead of queuing. */
    var enqueueFailure: ExamSubmissionError? = null

    val enqueued = mutableListOf<PendingSubmission>()

    /** Statuses by submission id; tests may set one directly (e.g. a run that a killed process left behind). */
    val statuses = MutableStateFlow<Map<String, SubmissionStatus>>(emptyMap())

    override suspend fun enqueue(draftId: String, pending: PendingSubmission) {
        enqueueFailure?.let {
            enqueueFailure = null
            throw it
        }
        store.writeManifest(draftId, pending)
        enqueued += pending
        set(pending.submissionId, SubmissionStatus.Sending)
        scope.launch {
            val status = when (val outcome = uploader.upload(draftId, isLastAttempt = true)) {
                SubmissionUploadOutcome.Succeeded -> SubmissionStatus.Sent
                SubmissionUploadOutcome.Retry -> SubmissionStatus.Sending
                is SubmissionUploadOutcome.Failed -> SubmissionStatus.Failed(outcome.error)
            }
            set(pending.submissionId, status)
        }
    }

    override fun status(draftId: String, submissionId: String): Flow<SubmissionStatus> =
        statuses.map { it[submissionId] ?: SubmissionStatus.Unknown }

    fun set(submissionId: String, status: SubmissionStatus) {
        statuses.update { it + (submissionId to status) }
    }
}
