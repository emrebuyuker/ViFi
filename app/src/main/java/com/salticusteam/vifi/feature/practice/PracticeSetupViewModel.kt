package com.salticusteam.vifi.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeScope
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "Sınava gir" setup (iOS `PracticeSetupViewModel` + `PracticeFlowView`): reads the subject's pool index (one
 * read; the reader caches it), then steps through exam type and scope. "Üniversitem" uses the profile's university,
 * and is left out for a university the user typed by hand (its id would match no pool).
 *
 * The route carries only the subject and the course id, so the index is read here rather than passed along.
 */
@HiltViewModel(assistedFactory = PracticeSetupViewModel.Factory::class)
class PracticeSetupViewModel @AssistedInject constructor(
    @Assisted("subjectId") private val subjectId: String,
    @Assisted("courseId") private val courseId: String,
    private val poolIndexReader: PoolIndexReader,
    private val profileStore: ProfileStore,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("subjectId") subjectId: String,
            @Assisted("courseId") courseId: String,
        ): PracticeSetupViewModel
    }

    // State
    private val _uiState = MutableStateFlow(PracticeSetupUiState())
    val uiState: StateFlow<PracticeSetupUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<PracticeSetupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Internal state
    private var choices: PracticeSetupChoices? = null
    private var loadJob: Job? = null

    // Intents
    init {
        load()
    }

    /** The screen came up (reported once per composition). */
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.PRACTICE_SETUP)
    }

    /** "Tekrar Dene" after a failed read. */
    fun onRetry() {
        load()
    }

    /** Step one: a type was picked; step two lists its scopes. */
    fun onTypeSelected(type: ExamType) {
        val current = choices ?: return
        _uiState.update { it.copy(selectedType = type, scopeRows = current.scopeRows(type)) }
    }

    /** Step two: a scope was picked; the session opens over that pool. */
    fun onScopeSelected(scope: PracticeScope) {
        val current = choices ?: return
        val type = _uiState.value.selectedType ?: return
        viewModelScope.launch { _events.send(PracticeSetupEvent.StartSession(current.selection(type, scope))) }
    }

    /** Back from step two to the type list. */
    fun onBackToTypes() {
        _uiState.update { it.copy(selectedType = null, scopeRows = emptyList()) }
    }

    // Helpers
    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val index = poolIndexReader.index(subjectId)
                val loaded = index?.let { PracticeSetupChoices(it, courseId, knownUniversityId()) }
                choices = loaded
                _uiState.update { PracticeSetupUiState(isLoading = false, typeRows = loaded?.typeRows.orEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.userMessage) }
            }
        }
    }

    /**
     * The profile's university id, unless the user typed a university that is not on the list (its id would not
     * match any pool).
     */
    private fun knownUniversityId(): String? =
        profileStore.profile?.takeUnless { it.universityIsCustom }?.universityId
}
