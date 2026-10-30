package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * What this device was already asked about notifications (iOS `NotificationPromptHistory`): the first offer of each
 * step happens once; after a "no" the step is offered exactly once more, no sooner than [REASK_DELAY] after the first
 * "no" (the permission after a successful exam submission, the campaign consent when the inbox opens); after that only
 * Account › Bildirim Ayarları. Per device, not per user, and kept through sign-outs (iOS parity).
 */
interface NotificationPromptHistory {

    /** Step 1/2 (the permission) was offered; 4.0.0's one-time prompt counts too. */
    suspend fun hasOfferedPriming(): Boolean

    /** Step 2/2 (the campaign consent) was offered. */
    suspend fun hasOfferedConsent(): Boolean

    suspend fun markPrimingOffered()

    suspend fun markConsentOffered()

    suspend fun markPermissionReaskShown()

    suspend fun markConsentReaskShown()

    /** Only the first "no" is kept: a later one cannot postpone the re-ask. */
    suspend fun recordPermissionDeclined()

    suspend fun recordConsentDeclined()

    suspend fun shouldReaskPermission(): Boolean

    suspend fun shouldReaskConsent(): Boolean

    companion object {
        val REASK_DELAY: Duration = Duration.ofDays(30)

        /** Due once not shown yet, a "no" recorded, and [REASK_DELAY] passed since it; it stays due until shown. */
        fun isReaskDue(shown: Boolean, declinedAt: Instant?, now: Instant): Boolean =
            !shown && declinedAt != null && !now.isBefore(declinedAt.plus(REASK_DELAY))
    }
}

/** The device's history in the app's DataStore, under iOS's key names. */
@Singleton
class DataStoreNotificationPromptHistory @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : NotificationPromptHistory {

    override suspend fun hasOfferedPriming(): Boolean =
        read().let { it[PRIMING_OFFERED] == true || it[LEGACY_PROMPTED] == true }

    override suspend fun hasOfferedConsent(): Boolean = read()[CONSENT_OFFERED] == true

    override suspend fun markPrimingOffered() = set(PRIMING_OFFERED)

    override suspend fun markConsentOffered() = set(CONSENT_OFFERED)

    override suspend fun markPermissionReaskShown() = set(PERMISSION_REASK_SHOWN)

    override suspend fun markConsentReaskShown() = set(CONSENT_REASK_SHOWN)

    override suspend fun recordPermissionDeclined() = recordFirst(PERMISSION_DECLINED_AT)

    override suspend fun recordConsentDeclined() = recordFirst(CONSENT_DECLINED_AT)

    override suspend fun shouldReaskPermission(): Boolean =
        read().let { isDue(it[PERMISSION_REASK_SHOWN] == true, it[PERMISSION_DECLINED_AT]) }

    override suspend fun shouldReaskConsent(): Boolean =
        read().let { isDue(it[CONSENT_REASK_SHOWN] == true, it[CONSENT_DECLINED_AT]) }

    // Helpers
    private suspend fun read(): Preferences = dataStore.data.first()

    private suspend fun set(key: Preferences.Key<Boolean>) {
        dataStore.edit { it[key] = true }
    }

    private suspend fun recordFirst(key: Preferences.Key<Long>) {
        dataStore.edit { if (key !in it) it[key] = clock.millis() }
    }

    private fun isDue(shown: Boolean, declinedAtMillis: Long?): Boolean =
        NotificationPromptHistory.isReaskDue(shown, declinedAtMillis?.let(Instant::ofEpochMilli), clock.instant())

    private companion object {
        // Keeps its pre-4.1.0 key, so a device that saw the old priming screen is not offered step 1/2 again.
        val PRIMING_OFFERED = booleanPreferencesKey("notifications.primingOffered.v1")
        val PERMISSION_DECLINED_AT = longPreferencesKey("notifications.permissionDeclinedAt.v1")
        val PERMISSION_REASK_SHOWN = booleanPreferencesKey("notifications.permissionReaskShown.v1")
        val CONSENT_OFFERED = booleanPreferencesKey("notifications.consentOffered.v1")
        val CONSENT_DECLINED_AT = longPreferencesKey("notifications.consentDeclinedAt.v1")
        val CONSENT_REASK_SHOWN = booleanPreferencesKey("notifications.consentReaskShown.v1")

        // 4.0.0's one-time AlertDialog ("Bildirimleri aç"): an offer of the permission step.
        val LEGACY_PROMPTED = booleanPreferencesKey("notifications.prompted")
    }
}
