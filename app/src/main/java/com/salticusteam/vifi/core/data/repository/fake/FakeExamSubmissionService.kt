package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ExamSubmissionService
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * In-memory exam submissions for the mock flavor, previews and tests (iOS MockExamSubmissionService): nothing
 * leaves the device. Validates like the live service, then records the submission.
 */
class FakeExamSubmissionService(
    private val latency: Duration = 600.milliseconds,
    /** When non-null, [submit] throws it after the latency. */
    var failure: ExamSubmissionError? = null,
) : ExamSubmissionService {

    private val _submissions = mutableListOf<Pair<String, ExamSubmission>>()

    /** Every accepted submission with its owner id, in order. */
    val submissions: List<Pair<String, ExamSubmission>> get() = _submissions

    override suspend fun submit(submission: ExamSubmission, ownerId: String) {
        if (ownerId.isEmpty()) throw ExamSubmissionError.NotSignedIn
        if (!submission.isValid) throw ExamSubmissionError.InvalidSubmission
        delay(latency)
        failure?.let { throw it }
        _submissions += ownerId to submission
    }
}
