package com.salticusteam.vifi.core.data.archive

import com.salticusteam.vifi.core.data.preferences.fake.FakeUserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.testing.MutableClock
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivePrefetcherTest {

    private lateinit var repository: PrefetchRecordingRepository
    private lateinit var preferences: FakeUserPreferencesRepository
    private lateinit var clock: MutableClock
    private lateinit var sut: ArchivePrefetcher

    @Before
    fun setUp() {
        repository = PrefetchRecordingRepository()
        preferences = FakeUserPreferencesRepository()
        clock = MutableClock()
        sut = ArchivePrefetcher(repository, preferences, clock)
    }

    @Test
    fun `prefetchIfNeeded when never warmed prefetches and records the time`() = runTest {
        sut.prefetchIfNeeded()

        assertEquals(1, repository.prefetchCallCount)
        assertEquals(clock.millis(), preferences.archivePrefetchedAt.first())
    }

    @Test
    fun `prefetchIfNeeded within the throttle does not prefetch again`() = runTest {
        sut.prefetchIfNeeded()
        clock.advance(Duration.ofHours(11).plusMinutes(59))

        sut.prefetchIfNeeded()

        assertEquals(1, repository.prefetchCallCount)
    }

    @Test
    fun `prefetchIfNeeded after the throttle prefetches again`() = runTest {
        sut.prefetchIfNeeded()
        clock.advance(Duration.ofHours(12))

        sut.prefetchIfNeeded()

        assertEquals(2, repository.prefetchCallCount)
        assertEquals(clock.millis(), preferences.archivePrefetchedAt.first())
    }

    @Test
    fun `prefetchIfNeeded when offline swallows the error and records nothing`() = runTest {
        repository.error = ArchiveError.Offline

        sut.prefetchIfNeeded()

        assertNull(preferences.archivePrefetchedAt.first())

        // The next launch retries.
        repository.error = null
        sut.prefetchIfNeeded()
        assertEquals(2, repository.prefetchCallCount)
        assertEquals(clock.millis(), preferences.archivePrefetchedAt.first())
    }

    @Test
    fun `prefetchIfNeeded while a warm is in flight skips the second call`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val first = launch { sut.prefetchIfNeeded() }
        runCurrent()

        sut.prefetchIfNeeded()
        assertEquals(1, repository.prefetchCallCount)

        gate.complete(Unit)
        first.join()
        assertEquals(clock.millis(), preferences.archivePrefetchedAt.first())
    }

    @Test
    fun `prefetchIfNeeded when cancelled records nothing and the next call retries`() = runTest {
        repository.gate = CompletableDeferred()
        val job = launch { sut.prefetchIfNeeded() }
        runCurrent()

        job.cancel()
        job.join()
        assertNull(preferences.archivePrefetchedAt.first())

        repository.gate = null
        sut.prefetchIfNeeded()
        assertEquals(2, repository.prefetchCallCount)
        assertEquals(clock.millis(), preferences.archivePrefetchedAt.first())
    }

    /** Counts warms; [gate] holds a warm in flight, [error] makes it fail. */
    private class PrefetchRecordingRepository : ArchiveRepository {
        var prefetchCallCount = 0
            private set
        var gate: CompletableDeferred<Unit>? = null
        var error: ArchiveError? = null

        override suspend fun items(path: ArchivePath): List<ArchiveItem> = emptyList()

        override suspend fun document(path: ArchivePath): ExamDocument = throw ArchiveError.NotFound

        override suspend fun prefetchArchive() {
            prefetchCallCount++
            gate?.await()
            error?.let { throw it }
        }
    }
}
