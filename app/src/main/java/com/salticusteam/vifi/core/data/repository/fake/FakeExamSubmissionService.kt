package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ExamSubmissionService
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * In-memory exam submissions for the mock flavor, previews and tests (iOS MockExamSubmissionService): nothing
 * leaves the device. Validates like the live service, then records the submission — once per submission id, like
 * the live service's idempotent submit.
 */
class FakeExamSubmissionService(
    private val latency: Duration = 600.milliseconds,
    /** When non-null, [submit] throws it after the latency. */
    var failure: ExamSubmissionError? = null,
) : ExamSubmissionService {

    private val _submissions = linkedMapOf<String, Pair<String, ExamSubmission>>()

    /** Every accepted submission with its owner id, in order (one entry per submission id). */
    val submissions: List<Pair<String, ExamSubmission>> get() = _submissions.values.toList()

    /** The ids of the accepted submissions, in order. */
    val submissionIds: List<String> get() = _submissions.keys.toList()

    /** How many times [submit] was called (retries included). */
    var submitCallCount = 0
        private set

    /** The submission ids whose uploads were discarded. */
    val discardedIds = mutableListOf<String>()

    override suspend fun submit(submission: ExamSubmission, ownerId: String, submissionId: String) {
        submitCallCount++
        if (ownerId.isEmpty()) throw ExamSubmissionError.NotSignedIn
        if (!submission.isValid) throw ExamSubmissionError.InvalidSubmission
        delay(latency)
        failure?.let { throw it }
        _submissions[submissionId] = ownerId to submission
    }

    override suspend fun discardUploads(submission: ExamSubmission, ownerId: String, submissionId: String) {
        discardedIds += submissionId
    }
}
