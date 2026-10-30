package com.salticusteam.vifi.feature.notifications

import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.NotificationPromptHistory
import com.salticusteam.vifi.core.data.repository.AnalyticsTracker
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One run of the notification flow on screen (iOS `NotificationPermissionFlowModel`): step 1/2 asks for the system
 * permission, and only after a yes there, step 2/2 asks for the campaign consent (the Play counterpart of Apple 4.5.4).
 *
 * It records what is shown and answered in [NotificationPromptHistory] (for the re-ask schedule), writes the consent
 * through [NotificationSettingsStore] and reports analytics. Whatever is left unanswered (the sheet swiped away, a
 * push that needs the screen, a sign-out) counts as a "no" for the schedule ([sheetDidClose]), though no consent is
 * written for it: not answering never opts anyone in. Main-thread confined, like the presenter that drives it.
 */
class NotificationPermissionFlow private constructor(
    val prompt: NotificationPrompt,
    needsSettingsApp: Boolean,
    private val notifications: NotificationService,
    private val settings: NotificationSettingsStore,
    private val history: NotificationPromptHistory,
    private val analytics: AnalyticsTracker,
) {

    enum class Page { PERMISSION, CONSENT }

    data class State(
        val page: Page,
        /** The system permission as last read (it is read again after the system dialog). */
        val permission: NotificationPermission,
        /** The system will not show its dialog any more: only the app's notification settings can turn them on. */
        val needsSettingsApp: Boolean = false,
        /** The system dialog or a consent write is in progress. */
        val isWorking: Boolean = false,
        /** The consent answer being saved, so its own button shows the progress; `null` when none. */
        val answering: Boolean? = null,
        /** The consent could not be saved; the user may try again or close. */
        val saveFailed: Boolean = false,
        /** The flow has ended; the sheet closes itself. */
        val isFinished: Boolean = false,
    ) {
        /** The consent is answered, not swiped away; after a failed save the sheet may be closed. */
        val canDismiss: Boolean get() = page == Page.PERMISSION || saveFailed
    }

    // State
    private val _state = MutableStateFlow(
        State(
            page = if (prompt.kind == NotificationPrompt.Kind.PERMISSION) Page.PERMISSION else Page.CONSENT,
            permission = prompt.permission,
            needsSettingsApp = needsSettingsApp,
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    // Internal state
    private var hasStarted = false

    /** The current page was answered (or its "no" recorded), so closing it is not a further "no". */
    private var isResolved = false

    /** The sheet is gone; a system dialog answered after that leads nowhere. */
    private var isClosed = false

    // Showing
    /** The first page is on screen: records it and reports it, once. */
    suspend fun start() {
        if (hasStarted) return
        hasStarted = true
        when (_state.value.page) {
            Page.PERMISSION -> {
                if (prompt.source == NotificationPromptSource.REASK) {
                    history.markPermissionReaskShown()
                } else {
                    history.markPrimingOffered()
                }
                analytics.track(AnalyticsEvent.NotificationPrimingShown(prompt.source))
            }
            Page.CONSENT -> consentDidAppear()
        }
    }

    // Step 1/2: the permission
    /**
     * "Bildirimleri Aç": shows the system dialog. A yes leads on to the consent unless it was already answered (on
     * another device); a refusal counts as a "no" and ends the flow without asking for campaigns.
     */
    suspend fun enableNotifications() {
        val current = _state.value
        if (current.page != Page.PERMISSION || current.isWorking || current.isFinished) return
        _state.update { it.copy(isWorking = true) }
        try {
            analytics.track(AnalyticsEvent.NotificationPrimingAccepted(prompt.source))
            notifications.requestAuthorization()
            val permission = notifications.permission()
            _state.update { it.copy(permission = permission) }
            // Closed while the system dialog was up (a tapped push, a sign-out): its "no" is recorded already.
            if (isClosed) return
            when {
                permission != NotificationPermission.AUTHORIZED -> {
                    isResolved = true
                    history.recordPermissionDeclined()
                    finish()
                }
                settings.state.value.hasAnswered -> finish()
                else -> {
                    _state.update { it.copy(page = Page.CONSENT) }
                    consentDidAppear()
                }
            }
        } finally {
            _state.update { it.copy(isWorking = false) }
        }
    }

    /** "Ayarlar'ı Aç" (the sheet opens the app's notification settings): the user is acting on it, so it is no "no". */
    fun settingsWereOpened() {
        if (_state.value.isFinished) return
        analytics.track(AnalyticsEvent.NotificationPrimingAccepted(prompt.source))
        finish()
    }

    /** "Şimdi Değil": the system dialog is kept for a user who says yes. */
    suspend fun declineNotifications() {
        val current = _state.value
        if (current.page != Page.PERMISSION || current.isWorking || current.isFinished) return
        isResolved = true
        analytics.track(AnalyticsEvent.NotificationPrimingDeclined(prompt.source))
        history.recordPermissionDeclined()
        finish()
    }

    // Step 2/2: the consent
    /**
     * "Evet, fırsatları bildir" / "Hayır, teşekkürler": both write the answer. A failed write keeps the page open with
     * an error and reports nothing, so the answer is never silently lost.
     */
    suspend fun answerConsent(marketing: Boolean) {
        val current = _state.value
        if (current.page != Page.CONSENT || current.isWorking || current.isFinished) return
        _state.update { it.copy(isWorking = true, answering = marketing, saveFailed = false) }
        try {
            if (!settings.setMarketing(marketing, NotificationConsentSource.PROMPT)) {
                _state.update { it.copy(saveFailed = true) }
                return
            }
            isResolved = true
            analytics.track(
                if (marketing) {
                    AnalyticsEvent.MarketingConsentAccepted(prompt.source)
                } else {
                    AnalyticsEvent.MarketingConsentDeclined(prompt.source)
                },
            )
            if (!marketing) history.recordConsentDeclined()
            finish()
        } finally {
            _state.update { it.copy(isWorking = false, answering = null) }
        }
    }

    // Closing
    /** The sheet went away, whatever the way. An unanswered page counts as a "no" for the re-ask schedule. */
    suspend fun sheetDidClose() {
        isClosed = true
        if (isResolved) return
        isResolved = true
        when (_state.value.page) {
            Page.PERMISSION -> history.recordPermissionDeclined()
            Page.CONSENT -> history.recordConsentDeclined()
        }
    }

    // Helpers
    private suspend fun consentDidAppear() {
        history.markConsentOffered()
        // Only the stand-alone consent offer is the consent's re-ask; the consent page that follows a permission offer
        // (even a re-asked one) is its first.
        if (prompt.kind == NotificationPrompt.Kind.CONSENT && prompt.source == NotificationPromptSource.REASK) {
            history.markConsentReaskShown()
        }
        analytics.track(AnalyticsEvent.MarketingConsentShown(prompt.source))
    }

    private fun finish() {
        isResolved = true
        _state.update { it.copy(isFinished = true) }
    }

    companion object {
        /**
         * The flow for [prompt]. A permission page whose system dialog can no longer be shown (Android 12 and lower
         * with notifications off, or after "don't ask again") offers the app's notification settings instead (NDEC-01).
         */
        suspend fun open(
            prompt: NotificationPrompt,
            notifications: NotificationService,
            settings: NotificationSettingsStore,
            history: NotificationPromptHistory,
            analytics: AnalyticsTracker,
        ): NotificationPermissionFlow = NotificationPermissionFlow(
            prompt = prompt,
            needsSettingsApp = prompt.kind == NotificationPrompt.Kind.PERMISSION && !notifications.canRequestPermission(),
            notifications = notifications,
            settings = settings,
            history = history,
            analytics = analytics,
        )
    }
}
