package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AppUpdateConfig
import kotlinx.coroutines.flow.Flow

/** Reads remote configuration (the `config` subtree) from the backend. */
interface AppConfigReader {
    /**
     * A live stream of the update policy: the current value, then again on every change. Emits `null` when
     * the policy is missing, malformed, or the read is rejected. Collection ends when the collector is cancelled.
     */
    fun configChanges(): Flow<AppUpdateConfig?>
}
