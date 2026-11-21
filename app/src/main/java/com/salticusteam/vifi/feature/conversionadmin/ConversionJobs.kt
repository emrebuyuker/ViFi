package com.salticusteam.vifi.feature.conversionadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.userMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which jobs a conversion list shows (iOS `ConversionListKind`). */
enum class ConversionListKind(val statuses: Set<ConversionStatus>) {
    /** Drafts waiting for the second approval ("Hazır sınavlar"). */
    READY(setOf(ConversionStatus.READY)),

    /** Jobs that gave up after their retries ("Başarısız işler"). */
    FAILED(setOf(ConversionStatus.FAILED)),
}

/**
 * The job lists' last loaded jobs, so the review and original-pages destinations (which carry only a job id in their
 * route) find the job a list showed (iOS passes the job value itself). A job missing here (the process was recreated)
 * is read again by the destination.
 */
@Singleton
class ConversionJobDirectory @Inject constructor() {

    private val jobs = ConcurrentHashMap<String, ExamConversion>()

    fun remember(list: List<ExamConversion>) {
        for (job in list) jobs[job.id] = job
    }

    fun job(id: String): ExamConversion? = jobs[id]

    fun forget(id: String) {
        jobs.remove(id)
    }
}

/**
 * One of the admin's job lists: loading until the first read, then the jobs, or the failure when nothing could be
 * shown (iOS `Loadable<[ExamConversion]>`).
 */
data class ConversionJobsUiState(
    val kind: ConversionListKind,
    val isLoading: Boolean = true,
    /** A pull-to-refresh is running over a list already shown. */
    val isRefreshing: Boolean = false,
    /** `null` until the first successful load. */
    val jobs: List<ExamConversion>? = null,
    val loadError: String? = null,
    /** A restart is running: its button and the others are disabled. */
    val isWorking: Boolean = false,
    /** The last failed action, shown above the list. */
    val actionError: String? = null,
)

/**
 * Drives the admin's job lists (iOS `ConversionJobsViewModel`): loads the jobs of one kind and restarts a failed one
 * with the provider the admin picked (K9; there is no default). A restarted job leaves the list because it is queued
 * again.
 */
@HiltViewModel(assistedFactory = ConversionJobsViewModel.Factory::class)
class ConversionJobsViewModel @AssistedInject constructor(
    @Assisted private val kind: ConversionListKind,
    private val service: ExamConversionAdminService,
    private val directory: ConversionJobDirectory,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(ConversionJobsUiState(kind))
    val uiState: StateFlow<ConversionJobsUiState> = _uiState.asStateFlow()

    // Internal state
    private var isFetching = false

    @AssistedFactory
    interface Factory {
        fun create(kind: ConversionListKind): ConversionJobsViewModel
    }

    // Intents
    init {
        load(showLoading = true)
    }

    /** Pull-to-refresh and retry. */
    fun refresh() = load(showLoading = _uiState.value.jobs == null)

    /**
     * Re-reads an already shown list (the admin came back from a review that published, discarded or re-queued a
     * job); does nothing while the first load is still pending.
     */
    fun refreshIfLoaded() {
        if (_uiState.value.jobs != null) load(showLoading = false)
    }

    /** Restarts a failed job (or re-runs a ready one) with [provider]; the job leaves the list once accepted. */
    fun restart(job: ExamConversion, provider: AIProvider) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, actionError = null) }
        viewModelScope.launch {
            try {
                service.regenerate(job.id, provider)
                directory.forget(job.id)
                _uiState.update { state ->
                    state.copy(isWorking = false, jobs = state.jobs?.filterNot { it.id == job.id })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, actionError = e.userMessage) }
            }
        }
    }

    // Helpers
    private fun load(showLoading: Boolean) {
        if (isFetching) return
        isFetching = true
        _uiState.update {
            if (showLoading) it.copy(isLoading = true, loadError = null) else it.copy(isRefreshing = true)
        }
        viewModelScope.launch {
            try {
                val jobs = service.conversions(kind.statuses)
                directory.remember(jobs)
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, jobs = jobs, loadError = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    // A failed refresh keeps the list already shown; only an empty screen shows the error.
                    if (state.jobs != null) state.copy(isRefreshing = false, actionError = e.userMessage)
                    else state.copy(isLoading = false, isRefreshing = false, loadError = e.userMessage)
                }
            } finally {
                isFetching = false
            }
        }
    }
}
