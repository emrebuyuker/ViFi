package com.salticusteam.vifi.core.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.salticusteam.vifi.core.model.ExamUnlockError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** On device because FirebaseFunctionsException.Code initialises an Android SparseArray. */
@RunWith(AndroidJUnit4::class)
class FirebaseExamUnlockErrorMappingTest {

    @Test
    fun unlockError_mapsCallableCodesLikeIos() {
        assertEquals(ExamUnlockError.InsufficientCoins, FirebaseExamUnlockService.unlockError(Code.FAILED_PRECONDITION))
        assertEquals(ExamUnlockError.NotFound, FirebaseExamUnlockService.unlockError(Code.NOT_FOUND))
        assertEquals(ExamUnlockError.NotSignedIn, FirebaseExamUnlockService.unlockError(Code.PERMISSION_DENIED))
        assertEquals(ExamUnlockError.NotSignedIn, FirebaseExamUnlockService.unlockError(Code.UNAUTHENTICATED))
        assertEquals(ExamUnlockError.Offline, FirebaseExamUnlockService.unlockError(Code.UNAVAILABLE))
        assertEquals(ExamUnlockError.Offline, FirebaseExamUnlockService.unlockError(Code.DEADLINE_EXCEEDED))
        assertNull(FirebaseExamUnlockService.unlockError(Code.INTERNAL))
    }
}
