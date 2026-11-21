package com.salticusteam.vifi.feature.conversionadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectNameSimilarity
import com.salticusteam.vifi.core.model.SubjectTarget
import com.salticusteam.vifi.core.model.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The admin subject-mapping screens' state (iOS `SubjectMappingViewModel`): the subject list with search and the
 * "Olası eşleşme" hints. Shared by the list and the detail destination, so the detail follows every reload and closes
 * itself when its subject is gone (merged).
 */
data class SubjectMappingUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** `null` until the first successful load. */
    val subjects: List<AdminSubject>? = null,
    val loadError: String? = null,
    val searchText: String = "",
    val isWorking: Boolean = false,
    /** The last failed action, shown above the list. */
    val actionError: String? = null,
    /** Subject id → id of the subject it probably duplicates (only single-university subjects). */
    val possibleMatches: Map<String, String> = emptyMap(),
) {

    /**
     * The subjects the search shows: those whose name or one of whose course names contains the text (case- and
     * diacritic-insensitive); everything for a blank search.
     */
    val filteredSubjects: List<AdminSubject>
        get() {
            val all = subjects ?: return emptyList()
            val query = fold(searchText)
            if (query.isEmpty()) return all
            return all.filter { subject ->
                fold(subject.name).contains(query) || subject.courses.any { fold(it.name).contains(query) }
            }
        }

    fun subject(id: String): AdminSubject? = subjects?.firstOrNull { it.id == id }

    /** The subject [id] probably duplicates, if any. */
    fun possibleMatch(id: String): AdminSubject? = possibleMatches[id]?.let(::subject)

    private fun fold(text: String): String = SubjectNameSimilarity.fold(text.trim())
}

/**
 * Drives the admin subject-mapping screens (P5; iOS `SubjectMappingViewModel`): the subject list with search, the
 * "Olası eşleşme" hints, and the three fixes: change a course's subject, rename a subject, merge one into another.
 * Every fix reloads the list from the server afterwards, so what is shown is what the server holds.
 */
@HiltViewModel
class SubjectMappingViewModel @Inject constructor(
    private val service: SubjectMappingService,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(SubjectMappingUiState())
    val uiState: StateFlow<SubjectMappingUiState> = _uiState.asStateFlow()

    // Intents
    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun onSearchChange(text: String) = _uiState.update { it.copy(searchText = text) }

    fun clearActionError() = _uiState.update { it.copy(actionError = null) }

    /** Moves a course to another (or a new) subject; the server moves its questions and pools too. */
    fun setCourseSubject(course: SubjectCourse, target: SubjectTarget) {
        perform { service.setCourseSubject(course.id, target) }
    }

    /** Renames [subject]; a blank or unchanged name does nothing. */
    fun rename(subject: AdminSubject, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == subject.name) return
        perform { service.renameSubject(subject.id, trimmed) }
    }

    /** Merges [subject] into [target]: its courses, questions and pools move, then it is deleted. */
    fun merge(subject: AdminSubject, target: AdminSubject) {
        if (subject.id == target.id) return
        perform { service.mergeSubject(subject.id, target.id) }
    }

    // Helpers
    private suspend fun load() {
        _uiState.update { state ->
            if (state.subjects == null) state.copy(isLoading = true, loadError = null) else state.copy(isRefreshing = true)
        }
        try {
            apply(service.subjects())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { state ->
                if (state.subjects == null) state.copy(isLoading = false, isRefreshing = false, loadError = e.userMessage)
                else state.copy(isRefreshing = false, actionError = e.userMessage)
            }
        }
    }

    private fun apply(subjects: List<AdminSubject>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                loadError = null,
                subjects = subjects,
                possibleMatches = SubjectNameSimilarity.possibleMatches(subjects),
            )
        }
    }

    private fun perform(action: suspend () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, actionError = null) }
        viewModelScope.launch {
            try {
                action()
                // The list is read again, so what is shown is what the server holds (working until then).
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(actionError = e.userMessage) }
            } finally {
                _uiState.update { it.copy(isWorking = false) }
            }
        }
    }
}
