package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.model.PoolIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Remembers each subject's index for the session, so reopening a course (or a second course of the same subject)
 * costs no further read (iOS `CachingPracticeIndexReader`). A failed read is not remembered; an absent index
 * (`null`) is. Concurrent callers of one subject share a single read; if its owner is cancelled, they read again.
 *
 * Added in 4.2.0 — session cache of the practice-pool index.
 */
class CachingPoolIndexReader(private val base: PoolIndexReader) : PoolIndexReader {

    private val mutex = Mutex()
    private val reads = mutableMapOf<String, CompletableDeferred<PoolIndex?>>()

    override suspend fun index(subjectId: String): PoolIndex? {
        while (true) {
            val (read, isOwner) = mutex.withLock {
                val running = reads[subjectId]
                if (running != null) {
                    running to false
                } else {
                    CompletableDeferred<PoolIndex?>().also { reads[subjectId] = it } to true
                }
            }
            if (isOwner) return ownRead(subjectId, read)
            try {
                return read.await()
            } catch (e: CancellationException) {
                // Either this caller was cancelled, or the owner was: only the second one reads again.
                if (!read.isCancelled) throw e
            }
        }
    }

    private suspend fun ownRead(subjectId: String, read: CompletableDeferred<PoolIndex?>): PoolIndex? =
        try {
            base.index(subjectId).also { read.complete(it) }
        } catch (e: Throwable) {
            // Not remembered: the next caller reads again. Waiting callers see the same failure.
            mutex.withLock { reads.remove(subjectId) }
            if (e is CancellationException) read.cancel() else read.completeExceptionally(e)
            throw e
        }
}
