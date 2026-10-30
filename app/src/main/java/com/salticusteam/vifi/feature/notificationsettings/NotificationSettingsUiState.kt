package com.salticusteam.vifi.feature.notificationsettings

import androidx.annotation.StringRes
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsState
import com.salticusteam.vifi.core.model.NotificationPermission

/**
 * Account › Bildirim Ayarları (iOS NotificationSettingsViewModel): the OS permission with the way to turn it on, and
 * the campaign switch bound live to the consent record.
 */
data class NotificationSettingsUiState(
    /** The OS permission as last read; `null` until the first read. */
    val permission: NotificationPermission? = null,
    /** The system dialog can still be shown (NDEC-01); otherwise only the app's settings can turn notifications on. */
    val systemCanAsk: Boolean = false,
    /** The system dialog is up. */
    val isRequestingPermission: Boolean = false,
    /** Whether the consent record is still loading, was read, or could not be read. */
    val consentAvailability: NotificationSettingsState.Availability = NotificationSettingsState.Availability.LOADING,
    /** Campaigns are on: the user said yes. Off when unanswered, unread or "no". */
    val marketingEnabled: Boolean = false,
    /** A consent write is in flight. */
    val isSavingConsent: Boolean = false,
    /** The last switch change could not be saved (it went back); cleared by the next attempt. */
    val saveFailed: Boolean = false,
) {
    /** "Bildirimleri Aç" is offered: not allowed yet, and the system dialog can still be shown. */
    val canRequestPermission: Boolean
        get() = permission != null && permission != NotificationPermission.AUTHORIZED && systemCanAsk

    /** "Ayarlar'ı Aç" is offered: not allowed, and only the app's notification settings can turn it on now. */
    val needsSettingsApp: Boolean
        get() = permission != null && permission != NotificationPermission.AUTHORIZED && !systemCanAsk

    /** The record was read, so the switch shows the user's real answer. */
    val isConsentAvailable: Boolean
        get() = consentAvailability == NotificationSettingsState.Availability.READY

    /** The record could not be read. */
    val isConsentUnavailable: Boolean
        get() = consentAvailability == NotificationSettingsState.Availability.UNAVAILABLE

    /** The switch takes a change: the answer is known and no other change is being saved. */
    val canChangeMarketing: Boolean
        get() = isConsentAvailable && !isSavingConsent

    /** How the permission reads on screen ("Sessiz" has no Android counterpart, NDEC-01). */
    @get:StringRes
    val permissionLabel: Int
        get() = when (permission) {
            NotificationPermission.AUTHORIZED -> R.string.notification_settings_permission_authorized
            NotificationPermission.DENIED -> R.string.notification_settings_permission_denied
            NotificationPermission.NOT_DETERMINED -> R.string.notification_settings_permission_not_determined
            null -> R.string.common_loading
        }

    /**
     * The explanation under the permission row; none once allowed or before the first read. It follows the offered
     * button: a "no" the system can still ask again about reads as not given yet, not as "go to Settings".
     */
    @get:StringRes
    val permissionNote: Int?
        get() = when {
            permission == null || permission == NotificationPermission.AUTHORIZED -> null
            needsSettingsApp -> R.string.notification_settings_permission_denied_note
            else -> R.string.notification_settings_permission_not_determined_note
        }
}
