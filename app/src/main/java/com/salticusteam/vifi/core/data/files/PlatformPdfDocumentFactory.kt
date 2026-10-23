package com.salticusteam.vifi.core.data.files

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.salticusteam.vifi.core.model.ExamFileError
import com.salticusteam.vifi.core.util.LogCategory
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The framework [PdfRenderer] (no native library of our own, so nothing to align for 16 KB pages). It needs a
 * seekable local file, which is why PDFs are downloaded first ([com.salticusteam.vifi.core.data.repository.RemoteFileLoader]).
 */
class PlatformPdfDocumentFactory @Inject constructor() : PdfDocumentFactory {

    override suspend fun open(file: File): PdfPages = withContext(Dispatchers.IO) {
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            LogCategory.FILES.log.e(e, "Opening a PDF file failed")
            throw ExamFileError.UnreadablePdf
        }
        try {
            val renderer = PdfRenderer(descriptor)
            if (renderer.pageCount == 0) {
                renderer.close()
                throw ExamFileError.UnreadablePdf
            }
            val ratios = (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page -> page.height.toFloat() / page.width.coerceAtLeast(1) }
            }
            PlatformPdfPages(renderer, descriptor, ratios)
        } catch (e: ExamFileError) {
            descriptor.close()
            throw e
        } catch (e: Exception) {
            // A corrupt or password-protected file: PdfRenderer throws IOException / SecurityException.
            LogCategory.FILES.log.e(e, "Parsing a PDF file failed")
            descriptor.close()
            throw ExamFileError.UnreadablePdf
        }
    }
}

/** [PdfRenderer] is not thread-safe and allows one open page at a time: every access holds [lock]. */
private class PlatformPdfPages(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    private val ratios: List<Float>,
) : PdfPages {

    private val lock = Any()
    private var isClosed = false

    override val pageCount: Int get() = ratios.size

    override fun heightToWidthRatio(index: Int): Float = ratios[index]

    override suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val width = widthPx.coerceIn(1, MAX_RENDER_WIDTH_PX)
        val height = (width * ratios[index]).toInt().coerceAtLeast(1)
        synchronized(lock) {
            if (isClosed) return@withContext null
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            renderer.openPage(index).use { page ->
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            bitmap
        }
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
            renderer.close()
            descriptor.close()
        }
    }

    private companion object {
        /** Keeps a zoomed-in page's bitmap bounded (~2560 × 3620 ARGB ≈ 37 MB at most). */
        const val MAX_RENDER_WIDTH_PX = 2560
    }
}
