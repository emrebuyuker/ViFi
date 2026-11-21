package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.ExamQuestion

/**
 * Reads the published questions of a structured exam (4.2.0, iOS `ExamQuestionReading`): the only query is
 * `questions where examId == X order by order` on the named `vifi` Firestore (~10–30 reads); never prefetched.
 * Throws `ArchiveError` on failure. Live: Firestore (Hat A fills `UnavailableExamQuestionReader`'s place); mock,
 * previews and tests: `FakeExamQuestionReader`.
 */
interface ExamQuestionReader {
    /** The exam's questions in reading order (parsed by `ExamQuestionParser`); empty when it has none. */
    suspend fun questions(examId: String): List<ExamQuestion>
}
