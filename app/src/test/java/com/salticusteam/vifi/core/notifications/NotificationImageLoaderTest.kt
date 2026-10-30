package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.model.NotificationImage
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The push image travels on a plain client: no credential may reach the signed URL's host. */
class NotificationImageLoaderTest {

    private lateinit var server: MockWebServer

    private val loader = NotificationImageLoader(
        OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build(),
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun url() = server.url("/notificationImages/n-1.jpg?X-Goog-Signature=ab12").toString()

    @Test
    fun `fetch returns the image bytes and sends no credentials`() {
        val image = byteArrayOf(1, 2, 3)
        server.enqueue(MockResponse().setBody(Buffer().write(image)))

        assertArrayEquals(image, loader.fetch(url()))
        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Firebase-AppCheck"))
        assertEquals("/notificationImages/n-1.jpg?X-Goog-Signature=ab12", request.path)
    }

    @Test
    fun `fetch accepts an image of exactly the size cap`() {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(NotificationImage.MAX_BYTES))))

        assertEquals(NotificationImage.MAX_BYTES, loader.fetch(url())?.size)
    }

    @Test
    fun `fetch drops an image over the size cap, with or without a length`() {
        val tooLarge = ByteArray(NotificationImage.MAX_BYTES + 1)
        server.enqueue(MockResponse().setBody(Buffer().write(tooLarge)))
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(tooLarge), 64 * 1024))

        assertNull(loader.fetch(url()))
        assertNull(loader.fetch(url()))
    }

    @Test
    fun `fetch drops a failed response`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("expired"))

        assertNull(loader.fetch(url()))
    }

    @Test
    fun `fetch gives up on a slow server instead of delaying the push`() {
        server.enqueue(MockResponse().setBody("x").setHeadersDelay(3, TimeUnit.SECONDS))

        assertNull(loader.fetch(url()))
    }

    @Test
    fun `fetch drops a malformed url`() {
        assertNull(loader.fetch("not a url"))
    }

    @Test
    fun `sample size brings the longer side to the edge limit with a power of two`() {
        assertEquals(1, NotificationImageLoader.sampleSize(1024, 576))
        assertEquals(2, NotificationImageLoader.sampleSize(1600, 900))
        assertEquals(2, NotificationImageLoader.sampleSize(900, 1600))
        assertEquals(32, NotificationImageLoader.sampleSize(20_000, 20_000))
    }
}
