package com.salticusteam.vifi.feature.notifications

import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.NotificationPromptHistory
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One run of the notification flow (iOS `NotificationPrompt`): which step it opens on and why. */
data class NotificationPrompt(
    val kind: Kind,
    val source: NotificationPromptSource,
    /** The system permission when it was queued (a re-ask while denied offers the app's settings instead). */
    val permission: NotificationPermission,
    /** Tells two runs apart, so the same kind queued again is a new sheet. */
    val id: Long = nextId.incrementAndGet(),
) {
    enum class Kind {
        /** Step 1/2 (the system permission), then step 2/2 when it was granted and the consent is unanswered. */
        PERMISSION,

        /** Step 2/2 alone: "may ViFi send you campaigns?" */
        CONSENT,
    }

    private companion object {
        val nextId = AtomicLong()
    }
}

/**
 * Decides when the notification flow is offered (iOS `NotificationPromptCoordinator`, Android mapping NDEC-01) and
 * holds it in a single slot until the app shows it: [pending] waits for the archive with no modal in the way, then
 * [present] moves it to [presented]; while either is taken nothing new is queued.
 *
 * - First run, after the onboarding tour: the permission step while it was never offered and the system has not
 *   answered; when notifications are already allowed (always so below Android 13 unless switched off), the consent
 *   step once if the record says "never answered" (the consent catch-up).
 * - After a successful exam submission: the permission step once more, 30 days after its first "no".
 * - When the inbox opens: the consent step once more, 30 days after its first "no", while campaigns are off.
 *
 * Android has no quiet ("provisional") permission, so iOS's silent pre-authorization has no counterpart.
 */
@Singleton
class NotificationPromptCoordinator @Inject constructor(
    private val notifications: NotificationService,
    private val settings: NotificationSettingsStore,
    private val history: NotificationPromptHistory,
) {

    // State
    private val _pending = MutableStateFlow<NotificationPrompt?>(null)
    val pending: StateFlow<NotificationPrompt?> = _pending.asStateFlow()

    private val _presented = MutableStateFlow<NotificationPrompt?>(null)
    val presented: StateFlow<NotificationPrompt?> = _presented.asStateFlow()

    /** No run is waiting or on screen. */
    val isFree: Boolean get() = _pending.value == null && _presented.value == null

    // Intents
    /** The first offer, once the archive is up and the tour is over. */
    suspend fun offerFirstRunIfNeeded() {
        if (!isFree) return
        val permission = notifications.permission()
        // A device that saw 4.0.0's one-time prompt (or the step itself) without allowing: that was its "no".
        if (history.hasOfferedPriming() && permission != NotificationPermission.AUTHORIZED) {
            history.recordPermissionDeclined()
        }
        when (permission) {
            NotificationPermission.NOT_DETERMINED -> if (!history.hasOfferedPriming()) {
                queue(NotificationPrompt.Kind.PERMISSION, NotificationPromptSource.PROMPT, permission)
            }
            NotificationPermission.AUTHORIZED -> if (
                !history.hasOfferedConsent() &&
                settings.waitUntilLoaded(CATCH_UP_WAIT_MS) &&
                !settings.state.value.hasAnswered
            ) {
                queue(NotificationPrompt.Kind.CONSENT, NotificationPromptSource.PROMPT, permission)
            }
            // Never offered first while denied: only Account › Bildirim Ayarları leads to the settings then.
            NotificationPermission.DENIED -> Unit
        }
    }

    /** The permission once more after a successful submission ("Onaylanınca haber verelim mi?"). */
    suspend fun examWasSubmitted() {
        if (!isFree || !history.shouldReaskPermission()) return
        val permission = notifications.permission()
        if (permission == NotificationPermission.AUTHORIZED) return
        queue(NotificationPrompt.Kind.PERMISSION, NotificationPromptSource.REASK, permission)
    }

    /**
     * The campaign consent once more when the inbox opens; whether it was queued. It does not look at the system
     * permission: campaigns are listed in the inbox too.
     */
    suspend fun inboxConsentReask(): Boolean {
        if (!isFree || !history.shouldReaskConsent()) return false
        if (!settings.waitUntilLoaded(REASK_WAIT_MS) || settings.state.value.marketingEnabled) return false
        return queue(NotificationPrompt.Kind.CONSENT, NotificationPromptSource.REASK, notifications.permission())
    }

    /** The app shows the waiting run now; `null` when none waits. */
    fun present(): NotificationPrompt? {
        val prompt = _pending.value ?: return null
        _pending.value = null
        _presented.value = prompt
        return prompt
    }

    /**
     * Clears the waiting and the shown run (a tapped push, sign-out, the sheet closed). A page left unanswered is
     * recorded as a "no" by the flow itself; one that never reached the screen is not used up.
     */
    fun dismiss() {
        _pending.value = null
        _presented.value = null
    }

    // Helpers
    /** Re-checked after the reads above: something else may have been queued meanwhile. */
    private fun queue(
        kind: NotificationPrompt.Kind,
        source: NotificationPromptSource,
        permission: NotificationPermission,
    ): Boolean {
        if (!isFree) return false
        _pending.value = NotificationPrompt(kind, source, permission)
        return true
    }

    internal companion object {
        /** The consent catch-up waits this long for the record (iOS 5 s). */
        const val CATCH_UP_WAIT_MS = 5_000L

        /** The inbox re-ask waits this long (iOS 2 s). */
        const val REASK_WAIT_MS = 2_000L
    }
}
