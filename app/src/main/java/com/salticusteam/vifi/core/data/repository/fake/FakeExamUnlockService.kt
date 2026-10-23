package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.ExamUnlockService
import com.salticusteam.vifi.core.model.ExamUnlockError
import com.salticusteam.vifi.core.model.ExamUnlockResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** In-memory exam unlocks for the mock flavor, previews and tests (iOS MockExamUnlockService). */
class FakeExamUnlockService(
    private val latency: Duration = 300.milliseconds,
    private val failure: ExamUnlockError? = null,
) : ExamUnlockService {

    private val _unlockedExamIds = mutableListOf<String>()
    val unlockedExamIds: List<String> get() = _unlockedExamIds

    override suspend fun unlock(examId: String): ExamUnlockResult {
        delay(latency)
        failure?.let { throw it }
        _unlockedExamIds += examId
        return ExamUnlockResult(balance = 20, alreadyUnlocked = false)
    }
}
