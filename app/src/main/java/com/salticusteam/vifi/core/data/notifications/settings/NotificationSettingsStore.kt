package com.salticusteam.vifi.core.data.notifications.settings

import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The signed-in user's campaign consent as the app knows it (iOS `NotificationSettingsStore`). Three states are kept
 * apart, never guessed one from another: not loaded yet, never answered ([consent] `null`), and the answer.
 */
data class NotificationSettingsState(
    val availability: Availability = Availability.LOADING,
    val consent: NotificationConsent? = null,
) {
    enum class Availability { LOADING, READY, UNAVAILABLE }

    val isLoaded: Boolean get() = availability == Availability.READY

    val hasAnswered: Boolean get() = consent != null

    /** Off until the user said yes: no record, an unread record or a "no" all mean no campaigns. */
    val marketingEnabled: Boolean get() = consent?.marketing ?: false
}

/**
 * Follows `users/<uid>/settings/notifications` while a user is signed in (profile completion not needed) and records
 * the answer. A different user starts over from "loading"; sign-out forgets everything, and what a previous user's
 * listener delivers late is ignored. Main-thread confined, like the session that drives it.
 */
@Singleton
class NotificationSettingsStore @Inject constructor(
    private val service: NotificationSettingsService,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // State
    private val _state = MutableStateFlow(NotificationSettingsState())
    val state: StateFlow<NotificationSettingsState> = _state.asStateFlow()

    // Internal state
    private var userId: String? = null
    private var observation: Job? = null
    private var job: Job? = null

    // Intents
    /** Follows [sessions] for the process lifetime; later calls do nothing. */
    fun start(sessions: Flow<SessionState>) {
        if (job != null) return
        job = scope.launch {
            sessions.collect { session ->
                when (session) {
                    is SessionState.SignedIn -> if (userId != session.user.id) observe(session.user.id)
                    SessionState.SignedOut -> reset()
                    SessionState.Unknown -> Unit
                }
            }
        }
    }

    /** Waits until the record is no longer loading, at most [timeoutMillis]; whether it was read. */
    suspend fun waitUntilLoaded(timeoutMillis: Long = LOAD_TIMEOUT_MS): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            state.first { it.availability != NotificationSettingsState.Availability.LOADING }
        }?.isLoaded ?: false

    /**
     * Records the answer and shows it at once. A rejected write puts the previous answer back (`false`); one that is not
     * acknowledged within [SAVE_TIMEOUT_MS] counts as saved (`true`): Firestore queued it and its cache already has it,
     * so waiting longer would only hang the screen. Without a user nothing is written (`false`).
     */
    suspend fun setMarketing(enabled: Boolean, source: NotificationConsentSource): Boolean {
        val uid = userId ?: return false
        val previous = _state.value.consent
        _state.update { it.copy(consent = NotificationConsent(marketing = enabled)) }
        return try {
            val acknowledged = withTimeoutOrNull(SAVE_TIMEOUT_MS) { service.setMarketing(uid, enabled, source) } != null
            if (!acknowledged) LogCategory.APP.log.i("The consent write is not acknowledged yet; it stays queued")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.w(e, "Recording the campaign consent failed")
            if (userId == uid) _state.update { it.copy(consent = previous) }
            false
        }
    }

    // Helpers
    private fun observe(uid: String) {
        observation?.cancel()
        userId = uid
        _state.value = NotificationSettingsState()
        observation = scope.launch {
            try {
                service.consentChanges(uid).collect { consent ->
                    if (userId == uid) {
                        _state.value = NotificationSettingsState(NotificationSettingsState.Availability.READY, consent)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never an answer: an unreadable record is not "no", and is not restarted on its own.
                if (userId == uid) {
                    _state.value = NotificationSettingsState(NotificationSettingsState.Availability.UNAVAILABLE)
                }
            }
        }
    }

    private fun reset() {
        observation?.cancel()
        observation = null
        userId = null
        _state.value = NotificationSettingsState()
    }

    internal companion object {
        const val LOAD_TIMEOUT_MS = 5_000L

        /** iOS `saveTimeout`. */
        const val SAVE_TIMEOUT_MS = 4_000L
    }
}
