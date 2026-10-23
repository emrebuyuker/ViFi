package com.salticusteam.vifi.feature.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the admin moderation queue (iOS `ModerationViewModel`): loads the pending submissions once per screen,
 * and again on pull-to-refresh or retry. The list itself lives in [ModerationQueue], so an approval or
 * rejection made in the detail removes its row here at once.
 */
@HiltViewModel
class ModerationViewModel @Inject constructor(
    private val queue: ModerationQueue,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    /** The loading/failure half of the state; the records come from the queue once this screen loaded them. */
    private val _load = MutableStateFlow(LoadState())

    val uiState: StateFlow<ModerationUiState> =
        combine(_load, queue.records) { load, records -> load.uiState(records) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, _load.value.uiState(null))

    // Internal state
    /** Set while a request is in flight, so a pull-to-refresh or retry can't overlap another load. */
    private var isFetching = false

    // Intents
    init {
        load(showLoading = true)
    }

    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.MODERATION)
    }

    /** Pull-to-refresh: re-reads while the current list stays on screen. */
    fun refresh() = load(showLoading = false)

    /** Retry after a failure, with the loading state. */
    fun retry() = load(showLoading = true)

    // Helpers
    private fun load(showLoading: Boolean) {
        if (isFetching) return
        isFetching = true
        _load.update {
            if (showLoading) it.copy(isLoading = true, error = null) else it.copy(isRefreshing = true)
        }
        viewModelScope.launch {
            try {
                queue.reload()
                _load.value = LoadState(isLoading = false, hasLoaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _load.update { load ->
                    // A failed refresh keeps the list already shown; only an empty screen shows the error.
                    if (load.hasLoaded && !showLoading) load.copy(isRefreshing = false)
                    else LoadState(isLoading = false, error = e.userMessage)
                }
            } finally {
                isFetching = false
            }
        }
    }

    /** Whether this screen has a list to show, and the load in progress or its failure. */
    private data class LoadState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val hasLoaded: Boolean = false,
        val error: String? = null,
    ) {
        /** A list loaded by an earlier screen is never shown as this screen's (it is reloaded first, like iOS). */
        fun uiState(records: List<ExamSubmissionRecord>?) = ModerationUiState(
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            records = if (hasLoaded) records else null,
            error = error,
        )
    }
}
