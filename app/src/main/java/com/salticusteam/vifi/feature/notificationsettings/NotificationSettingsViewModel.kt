package com.salticusteam.vifi.feature.notificationsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsState
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Account › Bildirim Ayarları (iOS NotificationSettingsViewModel): reads the OS permission (again on every return to
 * the screen, the user may come back from the system settings), asks for it when the system still can, and turns
 * campaigns on or off through the consent record.
 *
 * "Bildirimleri Aç" here reports nothing and leaves the prompt history alone; only a saved switch change is reported
 * (`marketing_consent_accepted|declined`, source `settings`). Turning campaigns off never arms a re-ask.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val notifications: NotificationService,
    private val settings: NotificationSettingsStore,
    private val analytics: AnalyticsTracker,
    /** Outlives this screen: a switch change already on its way is still reported if the screen closes meanwhile. */
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    // State
    /** The permission and progress half of the state; the consent fields come from the store. */
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())

    val uiState: StateFlow<NotificationSettingsUiState> =
        combine(_uiState, settings.state, ::withConsent)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = withConsent(_uiState.value, settings.state.value),
            )

    // Internal state
    private var permissionRead: Job? = null

    // Intents
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.NOTIFICATION_SETTINGS)
    }

    /** The screen came to the front (opened, or back from the system settings): the permission is read again. */
    fun onResume() {
        permissionRead?.cancel()
        permissionRead = viewModelScope.launch { readPermission() }
    }

    /** Shows the system dialog, then reads the permission again. Ignored when it cannot be shown or is already up. */
    fun onEnable() {
        val state = _uiState.value
        if (!state.canRequestPermission || state.isRequestingPermission) return
        _uiState.update { it.copy(isRequestingPermission = true) }
        viewModelScope.launch {
            try {
                notifications.requestAuthorization()
                readPermission()
            } finally {
                _uiState.update { it.copy(isRequestingPermission = false) }
            }
        }
    }

    /**
     * Turns campaigns on or off. The switch shows the new value at once and goes back, with the error, when saving
     * fails. Ignored until the record is read and while another change is being saved.
     */
    fun onMarketingChange(enabled: Boolean) {
        if (!settings.state.value.isLoaded || _uiState.value.isSavingConsent) return
        _uiState.update { it.copy(isSavingConsent = true, saveFailed = false) }
        appScope.launch {
            try {
                if (settings.setMarketing(enabled, NotificationConsentSource.SETTINGS)) {
                    analytics.track(
                        if (enabled) {
                            AnalyticsEvent.MarketingConsentAccepted(NotificationPromptSource.SETTINGS)
                        } else {
                            AnalyticsEvent.MarketingConsentDeclined(NotificationPromptSource.SETTINGS)
                        },
                    )
                } else {
                    _uiState.update { it.copy(saveFailed = true) }
                }
            } finally {
                _uiState.update { it.copy(isSavingConsent = false) }
            }
        }
    }

    // Helpers
    private suspend fun readPermission() {
        val permission = notifications.permission()
        val systemCanAsk = notifications.canRequestPermission()
        _uiState.update { it.copy(permission = permission, systemCanAsk = systemCanAsk) }
    }

    private fun withConsent(state: NotificationSettingsUiState, consent: NotificationSettingsState) = state.copy(
        consentAvailability = consent.availability,
        marketingEnabled = consent.marketingEnabled,
    )
}
