package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ExamQuestionReader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamQuestion
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * In-memory [ExamQuestionReader] for the mock flavor, previews and tests (iOS `MockExamQuestionReader`): every exam id
 * gets the [SampleQuestions] set unless [questionsByExam] says otherwise. Set [error] to make every call throw it.
 */
class FakeExamQuestionReader(
    private val questionsByExam: Map<String, List<ExamQuestion>> = emptyMap(),
    private val latency: Duration = Duration.ZERO,
    /** When non-null, every call throws it. */
    var error: ArchiveError? = null,
) : ExamQuestionReader {

    /** Every requested exam id, in order (for tests). */
    val requestedExamIds = mutableListOf<String>()

    override suspend fun questions(examId: String): List<ExamQuestion> {
        requestedExamIds += examId
        if (latency > Duration.ZERO) delay(latency)
        error?.let { throw it }
        return questionsByExam[examId] ?: SampleQuestions.questions(examId)
    }
}
