package com.salticusteam.vifi.feature.examviewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.files.PdfDocumentFactory
import com.salticusteam.vifi.core.data.files.PdfPages
import com.salticusteam.vifi.core.data.repository.RemoteFileLoader
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.LogCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Downloads the selected PDF of an exam (cached on disk) and opens it (iOS `PDFExamScreen.loadFile`); switches
 * between files when the exam has several (VIEW-08).
 */
@HiltViewModel
class ExamPdfViewModel @Inject constructor(
    private val fileLoader: RemoteFileLoader,
    private val pdfFactory: PdfDocumentFactory,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ExamPdfUiState())
    val uiState: StateFlow<ExamPdfUiState> = _uiState.asStateFlow()

    // Internal state
    private var document: ExamDocument? = null
    private var loadJob: Job? = null

    // Intents
    /** Starts with the first file once the exam's files are known; a repeated call for the same exam is ignored. */
    fun onDocumentLoaded(document: ExamDocument) {
        if (this.document == document) return
        this.document = document
        _uiState.update { it.copy(fileCount = document.fileUrls.size, selectedFile = 0) }
        loadSelectedFile()
    }

    fun onFileSelected(index: Int) {
        val state = _uiState.value
        if (index == state.selectedFile || index !in 0 until state.fileCount) return
        _uiState.update { it.copy(selectedFile = index) }
        loadSelectedFile()
    }

    fun onRetry() = loadSelectedFile()

    override fun onCleared() {
        loadJob?.cancel()
        _uiState.value.pages?.close()
    }

    // Helpers
    private fun loadSelectedFile() {
        val document = document ?: return
        val index = _uiState.value.selectedFile
        val url = document.fileUrls.getOrNull(index) ?: return

        loadJob?.cancel()
        val previous = _uiState.value.pages
        _uiState.update { it.copy(isLoading = true, pages = null, error = null) }
        previous?.close()

        loadJob = viewModelScope.launch {
            var opened: PdfPages? = null
            try {
                val file = fileLoader.localFile(url, document.pdfFileName(index))
                opened = pdfFactory.open(file)
                _uiState.update { it.copy(isLoading = false, pages = opened, error = null) }
            } catch (e: CancellationException) {
                // Superseded by another file, or the screen went away: release what was opened for it.
                if (_uiState.value.pages !== opened) opened?.close()
                throw e
            } catch (e: Exception) {
                LogCategory.FILES.log.e(e, "PDF %d of an exam failed to open", index + 1)
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            }
        }
    }
}

/** "2018 VİZE.pdf", or "2018 VİZE - Dosya 2.pdf" when the exam has several PDFs: the cached file's readable name. */
internal fun ExamDocument.pdfFileName(index: Int): String {
    val stem = title.map { if (it in "/\\:") '-' else it }.joinToString("").trim().ifEmpty { "Sınav" }
    return if (fileUrls.size > 1) "$stem - Dosya ${index + 1}.pdf" else "$stem.pdf"
}
