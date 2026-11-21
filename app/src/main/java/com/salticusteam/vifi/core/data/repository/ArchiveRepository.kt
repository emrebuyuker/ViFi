package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Reads the exam archive. Throws `ArchiveError` on failure.
 *
 * Has several implementations (Realtime Database, Firestore, [fake.FakeArchiveRepository]), which is why
 * [prefetchArchive], [archiveChanges] and [subjectId] carry default bodies (mirrors the iOS protocol extension).
 */
interface ArchiveRepository {
    /** Entries listed under [path] (universities at the root), sorted for display. */
    suspend fun items(path: ArchivePath): List<ArchiveItem>

    /** The files of the exam at [path] (a depth-5 path). */
    suspend fun document(path: ArchivePath): ExamDocument

    /**
     * Warms the source's offline cache with the whole archive, so later reads work without a connection.
     * Best-effort, called off the critical path: callers ignore failures and retry. Sources that keep
     * themselves available offline or have nothing remote to warm (sample data, test doubles) do nothing.
     */
    suspend fun prefetchArchive() {}

    /**
     * Emits whenever the archive changes, including when fresh server data replaces the copy cached by a
     * previous session. Lists re-read their entries on each element; collection ends when the collector is
     * cancelled. Sources without live updates (sample data, test doubles) never emit.
     */
    fun archiveChanges(): Flow<Unit> = emptyFlow()

    /**
     * The subject of the course at [path] (4.2.0, keys the practice pools); `null` when the course has none or it
     * cannot be read. Never throws (cancellation aside): the practice entry simply stays hidden. Sources without a
     * cheaper lookup find the course among its parent's entries (iOS protocol extension); the Firestore source may
     * override it with a single course-node read.
     */
    suspend fun subjectId(forCourse: ArchivePath): String? {
        val parent = forCourse.parent ?: return null
        val siblings = try {
            items(parent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return siblings.firstOrNull { it.path == forCourse }?.subjectId
    }
}
