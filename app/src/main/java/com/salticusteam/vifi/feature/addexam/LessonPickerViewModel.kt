package com.salticusteam.vifi.feature.addexam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveNameMatch
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.userMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The archive lessons under the chosen department (iOS LessonPickerViewModel's `Loadable` as fields). */
data class LessonPickerUiState(
    val isLoading: Boolean = true,
    /** The department's lessons; empty when the department has none or is not in the archive yet. */
    val lessons: List<ArchiveItem> = emptyList(),
    val error: String? = null,
    val query: String = "",
) {
    /** The loaded lessons whose names contain the query (Turkish-insensitive); empty unless loaded. */
    val matches: List<ArchiveItem>
        get() {
            if (isLoading || error != null) return emptyList()
            val needle = query.trim()
            if (needle.isEmpty()) return lessons
            return lessons.filter { ArchiveNameMatch.contains(it.name, needle) }
        }
}

/**
 * Loads the archive lessons under a department identified by its bundled-list display names (iOS
 * LessonPickerViewModel). The university must match by name; the department is searched across every faculty
 * of that university, because the bundled faculty name does not necessarily match the archive's.
 */
@HiltViewModel(assistedFactory = LessonPickerViewModel.Factory::class)
class LessonPickerViewModel @AssistedInject constructor(
    @Assisted("university") private val university: String,
    @Assisted("department") private val department: String,
    private val repository: ArchiveRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("university") university: String,
            @Assisted("department") department: String,
        ): LessonPickerViewModel
    }

    // State
    private val _uiState = MutableStateFlow(LessonPickerUiState())
    val uiState: StateFlow<LessonPickerUiState> = _uiState.asStateFlow()

    // Internal state
    private var loadJob: Job? = null

    // Intents
    init {
        load()
    }

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    fun onRetry() = load()

    // Helpers
    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                // A department with no archive match offers only free text.
                val lessons = departmentPath()?.let { repository.items(it) }.orEmpty()
                _uiState.update { it.copy(isLoading = false, lessons = lessons) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            }
        }
    }

    /** The archive department matching the bundled names, or `null` when the university or department is missing. */
    private suspend fun departmentPath(): ArchivePath? {
        if (university.isEmpty() || department.isEmpty()) return null
        val universityMatch = repository.items(ArchivePath.ROOT)
            .firstOrNull { ArchiveNameMatch.matches(it.name, university) } ?: return null
        for (faculty in repository.items(universityMatch.path)) {
            val match = repository.items(faculty.path)
                .firstOrNull { ArchiveNameMatch.matchesDepartment(archive = it.name, profile = department) }
            if (match != null) return match.path
        }
        return null
    }
}
