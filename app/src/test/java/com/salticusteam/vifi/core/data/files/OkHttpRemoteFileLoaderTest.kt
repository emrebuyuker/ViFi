package com.salticusteam.vifi.core.data.files

import com.salticusteam.vifi.core.model.ArchiveError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OkHttpRemoteFileLoaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private var imageCachesCleared = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `localFile downloads once and serves the local copy afterwards`() = runTest {
        server.enqueue(MockResponse().setBody("%PDF-1.4 sample"))
        val sut = loader()
        val url = server.url("/exam.pdf").toString()

        val first = sut.localFile(url, "2023 Vize.pdf")
        val second = sut.localFile(url, "2023 Vize.pdf")

        assertEquals("%PDF-1.4 sample", first.readText())
        assertEquals("2023 Vize.pdf", first.name)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `concurrent requests for the same url share one download`() = runTest {
        server.enqueue(MockResponse().setBody("bytes").setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        val sut = loader()
        val url = server.url("/shared.pdf").toString()

        val files = (1..3).map { async(Dispatchers.Default) { sut.localFile(url, "a.pdf") } }.awaitAll()

        assertEquals(1, files.toSet().size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a non 2xx answer fails with FileUnavailable and writes nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val sut = loader()

        assertThrows(ArchiveError.FileUnavailable(404)) { sut.localFile(server.url("/x.pdf").toString(), "x.pdf") }
        assertTrue(filesDir().walkTopDown().none { it.isFile })
    }

    @Test
    fun `a 401 or 403 answer fails with PermissionDenied`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val sut = loader()

        assertThrows(ArchiveError.PermissionDenied) { sut.localFile(server.url("/x.pdf").toString(), "x.pdf") }
    }

    @Test
    fun `an unreachable host fails with Offline`() = runTest {
        val url = server.url("/x.pdf").toString()
        server.shutdown()
        val sut = loader()

        assertThrows(ArchiveError.Offline) { sut.localFile(url, "x.pdf") }
    }

    @Test
    fun `purge deletes the files and clears the image caches`() = runTest {
        server.enqueue(MockResponse().setBody("one"))
        server.enqueue(MockResponse().setBody("two"))
        val sut = loader()
        val url = server.url("/x.pdf").toString()
        val file = sut.localFile(url, "x.pdf")

        sut.purge()

        assertFalse(file.exists())
        assertEquals(1, imageCachesCleared)
        // The next open downloads again.
        assertEquals("two", sut.localFile(url, "x.pdf").readText())
    }

    private fun filesDir() = tmp.root.resolve("Files")

    private fun loader() = OkHttpRemoteFileLoader(
        client = OkHttpClient(),
        filesDirectory = filesDir(),
        // Like the app scope: a supervisor, so a failed download is only seen by the callers awaiting it.
        scope = CoroutineScope(SupervisorJob()),
        ioDispatcher = Dispatchers.IO,
        clock = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC),
        clearImageCaches = { imageCachesCleared++ },
    )

    private suspend fun assertThrows(expected: ArchiveError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: ArchiveError) {
            assertEquals(expected, e)
        }
    }
}
