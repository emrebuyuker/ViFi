package com.salticusteam.vifi.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.navigation.ViFiRoute
import com.salticusteam.vifi.core.util.LogCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads the entries listed under an archive path and filters them by the search text (iOS `BrowseViewModel`).
 * Drives the home screen (root path, [ViFiRoute.Home]) and every drill-down level ([ViFiRoute.Browse]).
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val archiveRepository: ArchiveRepository,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(BrowseUiState(path = savedStateHandle.archivePath()))
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    // Internal state
    /** Set while a request is in flight, so a pull-to-refresh or retry can't overlap the initial load. */
    private var isFetching = false

    // Intents
    init {
        load()
        observeArchiveChanges()
    }

    /** Fetches the entries from scratch, showing the loading state meanwhile (initial load and retry). */
    fun load() {
        fetch(showLoading = true)
    }

    /** Re-fetches while the current entries stay on screen; a failure keeps them (pull-to-refresh). */
    fun refresh() {
        fetch(showLoading = false)
    }

    fun onSearchTextChange(text: String) {
        _uiState.update { it.copy(searchText = text) }
    }

    /** Drops [item] after an admin deleted it, so the list updates at once (a refetch could hit the cache). */
    fun remove(item: ArchiveItem) {
        _uiState.update { state ->
            state.copy(items = state.items?.filterNot { it.path == item.path })
        }
    }

    // Helpers
    private fun fetch(showLoading: Boolean) {
        if (isFetching) return
        isFetching = true
        if (showLoading) _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val items = archiveRepository.items(_uiState.value.path)
                _uiState.update { it.copy(isLoading = false, items = items, error = null) }
            } catch (e: CancellationException) {
                throw e // the ViewModel is going away; nothing to show
            } catch (e: Exception) {
                logFailure(e)
                _uiState.update { state ->
                    // A failed refresh keeps the entries already shown; only an empty screen shows the error.
                    if (state.items != null && !showLoading) state.copy(isLoading = false)
                    else state.copy(isLoading = false, items = null, error = e.userMessage)
                }
            } finally {
                isFetching = false
            }
        }
    }

    /** Re-reads the entries each time the archive changes, for the ViewModel's lifetime. */
    private fun observeArchiveChanges() {
        viewModelScope.launch {
            archiveRepository.archiveChanges().collect {
                // An initial load still in progress already returns the newest data.
                if (!_uiState.value.isLoading) refresh()
            }
        }
    }

    private fun logFailure(error: Exception) {
        val path = _uiState.value.path
        val location = if (path.isRoot) "root" else path.components.joinToString(" › ")
        LogCategory.ARCHIVE.log.e(error, "Loading %s failed", location)
    }

    private companion object {
        /** Home has no route arguments (root); a Browse destination carries its path. */
        fun SavedStateHandle.archivePath(): ArchivePath =
            if (contains("ids")) toRoute<ViFiRoute.Browse>().path else ArchivePath.ROOT
    }
}
