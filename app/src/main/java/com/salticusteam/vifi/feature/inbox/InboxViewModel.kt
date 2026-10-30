package com.salticusteam.vifi.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.inbox.InboxState
import com.salticusteam.vifi.core.data.inbox.InboxStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.NotificationAction
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.util.trackScreen
import com.salticusteam.vifi.feature.browse.ExamUnlockEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The inbox (iOS `InboxView`): the merged notifications newest first, unread ones marked live, each card's action,
 * and the tapped push's highlight. Opening it marks everything read in one server write, and so does anything that
 * arrives while it is open. Exam actions go through the archive's lock rule ([InboxExamGate]).
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val store: InboxStore,
    private val notifications: NotificationService,
    subscriptionStore: SubscriptionStore,
    private val gate: InboxExamGate,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val permissionOff = MutableStateFlow(false)
    private val requestedHighlight = MutableStateFlow<String?>(null)

    val uiState: StateFlow<InboxUiState> = combine(
        store.state,
        subscriptionStore.isSubscribed,
        permissionOff,
        requestedHighlight,
        gate.state,
    ) { state, isSubscribed, permissionOff, highlight, gate ->
        uiState(state, isSubscribed, permissionOff, highlight, gate)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    // One-shot events
    private val _events = Channel<InboxEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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

    /** The tapped push's notification (the route argument); `null` from the bell. */
    fun onHighlightRequested(notificationId: String?) {
        requestedHighlight.value = notificationId
    }

    fun onRetry() = store.retry()

    /** The card, its image or its button was tapped: the same action for all three. */
    fun onAction(entry: InboxEntry) {
        when (val action = entry.action ?: return) {
            is NotificationAction.Exam -> viewModelScope.launch {
                if (gate.check(action.target)) _events.send(InboxEvent.OpenExam(action.target.path, action.target.kind))
            }
            NotificationAction.Paywall -> send(InboxEvent.OpenPaywall)
            NotificationAction.AddExam -> send(InboxEvent.OpenAddExam(lesson = null))
            is NotificationAction.Url -> send(InboxEvent.OpenUrl(action.url))
            is NotificationAction.Location -> send(InboxEvent.ChooseMapsApp(action.location))
        }
    }

    /** The unlock sheet's outcome after it closed: open the exam, the add-exam form or the paywall, at most once. */
    fun onUnlockEvent(event: ExamUnlockEvent) {
        when (val next = gate.followUp(event) ?: return) {
            is InboxExamFollowUp.Open -> send(InboxEvent.OpenExam(next.target.path, next.target.kind))
            is InboxExamFollowUp.AddExam -> send(InboxEvent.OpenAddExam(next.lesson))
            InboxExamFollowUp.Paywall -> send(InboxEvent.OpenPaywall)
        }
    }

    fun onUnlockSheetDismissed() = gate.onSheetDismissed()

    fun onExamErrorShown() = gate.onErrorShown()

    // Helpers
    private fun send(event: InboxEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun uiState(
        state: InboxState,
        isSubscribed: Boolean,
        permissionOff: Boolean,
        highlight: String?,
        gate: InboxExamGateState,
    ) = InboxUiState(
        items = state.items.map { entry(it, state.isUnread(it), isSubscribed) },
        isLoading = state.isLoading,
        error = state.error,
        hasPartialFailure = state.hasPartialFailure,
        showPermissionOffBanner = permissionOff,
        // Only once listed: an expired or not yet delivered notification is not highlighted.
        highlightedId = highlight?.takeIf { id -> state.items.any { it.id == id } },
        checkingExamId = gate.checkingExamId,
        lockedExam = gate.lockedExam,
        examError = gate.errorMessage,
    )

    private fun entry(item: InboxItem, isUnread: Boolean, isSubscribed: Boolean) = InboxEntry(
        item = item,
        isUnread = isUnread,
        // A subscriber has nothing to buy: the Premium card is inert (iOS hides the button).
        action = item.action?.takeUnless { it == NotificationAction.Paywall && isSubscribed },
        imageUrl = item.imageStoragePath?.let(store::imageUrl),
    )
}
