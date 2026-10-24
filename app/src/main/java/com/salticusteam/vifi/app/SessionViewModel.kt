package com.salticusteam.vifi.app

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.data.session.SessionTeardown
import com.salticusteam.vifi.core.notifications.PushTokenRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the app-wide session to [AppRoot]'s sign-in gate, and starts the app-wide sign-out teardown and the
 * signed-in user's push token registration. Started
 * here rather than in the Application, so the mock graph is built after MainActivity read its launch arguments.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    sessionStore: SessionStore,
    sessionTeardown: SessionTeardown,
    pushTokenRegistration: PushTokenRegistration,
) : ViewModel() {

    // State
    val session: StateFlow<SessionState> = sessionStore.state

    // Intents
    init {
        sessionTeardown.start()
        pushTokenRegistration.start()
    }
}
