package com.salticusteam.vifi.core.data.files

import com.salticusteam.vifi.core.data.repository.RequestAuthorizer
import com.salticusteam.vifi.core.model.ArchiveError
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StorageAuthInterceptorTest {

    private val storageUrl =
        "https://firebasestorage.googleapis.com/v0/b/vifi-831a8.appspot.com/o/imagess%2Fa.jpg?alt=media"

    @Test
    fun `storage request goes to the canonical url with the credentials`() {
        val server = ScriptedServer(200)
        val authorizer = RecordingAuthorizer()

        client(authorizer, server).newCall(Request.Builder().url("$storageUrl&token=old").build()).execute().close()

        val sent = server.requests.single()
        assertEquals(storageUrl, sent.url.toString())
        assertEquals("Firebase id-token-1", sent.header("Authorization"))
        assertEquals("app-check", sent.header("X-Firebase-AppCheck"))
        assertEquals(listOf(storageUrl to false), authorizer.calls)
    }

    @Test
    fun `a rejected request is retried once with a fresh id token`() {
        val server = ScriptedServer(401, 200)
        val authorizer = RecordingAuthorizer()

        val response = client(authorizer, server).newCall(Request.Builder().url(storageUrl).build()).execute()

        assertEquals(200, response.code)
        assertEquals(listOf(false, true), authorizer.calls.map { it.second })
        assertEquals("Firebase id-token-2", server.requests.last().header("Authorization"))
        response.close()
    }

    @Test
    fun `a second rejection fails with PermissionDenied`() {
        val server = ScriptedServer(403, 403)

        assertArchiveError(ArchiveError.PermissionDenied) {
            client(RecordingAuthorizer(), server).newCall(Request.Builder().url(storageUrl).build()).execute()
        }
        assertEquals(2, server.requests.size)
    }

    @Test
    fun `an authorizer failure surfaces as its archive error`() {
        val authorizer = RecordingAuthorizer(error = ArchiveError.Offline)

        assertArchiveError(ArchiveError.Offline) {
            client(authorizer, ScriptedServer(200)).newCall(Request.Builder().url(storageUrl).build()).execute()
        }
    }

    @Test
    fun `other hosts pass through without credentials`() {
        val server = ScriptedServer(200)
        val authorizer = RecordingAuthorizer()

        client(authorizer, server).newCall(Request.Builder().url("https://example.com/a.jpg").build()).execute().close()

        assertNull(server.requests.single().header("Authorization"))
        assertTrue(authorizer.calls.isEmpty())
    }

    private fun client(authorizer: RequestAuthorizer, server: ScriptedServer) = OkHttpClient.Builder()
        .addInterceptor(StorageAuthInterceptor(authorizer))
        .addInterceptor(server)
        .build()

    private fun assertArchiveError(expected: ArchiveError, block: () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: ArchiveIOException) {
            assertEquals(expected, e.error)
        }
    }

    private class RecordingAuthorizer(private val error: ArchiveError? = null) : RequestAuthorizer {
        val calls = mutableListOf<Pair<String, Boolean>>()

        override suspend fun authorizationHeaders(url: String, forceRefresh: Boolean): Map<String, String> {
            calls += url to forceRefresh
            error?.let { throw it }
            return mapOf("Authorization" to "Firebase id-token-${calls.size}", "X-Firebase-AppCheck" to "app-check")
        }
    }

    /** Terminal interceptor answering with the scripted status codes in order; records what it received. */
    private class ScriptedServer(vararg codes: Int) : Interceptor {
        private val codes = codes.toMutableList()
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            requests += chain.request()
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(codes.removeAt(0))
                .message("")
                .body("".toResponseBody())
                .build()
        }
    }
}
