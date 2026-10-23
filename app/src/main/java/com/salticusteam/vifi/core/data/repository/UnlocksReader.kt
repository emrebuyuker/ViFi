package com.salticusteam.vifi.core.data.repository

/** Reads the set of exams a user has unlocked (`users/<uid>/unlocks/<examId>`). */
interface UnlocksReader {
    /** The ids of the exams [uid] has unlocked. */
    suspend fun unlockedExamIds(uid: String): Set<String>
}
