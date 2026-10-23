package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.ExamUnlockResult

/**
 * Unlocks a locked exam by spending coins. The actual debit happens server-side (the `unlockExam` Cloud
 * Function), because a client cannot be trusted to charge its own wallet honestly.
 */
interface ExamUnlockService {
    /**
     * Unlocks [examId], spending `ExamLock.UNLOCK_COST` coins (unless already unlocked).
     *
     * @throws com.salticusteam.vifi.core.model.ExamUnlockError notably `InsufficientCoins` when the balance
     * is too low.
     */
    suspend fun unlock(examId: String): ExamUnlockResult
}
