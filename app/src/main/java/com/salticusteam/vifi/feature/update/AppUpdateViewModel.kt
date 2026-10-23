package com.salticusteam.vifi.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.repository.AppUpdateChecker
import com.salticusteam.vifi.core.model.AppUpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Keeps the update gate in sync with the live policy for as long as the app is open, so a newly required
 * version gates even a running session. A dismissed optional version is remembered in memory only, so it is
 * suggested once per app session and again for a later, different version.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    appUpdateChecker: AppUpdateChecker,
) : ViewModel() {

    // State
    private val dismissedOptionalVersion = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppUpdateUiState> = combine(
        appUpdateChecker.statusChanges(),
        dismissedOptionalVersion,
        ::reduce,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, AppUpdateUiState())

    // Intents

    /** Any dismissal of the optional prompt (update or later) hides that version until the app restarts. */
    fun onOptionalDismissed() {
        val version = uiState.value.optional?.version ?: return
        dismissedOptionalVersion.update { version }
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
