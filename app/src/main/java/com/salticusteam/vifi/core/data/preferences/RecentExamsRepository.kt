package com.salticusteam.vifi.core.data.preferences

import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.RecentExam
import kotlinx.coroutines.flow.Flow

/** Most recently opened exams, newest first, at most [RecentExam.MAX_COUNT] (iOS RecentExamsStore). */
interface RecentExamsRepository {

    val exams: Flow<List<RecentExam>>

    /** Moves the exam to the top, replacing any earlier entry for [path]. */
    suspend fun record(path: ArchivePath, kind: ExamKind, at: Long = System.currentTimeMillis())

    /** Removes any entry for [path] (e.g. an exam that no longer exists); writes only when something changed. */
    suspend fun remove(path: ArchivePath)

    suspend fun clear()
}
