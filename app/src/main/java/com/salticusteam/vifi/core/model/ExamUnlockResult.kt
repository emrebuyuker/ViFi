package com.salticusteam.vifi.core.model

// Added in 2.5 — the outcome of unlocking an exam.
data class ExamUnlockResult(
    /** The user's balance afterwards. */
    val balance: Int,
    /** Whether it was already unlocked (no coins were spent). */
    val alreadyUnlocked: Boolean,
)
