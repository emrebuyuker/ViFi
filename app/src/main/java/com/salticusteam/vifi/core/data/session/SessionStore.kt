package com.salticusteam.vifi.core.data.session

import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.AuthUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether a user is signed in; the app root shows the login screen or the archive accordingly. */
sealed interface SessionState {
    /** The stored session has not been restored yet (briefly, at launch). */
    data object Unknown : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: AuthUser) : SessionState
}

/**
 * The app-wide session, following [AuthService.userChanges] for the process lifetime (iOS SessionStore). Starts
 * signed in when a stored user is already known, otherwise [SessionState.Unknown] until the first report.
 */
@Singleton
class SessionStore @Inject constructor(
    authService: AuthService,
    @ApplicationScope scope: CoroutineScope,
) {
    val state: StateFlow<SessionState> = authService.userChanges()
        .map { user -> user?.let(SessionState::SignedIn) ?: SessionState.SignedOut }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = authService.currentUser?.let(SessionState::SignedIn) ?: SessionState.Unknown,
        )

    /** The signed-in user, if any. */
    val user: AuthUser? get() = (state.value as? SessionState.SignedIn)?.user
}
