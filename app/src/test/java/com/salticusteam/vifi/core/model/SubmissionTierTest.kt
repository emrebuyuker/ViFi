package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubmissionTierTest {

    private fun classify(u: Boolean, f: Boolean, d: Boolean, c: Boolean, e: Boolean) =
        SubmissionTier.classify(u, f, d, c, e)

    @Test
    fun `classify when first missing level decides the tier`() {
        assertEquals(SubmissionTier.NEW_UNIVERSITY, classify(false, false, false, false, false))
        assertEquals(SubmissionTier.NEW_FACULTY, classify(true, false, false, false, false))
        assertEquals(SubmissionTier.NEW_DEPARTMENT, classify(true, true, false, false, false))
        assertEquals(SubmissionTier.NEW_COURSE, classify(true, true, true, false, false))
        assertEquals(SubmissionTier.NEW_EXAM, classify(true, true, true, true, false))
        assertEquals(SubmissionTier.DUPLICATE, classify(true, true, true, true, true))
    }

    @Test
    fun `fromRaw when config key returns tier`() {
        assertEquals(SubmissionTier.NEW_COURSE, SubmissionTier.fromRaw("newCourse"))
        assertNull(SubmissionTier.fromRaw("bogus"))
    }

    @Test
    fun `rewards coins when tier configured returns its amount else legacy value`() {
        val config = RewardsConfig(examUploadCoins = 5, tiers = mapOf(SubmissionTier.NEW_UNIVERSITY to 50))

        assertEquals(50, config.coins(SubmissionTier.NEW_UNIVERSITY))
        assertEquals(5, config.coins(SubmissionTier.NEW_EXAM))
        assertNull(RewardsConfig().coins(SubmissionTier.NEW_EXAM))
    }
}
