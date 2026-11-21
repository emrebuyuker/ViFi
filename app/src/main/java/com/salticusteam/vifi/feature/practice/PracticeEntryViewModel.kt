package com.salticusteam.vifi.feature.practice

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Added in 4.2.0 — whether a course offers "Sınava gir", and under which subject.
data class PracticeEntryUiState(
    /** The course's subject once its pool has at least one practice question; `null` hides the entry. */
    val subjectId: String? = null,
) {
    val isAvailable: Boolean get() = subjectId != null
}

/**
 * Decides whether a course offers "Sınava gir" (iOS `PracticeEntryViewModel`): reads its subject, then the subject's
 * pool index (one read; the reader caches it for the session). A failure, no subject or no questions leaves the entry
 * hidden; it never shows an error.
 */
@HiltViewModel
class PracticeEntryViewModel @Inject constructor(
    private val archiveRepository: ArchiveRepository,
    private val poolIndexReader: PoolIndexReader,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(PracticeEntryUiState())
    val uiState: StateFlow<PracticeEntryUiState> = _uiState.asStateFlow()

    // Internal state
    private var loadedCourse: ArchivePath? = null

    // Intents
    /** Reads the entry's availability for [course]; a course already read is not read again. Only lessons read. */
    suspend fun load(course: ArchivePath) {
        if (course.level != ArchiveLevel.LESSON || course == loadedCourse) return
        val subjectId = archiveRepository.subjectId(course) ?: return
        val index = try {
            poolIndexReader.index(subjectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        loadedCourse = course
        _uiState.value = PracticeEntryUiState(subjectId.takeIf { index?.hasPracticeQuestions == true })
    }
}
