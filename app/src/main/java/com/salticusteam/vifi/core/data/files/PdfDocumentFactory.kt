package com.salticusteam.vifi.core.data.files

import android.graphics.Bitmap
import java.io.Closeable
import java.io.File

/** Opens downloaded PDFs for the viewer (iOS `PDFDocumentOpener`). Behind an interface so ViewModels stay JVM-testable. */
interface PdfDocumentFactory {
    /** Opens the local [file] off the main thread. @throws com.salticusteam.vifi.core.model.ExamFileError.UnreadablePdf */
    suspend fun open(file: File): PdfPages
}

/** An open PDF. Pages render one at a time; [close] releases the file once the viewer no longer shows it. */
interface PdfPages : Closeable {
    val pageCount: Int

    /** Height / width of page [index], known without rendering it (layout before the bitmap is ready). */
    fun heightToWidthRatio(index: Int): Float

    /** Renders page [index] [widthPx] wide on a white background; `null` once the document is closed. */
    suspend fun render(index: Int, widthPx: Int): Bitmap?
}
