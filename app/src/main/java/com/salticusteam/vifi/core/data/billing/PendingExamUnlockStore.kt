package com.salticusteam.vifi.core.data.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The exam a paid unlock is being bought for, kept on the device until the purchase is recorded (iOS
 * `pendingExamUnlockId`): a purchase that completes later (a pending payment, a dropped network) is finished for it
 * when the purchase itself does not name the exam.
 */
interface PendingExamUnlockStore {
    /** The exam of the paid unlock in progress, or `null`. */
    suspend fun examId(): String?

    suspend fun save(examId: String)

    /** Clears the record if it still names [examId] (a later purchase may have replaced it). */
    suspend fun clear(examId: String)
}

class DataStorePendingExamUnlockStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PendingExamUnlockStore {

    override suspend fun examId(): String? = dataStore.data.first()[EXAM_ID]

    override suspend fun save(examId: String) {
        dataStore.edit { it[EXAM_ID] = examId }
    }

    override suspend fun clear(examId: String) {
        dataStore.edit { if (it[EXAM_ID] == examId) it.remove(EXAM_ID) }
    }

    private companion object {
        val EXAM_ID = stringPreferencesKey("billing.pendingExamUnlock.v1")
    }
}
