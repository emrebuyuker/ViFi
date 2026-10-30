package com.salticusteam.vifi.core.notifications

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a push's image for the foreground big-picture notification. The URL is a signed Storage URL that needs
 * no credentials, so this is a plain client of its own: no Firebase ID token or App Check header may ever reach that
 * host (never the exam files client and its `StorageAuthInterceptor`). A failure, a body over
 * [NotificationImage.MAX_BYTES] or the time limit means no image; the push is then shown with its text alone.
 *
 * Blocking: FCM calls `onMessageReceived` on a worker thread and the notification must be posted before it returns.
 * The URL carries its signature, so it is never logged.
 */
@Singleton
class NotificationImageLoader internal constructor(private val client: OkHttpClient) {

    @Inject constructor() : this(defaultClient())

    /** The decoded image, downsampled to at most [MAX_EDGE] pixels on its longer side; null on any failure. */
    fun load(url: String): Bitmap? = fetch(url)?.let(::decode)

    internal fun fetch(url: String): ByteArray? {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful || body == null) return null
                if (body.contentLength() > NotificationImage.MAX_BYTES) return null
                val source = body.source()
                // Buffers at most one byte past the cap: a larger body (or one without a length) is never read whole.
                if (source.request(NotificationImage.MAX_BYTES + 1L)) return null
                return source.readByteArray()
            }
        } catch (e: IOException) {
            LogCategory.APP.log.w(e, "Downloading the push image failed")
        } catch (e: IllegalArgumentException) {
            LogCategory.APP.log.w(e, "The push image URL is malformed")
        }
        return null
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    internal companion object {
        /** Keeps the bitmap small enough for the notification service (a 1600 px image decodes at 800 px). */
        const val MAX_EDGE = 1024

        /** Within FCM's time for `onMessageReceived`; a slow image is dropped rather than delaying the push. */
        const val TIMEOUT_SECONDS = 8L

        /** The power-of-two sample size that brings the longer side to [maxEdge] or below. */
        fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
            var size = 1
            while (maxOf(width, height) / size > maxEdge) size *= 2
            return size
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // No downgrade: a redirect may not leave https.
            .followSslRedirects(false)
            .build()
    }
}
