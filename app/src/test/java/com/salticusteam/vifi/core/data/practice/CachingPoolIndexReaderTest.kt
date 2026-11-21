package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.data.repository.fake.FakePoolIndexReader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PoolIndex
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class CachingPoolIndexReaderTest {

    private val sample = PoolIndex("s1", "Ders", mapOf(ExamType.VIZE to mapOf("tr" to 1)))

    @Test
    fun `a subject is read once and an absent index is remembered, but not a failure`() = runTest {
        val base = FakePoolIndexReader(mapOf("s1" to sample))
        val sut = CachingPoolIndexReader(base)

        assertEquals(sample, sut.index("s1"))
        assertEquals(sample, sut.index("s1"))
        assertNull(sut.index("none"))
        assertNull(sut.index("none"))
        assertEquals(2, base.readCount)

        base.error = ArchiveError.Offline
        try {
            sut.index("later")
            fail("expected a failure")
        } catch (e: ArchiveError) {
            assertEquals(ArchiveError.Offline, e)
        }
        base.error = null
        sut.index("later")
        assertEquals(4, base.readCount)
    }

    @Test
    fun `concurrent callers of one subject share a single read`() = runTest {
        val base = FakePoolIndexReader(mapOf("s1" to sample), latency = 100.milliseconds)
        val sut = CachingPoolIndexReader(base)

        val reads = List(3) { async { sut.index("s1") } }
        advanceUntilIdle()

        assertEquals(List(3) { sample }, reads.map { it.await() })
        assertEquals(1, base.readCount)
    }
}
