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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reads the reward values from the Realtime Database `config/rewards` subtree with live listeners (iOS
 * `FirebaseRewardsConfigReader`).
 *
 * BE-07 decision: an `android` node wins when it has usable values; otherwise the iOS node is reused, so the
 * Console keeps a single source until the platforms ever need different amounts. Both nodes are tiny and kept
 * synced; reads need no signed-in user, but App Check guards them. The client never writes here.
 */
class RtdbRewardsConfigReader @Inject constructor(
    database: FirebaseDatabase,
) : RewardsConfigReader {

    private val android = database.getReference(ANDROID_PATH).also { it.keepSynced(true) }
    private val ios = database.getReference(IOS_PATH).also { it.keepSynced(true) }

    override fun rewardsChanges(): Flow<RewardsConfig?> =
        combine(android.rewards(), ios.rewards(), ::preferred).distinctUntilChanged()

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
        const val ANDROID_PATH = "config/rewards/android"
        const val IOS_PATH = "config/rewards/ios"

        /** The Android values when they carry anything usable, else the iOS ones. */
        fun preferred(android: RewardsConfig?, ios: RewardsConfig?): RewardsConfig? =
            android?.takeIf { it.examUploadCoins != null || it.tiers.isNotEmpty() } ?: ios

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
