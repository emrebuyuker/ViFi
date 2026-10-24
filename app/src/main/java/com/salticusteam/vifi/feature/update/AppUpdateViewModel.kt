package com.salticusteam.vifi.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.AppUpdateChecker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsValues
import com.salticusteam.vifi.core.model.AppUpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps the update gate in sync with the live policy for as long as the app is open, so a newly required
 * version gates even a running session. A dismissed optional version is remembered in memory only, so it is
 * suggested once per app session and again for a later, different version.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    appUpdateChecker: AppUpdateChecker,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    private val dismissedOptionalVersion = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUpdateUiState> = combine(
        appUpdateChecker.statusChanges(),
        dismissedOptionalVersion,
        ::reduce,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, AppUpdateUiState())

    // Intents
    init {
        // Each prompt is counted once per version, when it actually appears (iOS RootView).
        trackShown({ it.required?.version }, isRequired = true)
        trackShown({ it.optional?.version }, isRequired = false)
    }

    /** "Güncelle" on the optional prompt; the dismissal follows through [onOptionalDismissed]. */
    fun onOptionalUpdateChosen() {
        analytics.track(AnalyticsEvent.UpdateAction(AnalyticsValues.UPDATE_ACTION_UPDATE))
    }

    /** "Daha Sonra" or a tap-away on the optional prompt; the dismissal follows through [onOptionalDismissed]. */
    fun onOptionalLaterChosen() {
        analytics.track(AnalyticsEvent.UpdateAction(AnalyticsValues.UPDATE_ACTION_LATER))
    }

    /** Any dismissal of the optional prompt (update or later) hides that version until the app restarts. */
    fun onOptionalDismissed() {
        val version = uiState.value.optional?.version ?: return
        dismissedOptionalVersion.update { version }
    }

    // Helpers
    private fun trackShown(version: (AppUpdateUiState) -> String?, isRequired: Boolean) {
        viewModelScope.launch {
            uiState.map(version).distinctUntilChanged().filterNotNull().collect {
                analytics.track(AnalyticsEvent.UpdatePromptShown(isRequired))
            }
        }
    }

    companion object {
        /** Required wins; an optional update is hidden once its version was dismissed. */
        internal fun reduce(status: AppUpdateStatus, dismissedVersion: String?): AppUpdateUiState = when (status) {
            AppUpdateStatus.UpToDate -> AppUpdateUiState()
            is AppUpdateStatus.Required -> AppUpdateUiState(required = status.update)
            is AppUpdateStatus.Optional ->
                AppUpdateUiState(optional = status.update.takeIf { it.version != dismissedVersion })
        }
    }
}
