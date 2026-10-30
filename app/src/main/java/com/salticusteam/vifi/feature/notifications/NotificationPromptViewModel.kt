package com.salticusteam.vifi.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.NotificationPromptHistory
import com.salticusteam.vifi.core.data.preferences.OnboardingPreferences
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.OnboardingState
import com.salticusteam.vifi.core.navigation.ModalPresence
import com.salticusteam.vifi.core.notifications.NotificationRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shows the notification flow the [NotificationPromptCoordinator] queues (iOS `NotificationPromptPresenter`): a
 * waiting run goes on screen once the tour is over and nothing is in the way (a registered modal, a tapped push still
 * on its way to the inbox), and leaves it when answered, closed, preempted by a tapped push or ended by a sign-out.
 * Hosted by the archive ([NotificationPromptHost]); it also makes the first offer there, after the tour, and the
 * permission's re-ask after an exam submission (the archive host reports it).
 */
@HiltViewModel
class NotificationPromptViewModel @Inject constructor(
    private val coordinator: NotificationPromptCoordinator,
    private val notifications: NotificationService,
    private val settings: NotificationSettingsStore,
    private val history: NotificationPromptHistory,
    private val analytics: AnalyticsTracker,
    private val modalPresence: ModalPresence,
    private val notificationRouter: NotificationRouter,
    private val onboardingPreferences: OnboardingPreferences,
    private val sessionStore: SessionStore,
) : ViewModel() {

    // State
    private val _flow = MutableStateFlow<NotificationPermissionFlow?>(null)

    /** The run on screen; `null` when none. */
    val flow: StateFlow<NotificationPermissionFlow?> = _flow.asStateFlow()

    // Internal state
    private var firstOffer: Job? = null

    init {
        // Whatever run waits is taken when the screen is free; a newer one queued meanwhile is the one taken.
        viewModelScope.launch {
            coordinator.pending.filterNotNull().collect { presentWhenFree() }
        }
        // A tapped push needs the screen (iOS `showPendingNotification`); a sign-out ends the run (iOS RootView).
        viewModelScope.launch {
            notificationRouter.pending.filterNotNull().collect { coordinator.dismiss() }
        }
        viewModelScope.launch {
            sessionStore.state.collect { if (it == SessionState.SignedOut) coordinator.dismiss() }
        }
        // However the shown run was cleared, the sheet goes and an unanswered page counts as a "no".
        viewModelScope.launch {
            coordinator.presented.collect { presented ->
                if (_flow.value?.prompt?.id != presented?.id) closeFlow()
            }
        }
    }

    // Intents
    /** The archive is on screen (once per signed-in archive): the first offer follows the tour. */
    fun onArchiveShown() {
        if (firstOffer?.isActive == true) return
        firstOffer = viewModelScope.launch {
            onboardingPreferences.state.first { it == OnboardingState.Hidden }
            coordinator.offerFirstRunIfNeeded()
        }
    }

    /**
     * An exam submission went through and its form closed: after a "no" to notifications, the moment the user waits
     * for the approval is the one to ask again (iOS `AddExamView.onSubmitted`).
     */
    fun onExamSubmitted() {
        viewModelScope.launch { coordinator.examWasSubmitted() }
    }

    /** The sheet is on screen. */
    fun onShown() {
        val flow = _flow.value ?: return
        viewModelScope.launch { flow.start() }
    }

    fun onEnable() {
        val flow = _flow.value ?: return
        viewModelScope.launch { flow.enableNotifications() }
    }

    /** The sheet opens the app's notification settings itself. */
    fun onOpenSettings() {
        _flow.value?.settingsWereOpened()
    }

    fun onNotNow() {
        val flow = _flow.value ?: return
        viewModelScope.launch { flow.declineNotifications() }
    }

    fun onAnswer(marketing: Boolean) {
        val flow = _flow.value ?: return
        viewModelScope.launch { flow.answerConsent(marketing) }
    }

    /** The sheet was swiped or backed away, or it closed itself after the flow ended. */
    fun onSheetClosed() {
        coordinator.dismiss()
    }

    // Helpers
    private suspend fun presentWhenFree() {
        combine(
            onboardingPreferences.state,
            modalPresence.count,
            notificationRouter.pending,
            sessionStore.state,
        ) { tour, modals, tap, session ->
            tour == OnboardingState.Hidden && modals == 0 && tap == null && session is SessionState.SignedIn
        }.first { it }
        val prompt = coordinator.present() ?: return
        val flow = NotificationPermissionFlow.open(prompt, notifications, settings, history, analytics)
        // Cleared while the system was asked whether it can still show its dialog: it never reached the screen.
        if (coordinator.presented.value?.id != prompt.id) return
        // An earlier run whose clearing was not seen yet is closed first, so its "no" is not lost.
        closeFlow()
        _flow.value = flow
    }

    private fun closeFlow() {
        val flow = _flow.value ?: return
        _flow.value = null
        viewModelScope.launch { flow.sheetDidClose() }
    }
}
