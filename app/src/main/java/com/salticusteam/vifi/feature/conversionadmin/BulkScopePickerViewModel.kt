package com.salticusteam.vifi.feature.conversionadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One level of the scope picker's archive browser (iOS `BulkScopeLevel`). */
data class BulkScopePickerUiState(
    /** The level being listed: the root (universities), a university, a faculty or a department. */
    val path: ArchivePath = ArchivePath.ROOT,
    val isLoading: Boolean = true,
    /** `null` until this level loaded. */
    val items: List<ArchiveItem>? = null,
    val error: String? = null,
) {
    /** At a department the whole department can be picked, besides its courses. */
    val canPickDepartment: Boolean get() = path.level == ArchiveLevel.DEPARTMENT

    /** The picked department's scope and its "A › B › C" name; `null` away from a department. */
    fun departmentScope(): Pair<BulkConversionScope, String>? {
        val id = path.components.lastOrNull() ?: return null
        if (!canPickDepartment) return null
        return BulkConversionScope.Department(id) to path.displayNames.joinToString(" › ")
    }
}

/**
 * Browses the archive down to a department (all its exams) or one of its courses for the bulk conversion (iOS
 * `BulkScopePicker`); the screen reports the choice, this only walks the tree one level at a time.
 */
@HiltViewModel
class BulkScopePickerViewModel @Inject constructor(
    private val archive: ArchiveRepository,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(BulkScopePickerUiState())
    val uiState: StateFlow<BulkScopePickerUiState> = _uiState.asStateFlow()

    // Internal state
    private var loadJob: Job? = null

    // Intents
    init {
        load(ArchivePath.ROOT)
    }

    /** Opens a university, faculty or department; lessons are picked, not opened, and exams are not listed. */
    fun open(item: ArchiveItem) {
        if (item.level.depth <= ArchiveLevel.DEPARTMENT.depth) load(item.path)
    }

    /** One level up; `false` at the root, where the picker closes instead. */
    fun up(): Boolean {
        val parent = _uiState.value.path.parent ?: return false
        load(parent)
        return true
    }

    fun retry() = load(_uiState.value.path)

    /** Back to the universities, for the next time the picker opens. */
    fun reset() = load(ArchivePath.ROOT)

    // Helpers
    private fun load(path: ArchivePath) {
        loadJob?.cancel()
        _uiState.update { BulkScopePickerUiState(path = path) }
        loadJob = viewModelScope.launch {
            try {
                val items = archive.items(path)
                _uiState.update { it.copy(isLoading = false, items = items) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            }
        }
    }
}
