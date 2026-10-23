package com.salticusteam.vifi.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.catalog.CatalogSource
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.ProfileService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.core.model.userMessage
import com.salticusteam.vifi.core.util.trackScreen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs both the profile-completion gate and the edit-profile screen (iOS ProfileFormViewModel): the editable
 * fields, their validation and the save. The phone number is not editable; it comes from the verified sign-in.
 *
 * A successful save updates [ProfileStore], which opens the gate; the edit screen also closes on
 * [ProfileFormEvent.Saved].
 */
@HiltViewModel(assistedFactory = ProfileFormViewModel.Factory::class)
class ProfileFormViewModel @AssistedInject constructor(
    /** The signed-in user; supplies the uid and the read-only phone number. */
    @Assisted private val user: AuthUser,
    /** The current profile when editing, `null` when completing it for the first time. */
    @Assisted private val existing: UserProfile?,
    private val profileService: ProfileService,
    private val profileStore: ProfileStore,
    private val authService: AuthService,
    private val analytics: AnalyticsTracker,
    private val catalogSource: CatalogSource,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(user: AuthUser, existing: UserProfile?): ProfileFormViewModel
    }

    // State
    private val _uiState = MutableStateFlow(ProfileFormUiState.initial(user.phoneNumber, existing))
    val uiState: StateFlow<ProfileFormUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<ProfileFormEvent>(Channel.BUFFERED)
    val events: Flow<ProfileFormEvent> = _events.receiveAsFlow()

    // Intents
    init {
        viewModelScope.launch {
            val catalog = catalogSource.catalog()
            _uiState.update { it.copy(catalog = catalog) }
        }
    }

    /** Logged on every appearance of the completion or edit screen. */
    fun onScreenShown() {
        analytics.trackScreen(
            if (existing == null) AnalyticsScreen.PROFILE_COMPLETION else AnalyticsScreen.EDIT_PROFILE,
        )
    }

    fun onNameChange(value: String) = edit { it.copy(name = value) }

    fun onSurnameChange(value: String) = edit { it.copy(surname = value) }

    fun onGenderSelected(gender: Gender) = edit { it.copy(gender = gender) }

    fun onPickerOpen(field: CatalogField) = edit { it.copy(picker = field) }

    fun onPickerDismiss() = _uiState.update { it.copy(picker = null) }

    /** Takes the value chosen in the open picker and returns to the form. */
    fun onCatalogSelected(selection: CatalogSelection) {
        _uiState.update { state ->
            when (state.picker) {
                CatalogField.UNIVERSITY -> state.copy(university = selection, picker = null)
                CatalogField.FACULTY -> state.copy(faculty = selection, picker = null)
                CatalogField.DEPARTMENT -> state.copy(department = selection, picker = null)
                null -> state
            }
        }
    }

    /** Saves the profile. Ignored while a save is running or the form is incomplete. */
    fun save() {
        val state = _uiState.value
        if (state.isWorking) return
        val profile = state.builtProfile ?: return
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            try {
                profileService.saveProfile(profile, user.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, error = e.userMessage) }
                return@launch
            }
            // Stays busy: the gate replaces the completion screen, and the edit screen closes.
            profileStore.update(profile)
            analytics.track(if (existing == null) AnalyticsEvent.ProfileCompleted else AnalyticsEvent.ProfileUpdated)
            _events.send(ProfileFormEvent.Saved)
        }
    }

    /** Signs out from the completion gate, so nobody is trapped on it. The session gate shows the login screen. */
    fun signOut() {
        if (_uiState.value.isWorking) return
        authService.signOut()
    }

    // Helpers
    /** Applies a field edit unless a save is running. */
    private fun edit(transform: (ProfileFormUiState) -> ProfileFormUiState) {
        _uiState.update { state -> if (state.isWorking) state else transform(state) }
    }
}
