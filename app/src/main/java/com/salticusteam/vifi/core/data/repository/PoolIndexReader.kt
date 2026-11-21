package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.PoolIndex

/**
 * Reads a subject's practice-pool summary `questionPoolIndex/<subjectId>` (4.2.0, iOS `PracticeIndexReading`): one
 * read tells the course screen whether to offer "Sınava gir" and the setup how many questions each type and scope
 * has. Throws `ArchiveError` on failure; callers keep the entry hidden on any failure.
 */
interface PoolIndexReader {
    /** The index of [subjectId], or `null` when the subject has no practice questions (no document). */
    suspend fun index(subjectId: String): PoolIndex?
}
