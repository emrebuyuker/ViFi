package com.salticusteam.vifi.feature.examviewer

import coil.network.HttpException
import com.salticusteam.vifi.core.data.files.ArchiveIOException
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamFileError
import java.io.IOException
import okhttp3.CacheControl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class PageErrorMessageTest {

    private fun httpError(code: Int, cacheControl: CacheControl? = null): HttpException {
        val request = Request.Builder().url("https://firebasestorage.googleapis.com/v0/b/x/o/p.jpg")
            .apply { cacheControl?.let(::cacheControl) }
            .build()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("m").build()
        return HttpException(response)
    }

    @Test
    fun `pageErrorMessage when offline cache miss reports no connection`() {
        assertEquals(ArchiveError.Offline.userMessage, httpError(504, CacheControl.FORCE_CACHE).pageErrorMessage())
    }

    @Test
    fun `pageErrorMessage when the server times out reports the file as unavailable`() {
        assertEquals(ArchiveError.FileUnavailable(504).userMessage, httpError(504).pageErrorMessage())
    }

    @Test
    fun `pageErrorMessage maps auth, wrapped, network and decode failures`() {
        assertEquals(ArchiveError.PermissionDenied.userMessage, httpError(403).pageErrorMessage())
        assertEquals(ArchiveError.FileUnavailable(404).userMessage, httpError(404).pageErrorMessage())
        assertEquals(ArchiveError.NotFound.userMessage, ArchiveIOException(ArchiveError.NotFound).pageErrorMessage())
        assertEquals(ArchiveError.Offline.userMessage, IOException("dns").pageErrorMessage())
        assertEquals(ExamFileError.UndecodableImage.userMessage, IllegalStateException().pageErrorMessage())
    }
}
