package com.salticusteam.vifi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives [ProfileGate] from the shared [ProfileStore] (iOS ProfileGate's `.task(id: user.id)` load). Clears the
 * store on sign-out, so the next user starts from a clean read.
 */
@HiltViewModel
class ProfileGateViewModel @Inject constructor(
    private val profileStore: ProfileStore,
    sessionStore: SessionStore,
) : ViewModel() {

    // State
    val profile: StateFlow<ProfileState> = profileStore.state

    // Intents
    init {
        viewModelScope.launch {
            sessionStore.state.collect { session ->
                if (session == SessionState.SignedOut) profileStore.reset()
            }
        }
    }

    /** Reads [uid]'s profile; called from a `LaunchedEffect(uid)`, so a newer user cancels the older read. */
    suspend fun load(uid: String) = profileStore.load(uid)

    /** Retries a failed read. */
    fun retry(uid: String) {
        viewModelScope.launch { profileStore.load(uid) }
    }
}
