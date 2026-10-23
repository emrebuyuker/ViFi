package com.salticusteam.vifi.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.announcements.AnnouncementsState
import com.salticusteam.vifi.core.data.announcements.AnnouncementsStore
import com.salticusteam.vifi.core.data.preferences.AnnouncementReadPreferences
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The announcements inbox (iOS InboxViewModel): newest first; opening it marks everything read, but the rows that
 * were unread on arrival keep their mark until the screen is left.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val store: AnnouncementsStore,
    private val readPreferences: AnnouncementReadPreferences,
    private val notifications: NotificationService,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val unreadOnArrival = MutableStateFlow<Set<String>?>(null)
    private val permissionOff = MutableStateFlow(false)

    val uiState: StateFlow<InboxUiState> = combine(store.state, unreadOnArrival, permissionOff, ::uiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    // Intents
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.INBOX)
        viewModelScope.launch {
            permissionOff.value = notifications.permission() == NotificationPermission.DENIED
            val read = readPreferences.readIds.first()
            // Fix the "new" marks from the first list that arrives, then mark it all read.
            val current = store.state.first { !it.isLoading }
            unreadOnArrival.value = current.announcements.map { it.id }.filterNot { it in read }.toSet()
            store.markAllRead()
        }
    }

    fun onRetry() = store.retry()

    // Helpers
    private fun uiState(state: AnnouncementsState, unread: Set<String>?, permissionOff: Boolean) = InboxUiState(
        items = state.announcements.map { InboxItem(it, isUnread = unread?.contains(it.id) == true) },
        isLoading = state.isLoading,
        error = state.error,
        showPermissionOffBanner = permissionOff,
    )
}
