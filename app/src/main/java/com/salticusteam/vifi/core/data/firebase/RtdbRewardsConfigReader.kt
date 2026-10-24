package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.salticusteam.vifi.core.data.repository.RewardsConfigReader
import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reads the reward values from the Realtime Database `config/rewards/ios` node with a live listener (iOS
 * `FirebaseRewardsConfigReader`).
 *
 * BE-07 decision (2026-10-24): Android uses the iOS node itself, so both platforms always promise the same
 * amounts from a single source in the Console. The node is tiny and kept synced; reads need no signed-in user,
 * but App Check guards them. The client never writes here.
 */
class RtdbRewardsConfigReader @Inject constructor(
    database: FirebaseDatabase,
) : RewardsConfigReader {

    private val ios = database.getReference(IOS_PATH).also { it.keepSynced(true) }

    override fun rewardsChanges(): Flow<RewardsConfig?> = ios.rewards().distinctUntilChanged()

    private fun DatabaseReference.rewards(): Flow<RewardsConfig?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(parse(snapshot.value))
            }

            override fun onCancelled(error: DatabaseError) {
                LogCategory.APP.log.e(error.toException(), "Rewards config listener cancelled")
                trySend(null)
            }
        }
        addValueEventListener(listener)
        awaitClose { removeEventListener(listener) }
    }.conflate()

    companion object {
        const val IOS_PATH = "config/rewards/ios"

        /**
         * Parses a node's value. RTDB numbers arrive as `Long`; a missing or non-positive count is dropped. The
         * optional `tiers` child carries per-tier amounts keyed by [SubmissionTier.raw]; unknown keys are dropped.
         */
        fun parse(value: Any?): RewardsConfig? {
            val map = value as? Map<*, *> ?: return null
            val tiers = (map["tiers"] as? Map<*, *>).orEmpty().entries.mapNotNull { (key, raw) ->
                val tier = SubmissionTier.fromRaw(key as? String) ?: return@mapNotNull null
                positiveInt(raw)?.let { tier to it }
            }.toMap()
            return RewardsConfig(examUploadCoins = positiveInt(map["examUploadCoins"]), tiers = tiers)
        }

        private fun positiveInt(value: Any?): Int? = (value as? Number)?.toInt()?.takeIf { it > 0 }
    }
}
