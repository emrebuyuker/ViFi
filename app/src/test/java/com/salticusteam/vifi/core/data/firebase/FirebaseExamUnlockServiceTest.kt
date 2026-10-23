package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.ExamUnlockError
import com.salticusteam.vifi.core.model.ExamUnlockResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Callable error-code mapping is covered by the instrumented FirebaseExamUnlockErrorMappingTest (Code needs Android). */
class FirebaseExamUnlockServiceTest {

    @Test
    fun `unlockError when transport fails maps to offline`() {
        assertEquals(ExamUnlockError.Offline, FirebaseExamUnlockService.unlockError(IOException("reset")))
    }

    @Test
    fun `parseResult reads balance and alreadyUnlocked`() {
        assertEquals(
            ExamUnlockResult(balance = 250, alreadyUnlocked = true),
            FirebaseExamUnlockService.parseResult(mapOf("balance" to 250L, "alreadyUnlocked" to true)),
        )
    }

    @Test
    fun `parseResult when fields missing falls back to zero and false`() {
        assertEquals(ExamUnlockResult(0, false), FirebaseExamUnlockService.parseResult(null))
        assertEquals(ExamUnlockResult(0, false), FirebaseExamUnlockService.parseResult(mapOf("balance" to "x")))
    }
}
