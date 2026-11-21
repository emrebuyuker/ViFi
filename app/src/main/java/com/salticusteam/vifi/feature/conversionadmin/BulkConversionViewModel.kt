package com.salticusteam.vifi.feature.conversionadmin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.ExamConversionAdminService
import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the bulk conversion is (iOS `BulkConversionViewModel.Phase`). */
sealed interface BulkPhase {
    data object Idle : BulkPhase
    data object Estimating : BulkPhase

    /** A dry run came back; waiting for the admin's confirmation. */
    data class Estimated(val estimate: BulkConversionEstimate) : BulkPhase
    data object Queueing : BulkPhase
    data class Queued(val result: BulkConversionEstimate) : BulkPhase

    /** An estimate or a queueing result is on screen, so a new estimate may start. */
    val isFinished: Boolean get() = this is Estimated || this is Queued
}

/** The bulk conversion screen's state (iOS `BulkConversionViewModel`). */
data class BulkConversionUiState(
    val phase: BulkPhase = BulkPhase.Idle,
    val usage: AIMonthlyUsage? = null,
    val error: String? = null,
    /** What to convert; changing it (or the provider) invalidates a previous estimate. */
    val scope: BulkConversionScope? = null,
    /** "Üniversite › Fakülte › Bölüm [› Ders]" of the picked [scope]. */
    val scopeName: String? = null,
    /** No default: the admin picks Claude or Gemini every time (K9). */
    val provider: AIProvider? = null,
) {
    val canEstimate: Boolean
        get() = scope != null && provider != null && (phase == BulkPhase.Idle || phase.isFinished)

    /** The estimate would push this month past the cap; the confirmation says so but the server decides. */
    val estimateExceedsBudget: Boolean
        get() {
            val estimated = phase as? BulkPhase.Estimated ?: return false
            return usage?.exceedsCap(estimated.estimate.estimatedCostUsd) ?: false
        }
}

/**
 * Drives the bulk conversion screen (K12b; iOS `BulkConversionViewModel`): pick a course or department and an AI
 * provider, get a dry-run estimate against the month's $50 budget, confirm, and queue the unconverted exams.
 */
@HiltViewModel
class BulkConversionViewModel @Inject constructor(
    private val service: ExamConversionAdminService,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(BulkConversionUiState())
    val uiState: StateFlow<BulkConversionUiState> = _uiState.asStateFlow()

    // Intents
    init {
        loadUsage()
    }

    fun onScopeSelected(scope: BulkConversionScope, name: String) {
        _uiState.update { state ->
            if (state.scope == scope) state.copy(scopeName = name) else state.copy(scope = scope, scopeName = name).resetEstimate()
        }
    }

    fun onProviderSelected(provider: AIProvider) {
        _uiState.update { state ->
            if (state.provider == provider) state else state.copy(provider = provider).resetEstimate()
        }
    }

    /** The month's spend for the gauge; a failed read leaves the gauge out. */
    fun loadUsage() {
        viewModelScope.launch { refreshUsage() }
    }

    fun estimate() {
        val state = _uiState.value
        val scope = state.scope
        val provider = state.provider
        if (!state.canEstimate || scope == null || provider == null) return
        _uiState.update { it.copy(phase = BulkPhase.Estimating, error = null) }
        viewModelScope.launch {
            try {
                val estimate = service.requestBulkConversion(scope, provider, dryRun = true)
                _uiState.update { it.copy(phase = BulkPhase.Estimated(estimate)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = BulkPhase.Idle, error = e.userMessage) }
            }
        }
    }

    /** Queues the exams of the confirmed estimate. */
    fun confirm() {
        val state = _uiState.value
        val scope = state.scope
        val provider = state.provider
        if (state.phase !is BulkPhase.Estimated || scope == null || provider == null) return
        _uiState.update { it.copy(phase = BulkPhase.Queueing, error = null) }
        viewModelScope.launch {
            try {
                val result = service.requestBulkConversion(scope, provider, dryRun = false)
                _uiState.update { it.copy(phase = BulkPhase.Queued(result)) }
                refreshUsage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(phase = BulkPhase.Idle, error = e.userMessage) }
            }
        }
    }

    // Helpers
    private suspend fun refreshUsage() {
        try {
            val usage = service.monthlyUsage()
            _uiState.update { it.copy(usage = usage) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The gauge is optional; the server enforces the cap either way.
        }
    }

    private fun BulkConversionUiState.resetEstimate(): BulkConversionUiState =
        if (phase is BulkPhase.Estimated || phase is BulkPhase.Queued) copy(phase = BulkPhase.Idle) else this
}
