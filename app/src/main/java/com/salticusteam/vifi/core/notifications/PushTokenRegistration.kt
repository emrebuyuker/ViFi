package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.data.repository.DeviceTokenStore
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the signed-in user's `devices/<token>` entry current (N11): written when a user signs in (or the app
 * starts signed in) and whenever FCM rotates the token. Only a signed-in user may write it, so a token issued
 * while signed out is not kept: the next sign-in reads the current token anyway. Each user/token pair is written
 * once per process.
 */
@Singleton
class PushTokenRegistration @Inject constructor(
    private val sessionStore: SessionStore,
    private val notifications: NotificationService,
    private val store: DeviceTokenStore,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // Internal state
    private val mutex = Mutex()
    private var job: Job? = null
    private var registered: Pair<String, String>? = null

    // Intents
    /** Starts following the session for the process lifetime; later calls do nothing. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            sessionStore.state.collect { state ->
                if (state is SessionState.SignedIn) register(state.user.id, token = null)
            }
        }
    }

    /** FCM issued [token]; stored right away for a signed-in user. */
    fun onNewToken(token: String) {
        val uid = sessionStore.user?.id ?: return
        scope.launch { register(uid, token) }
    }

    // Helpers
    private suspend fun register(uid: String, token: String?) {
        try {
            val current = token ?: notifications.token() ?: return
            mutex.withLock {
                if (registered == uid to current) return
                store.register(uid, current)
                registered = uid to current
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.w(e, "Registering the push token failed")
        }
    }
}
