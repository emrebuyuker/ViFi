package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PoolIndex
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * In-memory [PoolIndexReader] (iOS `MockPracticeIndexReader`): only [SAMPLE_SUBJECT_ID] (the mock archive's TOPOLOJİ 1)
 * has an index by default. Set [error] to make every call throw it; [readCount] counts reads (for caching tests).
 */
class FakePoolIndexReader(
    private val indexes: Map<String, PoolIndex> = mapOf(SAMPLE_SUBJECT_ID to SAMPLE),
    private val latency: Duration = Duration.ZERO,
    var error: ArchiveError? = null,
) : PoolIndexReader {

    var readCount = 0
        private set

    override suspend fun index(subjectId: String): PoolIndex? {
        readCount++
        if (latency > Duration.ZERO) delay(latency)
        error?.let { throw it }
        return indexes[subjectId]
    }

    companion object {
        const val SAMPLE_SUBJECT_ID = "topoloji-1"

        /** The course id of the mock archive's TOPOLOJİ 1 (`FakeArchiveRepository` structured sample). */
        const val SAMPLE_COURSE_ID = "kocaeli-muhendislik-matematik-topoloji-1"

        /** The mock profile's university (`FakeProfileService.SAMPLE_PROFILE`), so "Üniversitem" has questions. */
        const val SAMPLE_UNIVERSITY_ID = "bogazici-universitesi"

        /** Final has all three scopes; vize has a single question in Türkiye geneli only (P4). iOS parity. */
        val SAMPLE = PoolIndex(
            subjectId = SAMPLE_SUBJECT_ID,
            name = "Topoloji 1",
            types = mapOf(
                ExamType.FINAL to mapOf("c-$SAMPLE_COURSE_ID" to 3, "u-$SAMPLE_UNIVERSITY_ID" to 2, "tr" to 3),
                ExamType.VIZE to mapOf("tr" to 1),
            ),
        )
    }
}
