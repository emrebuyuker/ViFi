package com.salticusteam.vifi.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.util.trackScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The signed-in account (iOS AccountViewModel, without account deletion yet): the profile name and verified
 * number, both followed live, and signing out. Signing out needs no navigation: the session gate replaces the
 * whole archive, this screen included, with the login screen.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    sessionStore: SessionStore,
    profileStore: ProfileStore,
    private val authService: AuthService,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    // State
    val uiState: StateFlow<AccountUiState> = combine(sessionStore.state, profileStore.state, ::accountState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = accountState(sessionStore.state.value, profileStore.state.value),
        )

    // Intents
    fun onScreenShown() {
        analytics.trackScreen(AnalyticsScreen.ACCOUNT)
    }

    /** Signs out after the user confirmed it. */
    fun signOut() {
        authService.signOut()
        analytics.track(AnalyticsEvent.SignedOut)
    }

    // Helpers
    private fun accountState(session: SessionState, profile: ProfileState) = AccountUiState(
        profileName = (profile as? ProfileState.Present)?.profile?.fullName,
        formattedPhoneNumber = (session as? SessionState.SignedIn)?.user?.formattedPhoneNumber,
    )
}
