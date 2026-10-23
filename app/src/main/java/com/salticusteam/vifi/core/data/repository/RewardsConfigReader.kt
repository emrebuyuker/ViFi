package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.RewardsConfig
import kotlinx.coroutines.flow.Flow

/** Reads the reward configuration (the `config/rewards` subtree) from the backend. */
interface RewardsConfigReader {
    /**
     * A live stream of the reward values: the current value, then again on every change. Emits `null` when the
     * values are missing, malformed, or the read is rejected. Collection ends when the collector is cancelled.
     */
    fun rewardsChanges(): Flow<RewardsConfig?>
}
