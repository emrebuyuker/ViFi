package com.salticusteam.vifi.core.data.notifications.admin

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.salticusteam.vifi.core.data.files.SubmissionJpegEncoder
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.util.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encodes a picked photo with the [SubmissionJpegEncoder] rules, sized for a notification: the longest side brought
 * down to [NotificationImage.MAX_PIXEL_SIZE] (decoded with a power-of-two sample size first, so a camera photo is
 * never held at full resolution), the EXIF orientation applied, and the JPEG quality stepped down (80/60/45/30)
 * until it fits [NotificationImage.MAX_BYTES]. Photo Picker URIs need no storage permission. Firebase-free, so both
 * flavors share it.
 */
class ContentResolverNotificationImageEncoder @Inject constructor(
    @ApplicationContext context: Context,
) : NotificationImageEncoder {

    private val resolver: ContentResolver = context.contentResolver

    override suspend fun encode(uri: String): EncodedNotificationImage = withContext(Dispatchers.IO) {
        try {
            encode(Uri.parse(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            LogCategory.FILES.log.w(e, "Could not read a picked notification image")
            EncodedNotificationImage.Unreadable
        } catch (e: SecurityException) {
            LogCategory.FILES.log.w(e, "No access to a picked notification image")
            EncodedNotificationImage.Unreadable
        } catch (e: Exception) {
            LogCategory.FILES.log.w(e, "Could not encode a picked notification image")
            EncodedNotificationImage.Unusable
        } catch (e: OutOfMemoryError) {
            LogCategory.FILES.log.w(e, "Picked notification image too large to decode")
            EncodedNotificationImage.Unusable
        }
    }

    // Helpers
    private fun encode(source: Uri): EncodedNotificationImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; only a missing stream means the URI is unreadable.
        val stream = resolver.openInputStream(source) ?: return EncodedNotificationImage.Unreadable
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return EncodedNotificationImage.Unusable

        val options = BitmapFactory.Options().apply {
            inSampleSize = SubmissionJpegEncoder.sampleSize(bounds.outWidth, bounds.outHeight, MAX_PIXEL_SIZE)
        }
        val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return EncodedNotificationImage.Unusable
        val upright = upright(scaled(decoded), orientation(source))
        val jpeg = try {
            SubmissionJpegEncoder.firstFitting(NotificationImage.MAX_BYTES) { quality ->
                ByteArrayOutputStream().use { out ->
                    if (upright.compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
                }
            }
        } finally {
            upright.recycle()
        }
        return jpeg?.let(EncodedNotificationImage::Jpeg) ?: EncodedNotificationImage.Unusable
    }

    /** Scales [bitmap] down to the notification's longest-side limit, recycling the original when replaced. */
    private fun scaled(bitmap: Bitmap): Bitmap {
        val (width, height) = SubmissionJpegEncoder.targetSize(bitmap.width, bitmap.height, MAX_PIXEL_SIZE)
        if (width == bitmap.width && height == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, width, height, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    /** Applies the EXIF orientation, recycling the original when replaced. */
    private fun upright(bitmap: Bitmap, orientation: Int): Bitmap {
        val transform = SubmissionJpegEncoder.orientationTransform(orientation)
        if (transform.isIdentity) return bitmap
        val matrix = Matrix().apply {
            if (transform.mirrored) postScale(-1f, 1f)
            postRotate(transform.rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    /** The EXIF orientation tag, or "normal" when the image carries none or it cannot be read. */
    private fun orientation(source: Uri): Int = try {
        resolver.openInputStream(source)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private companion object {
        const val MAX_PIXEL_SIZE = NotificationImage.MAX_PIXEL_SIZE
    }
}
