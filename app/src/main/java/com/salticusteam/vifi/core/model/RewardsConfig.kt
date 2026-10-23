package com.salticusteam.vifi.core.model

// Added in 2.5 — remote reward values for user contributions (`config/rewards/<platform>`), all optional.
/**
 * With nothing configured the add-exam flow shows a generic "you'll earn coins" note instead of an amount.
 * [tiers] holds per-novelty-tier amounts; [examUploadCoins] is the legacy flat value kept as a fallback.
 */
data class RewardsConfig(
    /** Legacy flat award for an approved submission; the fallback when a tier is not configured. */
    val examUploadCoins: Int? = null,
    /** Coins per contribution tier (always positive when present). Empty when none are configured. */
    val tiers: Map<SubmissionTier, Int> = emptyMap(),
) {
    /**
     * The coins promised for a submission of [tier]: its configured amount, else the legacy flat value, else
     * `null` (nothing is promised and the UI shows a generic note).
     */
    fun coins(tier: SubmissionTier): Int? = tiers[tier] ?: examUploadCoins
}
