package com.salticusteam.vifi.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.util.LaunchArg
import com.salticusteam.vifi.core.util.LaunchArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The one-time notification permission prompt, shown once per device after sign-in and profile (iOS
 * `NotificationPrompt`). Joining the `all` topic moved to `PushTopicSubscription` (every start, signed in or not).
 */
@HiltViewModel
class NotificationPromptViewModel @Inject constructor(
    private val notifications: NotificationService,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    // State
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    init {
        viewModelScope.launch {
            val forced = LaunchArgs.isSet(LaunchArg.NOTIFICATIONS_PROMPT)
            val prompted = preferences.notificationsPrompted.first()
            if (forced || (!prompted && notifications.permission() == NotificationPermission.NOT_DETERMINED)) {
                _isVisible.value = true
            }
        }
    }

    // Intents
    fun onAllow() {
        _isVisible.value = false
        viewModelScope.launch {
            // Records "prompted" itself; a denial is not asked again (Inbox offers the system settings instead).
            notifications.requestAuthorization()
            preferences.setNotificationsPrompted(true)
        }
    }

    fun onLater() {
        _isVisible.value = false
        viewModelScope.launch { preferences.setNotificationsPrompted(true) }
    }
}
