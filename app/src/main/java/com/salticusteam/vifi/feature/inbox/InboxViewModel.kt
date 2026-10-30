package com.salticusteam.vifi.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.inbox.InboxState
import com.salticusteam.vifi.core.data.inbox.InboxStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The inbox (iOS `InboxView`): the merged notifications newest first, unread ones marked live. Opening it marks
 * everything read in one server write, and so does anything that arrives while it is open.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val store: InboxStore,
    private val notifications: NotificationService,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val permissionOff = MutableStateFlow(false)

    val uiState: StateFlow<InboxUiState> = combine(store.state, permissionOff, ::uiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    // Internal state
    private var marking: Job? = null

    // Intents
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.INBOX)
        store.reevaluateExpiry()
        viewModelScope.launch {
            permissionOff.value = notifications.permission() == NotificationPermission.DENIED
        }
        // What is unread now, and again whenever more arrives or the marker turns up while the page is open.
        marking?.cancel()
        marking = viewModelScope.launch {
            store.unreadCount.filter { it > 0 }.collect { store.markAllRead() }
        }
    }

    fun onRetry() = store.retry()

    // Helpers
    private fun uiState(state: InboxState, permissionOff: Boolean) = InboxUiState(
        items = state.items.map { InboxEntry(it, isUnread = state.isUnread(it)) },
        isLoading = state.isLoading,
        error = state.error,
        showPermissionOffBanner = permissionOff,
    )
}
