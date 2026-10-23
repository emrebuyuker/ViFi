package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.RewardsConfigReader
import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.SubmissionTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Reward values held in memory for the mock flavor, previews and tests. */
class FakeRewardsConfigReader(config: RewardsConfig? = null) : RewardsConfigReader {

    /** The values served; set it to simulate a Console change. */
    val config = MutableStateFlow(config)

    override fun rewardsChanges(): Flow<RewardsConfig?> = config

    companion object {
        /** iOS StubRewardsConfigReader's per-tier amounts. */
        val SAMPLE_TIERS = mapOf(
            SubmissionTier.NEW_UNIVERSITY to 500,
            SubmissionTier.NEW_FACULTY to 300,
            SubmissionTier.NEW_DEPARTMENT to 200,
            SubmissionTier.NEW_COURSE to 120,
            SubmissionTier.NEW_EXAM to 60,
            SubmissionTier.DUPLICATE to 10,
        )
    }
}
