package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Reads the exam archive. Throws `ArchiveError` on failure.
 *
 * Has several implementations (Realtime Database, Firestore, [fake.FakeArchiveRepository]), which is why
 * [prefetchArchive] and [archiveChanges] carry default bodies (mirrors the iOS protocol extension).
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
}
