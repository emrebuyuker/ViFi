package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.testing.MutableClock
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSubmissionDraftStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock()
    private lateinit var root: File
    private lateinit var sut: FileSubmissionDraftStore

    private val pending = PendingSubmission(
        submissionId = "sub1", ownerId = "uid", university = "Ege", faculty = "Müh", department = "Bil",
        lesson = "BLG 101", examName = "Vize", year = 2024, kind = "JPG", fileCount = 2, estimatedTier = "newExam",
    )

    @Before
    fun setUp() {
        root = folder.newFolder("drafts")
        sut = FileSubmissionDraftStore(root, clock, dispatcher)
    }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(dispatcher) { body() }

    @Test
    fun `pages are read back in order after being written in parts`() = test {
        val id = sut.newDraftId()
        sut.writePages(id, listOf(SubmissionFile(byteArrayOf(2), 2)))
        sut.writePages(id, listOf(SubmissionFile(byteArrayOf(1), 1), SubmissionFile(byteArrayOf(10), 10)))

        assertEquals(
            listOf(SubmissionFile(byteArrayOf(1), 1), SubmissionFile(byteArrayOf(2), 2), SubmissionFile(byteArrayOf(10), 10)),
            sut.readPages(id),
        )
    }

    @Test
    fun `clearPages keeps the manifest`() = test {
        val id = sut.newDraftId()
        sut.writePages(id, listOf(SubmissionFile(byteArrayOf(1), 1)))
        sut.writeManifest(id, pending)

        sut.clearPages(id)

        assertTrue(sut.readPages(id).isEmpty())
        assertEquals(pending, sut.readManifest(id))
    }

    @Test
    fun `a missing draft reads as empty`() = test {
        assertTrue(sut.readPages("unknown").isEmpty())
        assertNull(sut.readManifest("unknown"))
    }

    @Test
    fun `discardIfNotQueued keeps a queued draft and deletes an unqueued one`() = test {
        val queued = sut.newDraftId()
        val unqueued = sut.newDraftId()
        sut.writePages(queued, listOf(SubmissionFile(byteArrayOf(1), 1)))
        sut.writeManifest(queued, pending)
        sut.writePages(unqueued, listOf(SubmissionFile(byteArrayOf(1), 1)))

        sut.discardIfNotQueued(queued)
        sut.discardIfNotQueued(unqueued)

        assertEquals(1, sut.readPages(queued).size)
        assertFalse(File(root, unqueued).exists())
    }

    @Test
    fun `pruneStale removes old abandoned drafts but keeps the current and recent ones`() = test {
        val current = sut.newDraftId()
        val abandoned = sut.newDraftId()
        val recent = sut.newDraftId()
        val queued = sut.newDraftId()
        for (id in listOf(current, abandoned, recent, queued)) sut.writePages(id, listOf(SubmissionFile(byteArrayOf(1), 1)))
        sut.writeManifest(queued, pending)
        clock.now = Instant.ofEpochMilli(File(root, recent).lastModified())
        val old = clock.millis() - FileSubmissionDraftStore.DRAFT_MAX_AGE_MILLIS
        for (id in listOf(current, abandoned, queued)) File(root, id).setLastModified(old)

        sut.pruneStale(keep = current)

        assertTrue(File(root, current).exists())
        assertFalse(File(root, abandoned).exists())
        assertTrue(File(root, recent).exists())
        // A queued upload's draft is given a month.
        assertTrue(File(root, queued).exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a draft id that is not a plain id is refused`() = test {
        sut.readPages("../escape")
    }
}
