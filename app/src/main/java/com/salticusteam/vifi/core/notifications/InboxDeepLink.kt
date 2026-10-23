package com.salticusteam.vifi.core.notifications

import android.os.Bundle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Open the inbox" requests from a notification tap. The launching Activity records one, the signed-in archive
 * host consumes it — so a tap that cold-starts the app survives until the host exists (a plain event would be lost).
 */
@Singleton
class InboxDeepLink @Inject constructor() {

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }

    /** Records a request when [extras] come from a notification tap (ours, or the system tray's FCM notification). */
    fun handle(extras: Bundle?) {
        if (opensInbox(extras?.keySet().orEmpty(), extras?.getBoolean(EXTRA_OPEN_INBOX) == true)) request()
    }

    companion object {
        const val EXTRA_OPEN_INBOX = "open_inbox"

        /** FCM adds `google.message_id` to the launch intent of a notification the system tray showed. */
        private const val FCM_MESSAGE_ID = "google.message_id"

        fun opensInbox(extraKeys: Set<String>, openInboxFlag: Boolean): Boolean =
            openInboxFlag || FCM_MESSAGE_ID in extraKeys
    }
}
