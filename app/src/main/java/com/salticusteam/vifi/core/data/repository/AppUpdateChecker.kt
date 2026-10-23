package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AppUpdateStatus
import kotlinx.coroutines.flow.Flow

/** Evaluates the remote update policy against the installed version. */
interface AppUpdateChecker {
    /**
     * A live stream of the update requirement: the current status, then again whenever the remote policy
     * changes. A required update stays latched when the policy later becomes unreadable, so the app is never
     * silently un-gated. Collection ends when the collector is cancelled.
     */
    fun statusChanges(): Flow<AppUpdateStatus>
}
