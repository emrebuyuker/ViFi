package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.data.repository.DeviceRegistrar
import com.salticusteam.vifi.core.data.repository.DeviceRegistration
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps this install registered under the signed-in user (iOS `DeviceRegistrationStore`), so targeted and personal
 * pushes reach it. A write needs both the FCM token and a signed-in user, whichever arrives last triggers it; the same
 * pair is written once per process (in memory only, so every cold start rewrites it and an app update's `appVersion`
 * is picked up). A failure is logged and retried on the next trigger (token refresh, sign-in, launch).
 *
 * Independent of the notification permission and of campaign consent: the token exists without the permission, and
 * consent is enforced by the server at delivery. Sign-out resets the in-memory state; [unregister] also removes the
 * document, for the screens that sign out or delete the account.
 */
@Singleton
class DeviceRegistrationStore @Inject constructor(
    private val registrar: DeviceRegistrar,
    private val notifications: NotificationService,
    @ApplicationScope private val scope: CoroutineScope,
) : DeviceRegistration {

    // Internal state (FCM reports tokens on its own thread, so every read and write holds the lock)
    private val lock = Any()
    private var token: String? = null
    private var userId: String? = null
    private var registered: Pair<String, String>? = null
    private var inFlight: Pair<String, String>? = null
    private var inFlightWrite: Job? = null
    private var job: Job? = null

    // Intents
    /**
     * Reads the current token and follows [sessions] for the process lifetime; later calls do nothing. Given by the
     * session owner, like the push tap router's.
     */
    fun start(sessions: Flow<SessionState>) {
        if (job != null) return
        job = scope.launch {
            launch { tokenDidChange(notifications.token()) }
            sessions.collect { state ->
                when (state) {
                    is SessionState.SignedIn -> userDidSignIn(state.user.id)
                    SessionState.SignedOut -> userDidSignOut()
                    SessionState.Unknown -> Unit
                }
            }
        }
    }

    /** FCM issued [token] (at start and on every refresh); a blank one is not registered. */
    fun tokenDidChange(token: String?) {
        synchronized(lock) { this.token = token?.trim()?.takeIf { it.isNotEmpty() } }
        registerIfNeeded()
    }

    /** A user signed in (not gated on a completed profile, iOS parity); another user starts over. */
    fun userDidSignIn(uid: String) {
        synchronized(lock) {
            if (userId != uid) {
                registered = null
                inFlight = null
            }
            userId = uid
        }
        registerIfNeeded()
    }

    /** In-memory reset only: nothing is written until the next sign-in. */
    fun userDidSignOut() {
        synchronized(lock) {
            userId = null
            registered = null
            inFlight = null
        }
    }

    /**
     * Stops registering first, so a token refresh during the removal cannot register again, then removes this install
     * from [uid] within [UNREGISTER_TIMEOUT_MS]. Never throws: a slow or failed removal must not block signing out;
     * a device left behind is pruned by the server when its token stops working or moves to another account.
     */
    override suspend fun unregister(uid: String) {
        val pendingWrite = synchronized(lock) {
            userId = null
            registered = null
            inFlight = null
            inFlightWrite
        }
        val finished = withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
            try {
                // A write still landing would recreate the document right after the delete.
                pendingWrite?.join()
                registrar.unregister(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogCategory.APP.log.w(e, "Removing this device from push failed")
            }
        }
        if (finished == null) LogCategory.APP.log.w("Removing this device from push timed out")
    }

    // Helpers
    private fun registerIfNeeded() {
        val pair = synchronized(lock) {
            val uid = userId ?: return
            val current = token ?: return
            val next = uid to current
            if (next == registered || next == inFlight) return
            inFlight = next
            next
        }
        val write = scope.launch {
            try {
                registrar.register(uid = pair.first, token = pair.second)
                synchronized(lock) { if (inFlight == pair) registered = pair }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogCategory.APP.log.w(e, "Registering this device for push failed")
            } finally {
                synchronized(lock) { if (inFlight == pair) inFlight = null }
            }
        }
        synchronized(lock) { inFlightWrite = write }
    }

    private companion object {
        /** iOS `unregisterTimeout`. */
        const val UNREGISTER_TIMEOUT_MS = 3_000L
    }
}
