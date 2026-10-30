package com.salticusteam.vifi.feature.notificationcomposer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.SentNotification
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the admin notification history (iOS `NotificationHistoryViewModel`): loads the latest sent, scheduled and
 * automatic notifications once per screen, again on pull-to-refresh or retry, and cancels a scheduled one before it
 * goes out (no confirmation, iOS parity).
 */
@HiltViewModel
class NotificationHistoryViewModel @Inject constructor(
    private val service: NotificationAdminService,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val _uiState = MutableStateFlow(NotificationHistoryUiState())
    val uiState: StateFlow<NotificationHistoryUiState> = _uiState.asStateFlow()

    // Intents
    init {
        viewModelScope.launch { load() }
    }

    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.NOTIFICATION_HISTORY)
    }

    /** Pull-to-refresh, and the retry after a failed first load. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        // A retry after a failed first load goes back to the loading state at once.
        _uiState.update { state ->
            state.copy(
                errorMessage = null,
                isRefreshing = true,
                loadError = state.loadError.takeIf { state.notifications != null },
            )
        }
        viewModelScope.launch {
            try {
                load()
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Cancels [notification] (only a scheduled one can be) and reloads the list, so the row shows its new status. */
    fun cancel(notification: SentNotification) {
        if (!_uiState.value.canCancel(notification)) return
        _uiState.update { it.copy(cancelingId = notification.id, errorMessage = null) }
        viewModelScope.launch {
            try {
                service.cancel(notification.id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.userMessage) }
                // It may have gone out meanwhile; the reload shows where it really is.
                if (e is NotificationAdminError.NotCancelable) load()
            } finally {
                _uiState.update { it.copy(cancelingId = null) }
            }
        }
    }

    // Helpers
    private suspend fun load() {
        try {
            val notifications = service.history()
            _uiState.update { it.copy(notifications = notifications, loadError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed refresh keeps the rows already on screen and reports the problem above them.
            _uiState.update {
                if (it.notifications == null) it.copy(loadError = e.userMessage) else it.copy(errorMessage = e.userMessage)
            }
        }
    }
}
