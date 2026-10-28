package com.salticusteam.vifi.core.data.billing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.salticusteam.vifi.core.model.SubscriptionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** On device because FirebaseFunctionsException.Code initialises an Android SparseArray. */
@RunWith(AndroidJUnit4::class)
class FirebaseExamPurchaseErrorMappingTest {

    @Test
    fun recordError_mapsCallableCodesLikeIos() {
        val map: (Code) -> SubscriptionError? = FirebaseExamPurchaseBackend::recordError
        assertEquals(SubscriptionError.FailedVerification, map(Code.FAILED_PRECONDITION))
        assertEquals(SubscriptionError.FailedVerification, map(Code.INVALID_ARGUMENT))
        assertEquals(SubscriptionError.StoreUnavailable, map(Code.UNAVAILABLE))
        assertEquals(SubscriptionError.StoreUnavailable, map(Code.DEADLINE_EXCEEDED))
        assertNull(map(Code.NOT_FOUND))
        assertNull(map(Code.PERMISSION_DENIED))
    }
}
