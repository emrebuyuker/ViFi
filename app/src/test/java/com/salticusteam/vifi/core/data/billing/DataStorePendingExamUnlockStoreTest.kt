package com.salticusteam.vifi.core.data.billing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePendingExamUnlockStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `save, then clear only the exam it still names`() = runTest {
        val file = tmp.newFile("pending.preferences_pb").also { it.delete() }
        val store = DataStorePendingExamUnlockStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                produceFile = { file },
            ),
        )
        assertNull(store.examId())

        store.save("exam-1")
        store.save("exam-2")
        store.clear("exam-1")
        assertEquals("exam-2", store.examId())

        store.clear("exam-2")
        assertNull(store.examId())
    }
}
