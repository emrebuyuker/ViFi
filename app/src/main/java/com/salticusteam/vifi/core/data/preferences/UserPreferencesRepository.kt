package com.salticusteam.vifi.core.data.preferences

import kotlinx.coroutines.flow.Flow

/** Small device-local flags (iOS UserDefaults: UpdatePromptDefaults, prefetch/notification defaults). */
interface UserPreferencesRepository {

    /** Optional update version the user chose to skip; `null` when none. */
    val skippedUpdateVersion: Flow<String?>

    /** Whether the notification permission rationale was already shown. */
    val notificationsPrompted: Flow<Boolean>

    /** Whether the one-time archive prefetch already ran. */
    val prefetchDone: Flow<Boolean>

    /** `null` clears the skipped version. */
    suspend fun setSkippedUpdateVersion(version: String?)

    suspend fun setNotificationsPrompted(prompted: Boolean)

    suspend fun setPrefetchDone(done: Boolean)
}
