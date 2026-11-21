package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.PracticeService
import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeError
import com.salticusteam.vifi.core.model.PracticeQuestion
import com.salticusteam.vifi.core.model.PracticeRequest
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * Serves the [SampleQuestions] as a practice pool and counts the free daily limit in memory, like the server (5 per
 * day; Premium unlimited) — iOS `MockPracticeService`. Questions come in order (deterministic for UI tests).
 */
class FakePracticeService(
    private val isSubscribed: Boolean = false,
    limitReached: Boolean = false,
    private val pool: List<PracticeQuestion> = SampleQuestions.questions("mock-exam").map {
        PracticeQuestion(it, examTitle = "2003 FİNAL", universityName = "Kocaeli Üniversitesi")
    },
    private val latency: Duration = Duration.ZERO,
    /** When non-null, every call throws it. */
    var error: PracticeError? = null,
) : PracticeService {

    private var usedToday = if (limitReached) DAILY_LIMIT else 0

    /** Every request, in order (for tests). */
    val requests = mutableListOf<PracticeRequest>()

    override suspend fun nextQuestions(request: PracticeRequest): PracticeBatch {
        requests += request
        if (latency > Duration.ZERO) delay(latency)
        error?.let { throw it }
        val unseen = pool.filter { it.id !in request.exclude }
        if (isSubscribed) {
            return PracticeBatch(unseen.take(request.count), poolSize = pool.size, remainingToday = null, limitReached = false)
        }
        val allowance = (DAILY_LIMIT - usedToday).coerceAtLeast(0)
        val delivered = unseen.take(minOf(request.count, allowance))
        usedToday += delivered.size
        return PracticeBatch(
            questions = delivered,
            poolSize = pool.size,
            remainingToday = (DAILY_LIMIT - usedToday).coerceAtLeast(0),
            limitReached = delivered.isEmpty() && unseen.isNotEmpty() && allowance == 0,
        )
    }

    companion object {
        const val DAILY_LIMIT = 5
    }
}
