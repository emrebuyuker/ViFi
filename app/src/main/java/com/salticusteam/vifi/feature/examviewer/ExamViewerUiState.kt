package com.salticusteam.vifi.feature.examviewer

import com.salticusteam.vifi.core.data.files.PdfPages
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind

/** The exam being viewed: its files once loaded. [routeKind] is the kind the list (or a recent entry) knew. */
data class ExamViewerUiState(
    val path: ArchivePath,
    val routeKind: ExamKind,
    val isLoading: Boolean = true,
    val document: ExamDocument? = null,
    val error: String? = null,
) {
    /** The exam name, available before the document loads (top bar title). */
    val title: String get() = path.displayName.orEmpty()

    /** "University › Lesson": where the exam comes from. */
    val subtitle: String
        get() = listOfNotNull(path.displayNames.firstOrNull(), path.displayNames.dropLast(1).lastOrNull())
            .joinToString(" › ")

    /**
     * The viewer to show: the document's own kind once loaded, so an exam whose files changed kind since the
     * list was read opens in the right viewer (iOS `redirectIfKindChanged`).
     */
    val kind: ExamKind get() = document?.kind ?: routeKind
}

/** The PDF file shown by the PDF viewer, out of the exam's [fileCount] files. */
data class ExamPdfUiState(
    val fileCount: Int = 0,
    val selectedFile: Int = 0,
    val isLoading: Boolean = true,
    /** The open document; rendered page by page by the screen. */
    val pages: PdfPages? = null,
    val error: String? = null,
)
