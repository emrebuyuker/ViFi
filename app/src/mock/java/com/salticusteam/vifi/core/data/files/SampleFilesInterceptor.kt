package com.salticusteam.vifi.core.data.files

import android.content.res.AssetManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Answers the sample archive's file URLs (`…/2022-final-2.jpg`, `…/2023-vize.pdf`) with the bundled sample
 * exam (iOS `sample-page-N.jpg` / `sample-exam.pdf`), so the viewers work offline in the mock build.
 */
class SampleFilesInterceptor(private val assets: AssetManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val name = request.url.pathSegments.lastOrNull().orEmpty()
        val (asset, mediaType) = when {
            name.endsWith(".pdf") -> "samples/sample-exam.pdf" to "application/pdf"
            else -> {
                val page = PAGE_NUMBER.find(name)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, PAGE_COUNT) ?: 1
                "samples/sample-page-$page.jpg" to "image/jpeg"
            }
        }
        val bytes = assets.open(asset).use { it.readBytes() }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody(mediaType.toMediaType()))
            .build()
    }

    private companion object {
        const val PAGE_COUNT = 3
        val PAGE_NUMBER = Regex("""-(\d+)\.jpg$""")
    }
}
