package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.SubmissionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtdbRewardsConfigReaderTest {

    @Test
    fun `parse reads flat coins and known positive tiers`() {
        val config = RtdbRewardsConfigReader.parse(
            mapOf(
                "examUploadCoins" to 10L,
                "tiers" to mapOf("newExam" to 60L, "newUniversity" to 500L, "bogus" to 5L, "duplicate" to 0L),
            ),
        )

        assertEquals(
            RewardsConfig(
                examUploadCoins = 10,
                tiers = mapOf(SubmissionTier.NEW_EXAM to 60, SubmissionTier.NEW_UNIVERSITY to 500),
            ),
            config,
        )
    }

    @Test
    fun `parse when node missing returns null and non-positive coins are dropped`() {
        assertNull(RtdbRewardsConfigReader.parse(null))
        assertEquals(RewardsConfig(), RtdbRewardsConfigReader.parse(mapOf("examUploadCoins" to -3L)))
    }

    @Test
    fun `preferred uses android values when set and falls back to ios`() {
        val android = RewardsConfig(examUploadCoins = 20)
        val ios = RewardsConfig(examUploadCoins = 10)

        assertEquals(android, RtdbRewardsConfigReader.preferred(android, ios))
        assertEquals(ios, RtdbRewardsConfigReader.preferred(null, ios))
        assertEquals(ios, RtdbRewardsConfigReader.preferred(RewardsConfig(), ios))
        assertNull(RtdbRewardsConfigReader.preferred(null, null))
    }
}
