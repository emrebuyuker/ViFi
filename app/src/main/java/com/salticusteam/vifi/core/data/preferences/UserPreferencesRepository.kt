package com.salticusteam.vifi.core.data.preferences

import kotlinx.coroutines.flow.Flow

/** Small device-local flags (iOS UserDefaults: UpdatePromptDefaults, prefetch/notification defaults). */
interface UserPreferencesRepository {

    /** Optional update version the user chose to skip; `null` when none. */
    val skippedUpdateVersion: Flow<String?>

    /** Whether the notification permission rationale was already shown. */
    val notificationsPrompted: Flow<Boolean>

    /** When the last archive prefetch completed (epoch milliseconds); `null` when it never did. */
    val archivePrefetchedAt: Flow<Long?>

    /** `null` clears the skipped version. */
    suspend fun setSkippedUpdateVersion(version: String?)

    suspend fun setNotificationsPrompted(prompted: Boolean)

    suspend fun setArchivePrefetchedAt(epochMillis: Long)
}
