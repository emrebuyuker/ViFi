package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.PracticeBatch
import com.salticusteam.vifi.core.model.PracticeRequest

/**
 * Hands out practice questions through the `nextPracticeQuestions` callable (4.2.0, iOS `PracticeServicing`). The
 * server counts the free daily limit (5 per Istanbul day; Premium unlimited), so practice questions are never read
 * from `questions` directly. The live implementation attaches the [SubscriptionProofProvider]'s proof to every call
 * (`PracticeCalls.payload`). Throws `PracticeError` on failure.
 */
interface PracticeService {
    suspend fun nextQuestions(request: PracticeRequest): PracticeBatch
}
