package com.salticusteam.vifi.feature.practice

import com.salticusteam.vifi.core.data.repository.PracticeService
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PoolIndex
import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeError
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeRequest
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.PracticeSelection
import com.salticusteam.vifi.core.model.QuestionAnswer

/** A scripted [PracticeService] (iOS `StubPracticeService`): returns the queued results in order, records requests. */
class StubPracticeService(
    results: List<Result> = emptyList(),
    /** Used once [results] runs dry. */
    private val fallback: Result = Result.Batch(PracticeFixture.batch(emptyList(), remaining = null)),
) : PracticeService {

    sealed interface Result {
        data class Batch(val batch: PracticeBatch) : Result
        data class Failure(val error: PracticeError) : Result
    }

    private val queued = results.toMutableList()
    val requests = mutableListOf<PracticeRequest>()

    override suspend fun nextQuestions(request: PracticeRequest): PracticeBatch {
        requests += request
        val next = if (queued.isEmpty()) fallback else queued.removeAt(0)
        return when (next) {
            is Result.Batch -> next.batch
            is Result.Failure -> throw next.error
        }
    }
}

object PracticeFixture {
    val selection = PracticeSelection(
        subjectId = "topoloji-1",
        examType = ExamType.FINAL,
        scope = PracticeScope.TURKEY,
        courseId = "topoloji-1-kocaeli",
        universityId = "kocaeli-universitesi",
        poolCount = 12,
    )

    fun question(id: String): PracticeQuestion {
        val examId = id.dropLast(4)
        val order = id.takeLast(3).toIntOrNull() ?: 1
        return PracticeQuestion(
            question = ExamQuestion(
                id = id,
                examId = examId,
                order = order,
                label = order.toString(),
                text = "Soru $order?",
                speech = "Soru $order?",
                choices = emptyList(),
                figures = emptyList(),
                answer = QuestionAnswer(text = "Cevap $order", choice = null, speech = null),
                solution = emptyList(),
                topics = emptyList(),
                difficulty = 1,
            ),
            examTitle = "2019 FİNAL",
            universityName = "Bozok Üniversitesi",
        )
    }

    /** A batch of the questions with [ids] (`<examId>-<nnn>`); `remaining == null` is Premium. */
    fun batch(ids: List<String>, remaining: Int?, limitReached: Boolean = false, poolSize: Int = 12) =
        PracticeBatch(ids.map(::question), poolSize, remaining, limitReached)

    fun batchResult(ids: List<String>, remaining: Int?, limitReached: Boolean = false) =
        StubPracticeService.Result.Batch(batch(ids, remaining, limitReached))

    fun index(types: Map<ExamType, Map<String, Int>>) = PoolIndex("topoloji-1", "Topoloji 1", types)
}
