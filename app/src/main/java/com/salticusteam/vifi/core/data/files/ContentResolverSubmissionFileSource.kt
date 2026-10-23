package com.salticusteam.vifi.core.data.files

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.salticusteam.vifi.core.data.repository.PickedPdf
import com.salticusteam.vifi.core.data.repository.SubmissionFileSource
import com.salticusteam.vifi.core.util.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads picked files through the [ContentResolver] (Photo Picker and Storage Access Framework URIs need no
 * storage permission) and encodes images with the [SubmissionJpegEncoder] rules. Firebase-free, so both flavors
 * share it.
 */
class ContentResolverSubmissionFileSource @Inject constructor(
    @ApplicationContext context: Context,
) : SubmissionFileSource {

    private val resolver: ContentResolver = context.contentResolver

    override suspend fun jpegPage(uri: String, maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        try {
            encode(Uri.parse(uri), maxBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.w(e, "Could not encode a picked image")
            null
        } catch (e: OutOfMemoryError) {
            LogCategory.FILES.log.w(e, "Picked image too large to decode")
            null
        }
    }

    override suspend fun pdf(uri: String, maxBytes: Int): PickedPdf = withContext(Dispatchers.IO) {
        try {
            val source = Uri.parse(uri)
            // Reject an oversized file before reading it into memory.
            val size = declaredSize(source)
            if (size != null && size > maxBytes) return@withContext PickedPdf.TooLarge
            val bytes = resolver.openInputStream(source)?.use { it.readAtMost(maxBytes + 1) }
                ?: return@withContext PickedPdf.Unreadable
            when {
                bytes.isEmpty() -> PickedPdf.Empty
                bytes.size > maxBytes -> PickedPdf.TooLarge
                else -> PickedPdf.Data(bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.w(e, "Could not read a picked PDF")
            PickedPdf.Unreadable
        }
    }

    // Helpers
    private fun encode(source: Uri, maxBytes: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = SubmissionJpegEncoder.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val upright = upright(scaled(decoded), orientation(source))
        return try {
            SubmissionJpegEncoder.firstFitting(maxBytes) { quality ->
                ByteArrayOutputStream().use { out ->
                    if (upright.compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
                }
            }
        } finally {
            upright.recycle()
        }
    }

    /** Scales [bitmap] down to the encoder's longest-side limit, recycling the original when replaced. */
    private fun scaled(bitmap: Bitmap): Bitmap {
        val (width, height) = SubmissionJpegEncoder.targetSize(bitmap.width, bitmap.height)
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

    /** The provider-reported size, or `null` when unknown. */
    private fun declaredSize(source: Uri): Long? =
        resolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else null
        }

    /** Reads at most [limit] bytes, so an undeclared oversized file is detected without buffering all of it. */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
