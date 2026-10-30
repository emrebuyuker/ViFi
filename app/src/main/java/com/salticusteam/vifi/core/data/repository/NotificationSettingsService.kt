package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the signed-in user's campaign-notification consent, `users/{uid}/settings/notifications`
 * (iOS `NotificationSettingsServicing`).
 */
interface NotificationSettingsService {

    /**
     * The consent record: `null` when the user never answered. Emits right away and again on each change; a flow
     * that fails throws [com.salticusteam.vifi.core.model.NotificationSettingsError] and ends, so a failed listener
     * never reads as a missing consent.
     */
    fun consentChanges(uid: String): Flow<NotificationConsent?>

    /**
     * Records the answer with the wording version and a server timestamp. Throws when it is rejected (the rules
     * accept exactly `{marketing, source, version, updatedAt}`); a write made offline is queued by Firestore.
     */
    suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource)
}
