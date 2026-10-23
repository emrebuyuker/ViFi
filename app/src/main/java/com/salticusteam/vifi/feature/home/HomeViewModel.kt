package com.salticusteam.vifi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.RecentExam
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Drives the home screen's recently viewed shelf: reads, removes and clears the stored recents (iOS HomeView). */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recentExams: RecentExamsRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Intents
    init {
        viewModelScope.launch {
            recentExams.exams.collect { list -> _uiState.update { it.copy(recents = list) } }
        }
    }

    /** A recent card was tapped; the screen opens the exam, this only reports it. */
    fun onRecentOpened() {
        analytics.track(AnalyticsEvent.RecentExamOpened)
    }

    fun onRemove(exam: RecentExam) {
        viewModelScope.launch { recentExams.remove(exam.path) }
    }

    fun onClearRequested() {
        _uiState.update { it.copy(isClearConfirmationShown = true) }
    }

    fun onClearDismissed() {
        _uiState.update { it.copy(isClearConfirmationShown = false) }
    }

    fun onClearConfirmed() {
        _uiState.update { it.copy(isClearConfirmationShown = false) }
        viewModelScope.launch { recentExams.clear() }
    }
}
