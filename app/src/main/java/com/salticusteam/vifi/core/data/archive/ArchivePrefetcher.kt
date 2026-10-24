package com.salticusteam.vifi.core.data.archive

import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import java.time.Clock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Warms the archive's offline cache after sign-in, so browsing a branch the user has not visited still works
 * without a connection (iOS ArchivePrefetcher).
 *
 * Firestore has no `keepSynced`: only the queries a user actually runs are cached, so an offline list of an
 * unvisited branch comes back empty. A one-shot fetch of every published document fixes that (see
 * [ArchiveRepository.prefetchArchive]). The warm is:
 * - **best-effort** — a failure (typically offline) is ignored and retried on the next launch;
 * - **throttled** — a successful warm is not repeated until [THROTTLE_MILLIS] has passed, so the bulk read is not
 *   billed on every launch while the cached copy stays fresh enough for offline use.
 *
 * Sources with nothing remote to warm (sample data) do nothing in `prefetchArchive()`, so this is safe in every
 * flavor.
 */
@Singleton
class ArchivePrefetcher @Inject constructor(
    private val repository: ArchiveRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {

    // Internal state
    /** Guards against a second warm starting while one is already in flight. */
    private val isPrefetching = AtomicBoolean(false)

    // Public API
    /**
     * Warms the cache when it is stale and no warm is already running; returns once the warm finishes, or at once
     * when it is skipped. Call it from a scope tied to the signed-in archive so a sign-out cancels it.
     */
    suspend fun prefetchIfNeeded() {
        if (!isPrefetching.compareAndSet(false, true)) return
        try {
            if (!isStale()) return
            repository.prefetchArchive()
            preferences.setArchivePrefetchedAt(clock.millis())
            Timber.d("Archive prefetch complete")
        } catch (e: CancellationException) {
            // Cancelled (e.g. sign-out before it finished): nothing recorded, so the next launch retries.
            throw e
        } catch (e: Exception) {
            // Typically offline (`Source.SERVER` throws): nothing recorded, so the next launch retries.
            Timber.i(e, "Archive prefetch skipped")
        } finally {
            isPrefetching.set(false)
        }
    }

    // Helpers
    /** Whether the last successful warm is old enough — or never happened — to warm again. */
    private suspend fun isStale(): Boolean {
        val last = preferences.archivePrefetchedAt.first() ?: return true
        return clock.millis() - last >= THROTTLE_MILLIS
    }

    companion object {
        /** A successful warm is not repeated for this long (12 hours). */
        const val THROTTLE_MILLIS: Long = 12 * 60 * 60 * 1_000L
    }
}
