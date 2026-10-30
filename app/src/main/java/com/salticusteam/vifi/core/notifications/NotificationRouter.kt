package com.salticusteam.vifi.core.notifications

import android.content.Intent
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.NotificationPayloadParser
import com.salticusteam.vifi.core.navigation.ModalPresence
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/**
 * Routes a tapped push to the inbox (iOS `NotificationRouter`). The launching Activity records the tap; the
 * signed-in archive host takes it once every gate is passed, the tour is over and no modal is in the way
 * ([deliveries]), then opens the inbox highlighting that notification. That way a cold-start tap survives until the
 * host exists, and a tap while a sheet holds the user's work waits for it instead of being lost.
 *
 * Opening the inbox from the bell and closing it are plain navigation (`Inbox()` without a highlight, Back); the
 * highlight travels as the route argument, so the back stack is the only "is the inbox shown" state.
 */
@Singleton
class NotificationRouter @Inject constructor(
    private val modalPresence: ModalPresence,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /** A tap waiting to open the inbox; [notificationId] is null for a push without one (opens with no highlight). */
    data class Request(val notificationId: String?)

    // State
    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    // Internal state
    private var job: Job? = null

    // Intents
    /**
     * Follows [sessions] for the process lifetime ([sessionDidChange]); later calls do nothing. Given by the session
     * owner rather than injected, so MainActivity can hold the router before the mock graph reads its launch arguments.
     */
    fun start(sessions: Flow<SessionState>) {
        if (job != null) return
        job = scope.launch {
            // The first report is a change from "not restored yet", so a start signed out drops a waiting tap.
            var previous: SessionState = SessionState.Unknown
            sessions.collect { state ->
                sessionDidChange(previous, state)
                previous = state
            }
        }
    }

    /** Records a tap; a newer tap replaces one still waiting. */
    fun receive(notificationId: String?) {
        _pending.value = Request(notificationId)
    }

    /** Records the tap [intent] carries, if any: ours (the foreground push) or the system tray's (a background one). */
    fun handle(intent: Intent?) {
        // Reopening the app from recents re-delivers the launching intent; that tap was already handled.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val extras = intent.extras ?: return
        val request = tapRequest(
            extraKeys = extras.keySet(),
            openInboxFlag = extras.getBoolean(EXTRA_OPEN_INBOX),
            notificationId = extras.getString(NotificationContent.EXTRA_NOTIFICATION_ID),
        ) ?: return
        receive(request.notificationId)
    }

    /** Drops a waiting tap. */
    fun reset() {
        _pending.value = null
    }

    /**
     * Session rules: signing out drops a waiting tap; a tap made while signed out belongs to no account, so the next
     * sign-in drops it too; a cold start (`Unknown → SignedIn`) keeps the tap that launched the app.
     */
    fun sessionDidChange(from: SessionState, to: SessionState) {
        when {
            to is SessionState.SignedOut -> reset()
            from is SessionState.SignedOut && to is SessionState.SignedIn -> reset()
            else -> Unit
        }
    }

    /**
     * The taps to deliver, each once, while the host collects: the newest waiting tap as soon as [ready] (the tour is
     * over) and no registered modal is shown. Other modals are never closed for it (they may hold the user's work).
     * A tap leaves [pending] only after its delivery was collected, so none is lost to a cancelled collector.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun deliveries(ready: Flow<Boolean>): Flow<Request> =
        combine(pending, ready) { request, isReady -> request?.takeIf { isReady } }
            .distinctUntilChanged()
            .transformLatest { request ->
                if (request == null) return@transformLatest
                modalPresence.awaitNoModal()
                emit(request)
                _pending.compareAndSet(request, null)
            }

    companion object {
        /** Set on the tap intent of a foreground push ([PushNotifier]). */
        const val EXTRA_OPEN_INBOX = "open_inbox"

        /** FCM adds `google.message_id` to the launch intent of a notification the system tray showed. */
        private const val FCM_MESSAGE_ID = "google.message_id"

        /**
         * The request a launch carries: a tap on our notification or on a system tray one; any other launch opens
         * nothing. The id must pass the server's pattern, otherwise the inbox opens with no highlight; `kind` is
         * not read (iOS parity).
         */
        fun tapRequest(extraKeys: Set<String>, openInboxFlag: Boolean, notificationId: String?): Request? {
            if (!openInboxFlag && FCM_MESSAGE_ID !in extraKeys) return null
            val id = NotificationPayloadParser.notificationIdFromPush(
                mapOf(NotificationContent.EXTRA_NOTIFICATION_ID to notificationId),
            )
            return Request(id)
        }
    }
}
