package com.salticusteam.vifi.core.data.practice

import com.salticusteam.vifi.core.data.notifications.admin.FirebaseStatus
import com.salticusteam.vifi.core.model.PracticeError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The practice callable's failure mapping (iOS `FirebasePracticeService.practiceError`). */
class FirebasePracticeServiceTest {

    @Test
    fun `an unauthenticated or denied call is not signed in`() {
        assertEquals(PracticeError.NotSignedIn, FirebasePracticeService.practiceError(FirebaseStatus.UNAUTHENTICATED))
        assertEquals(PracticeError.NotSignedIn, FirebasePracticeService.practiceError(FirebaseStatus.PERMISSION_DENIED))
    }

    @Test
    fun `an unavailable or timed out call is offline`() {
        assertEquals(PracticeError.Offline, FirebasePracticeService.practiceError(FirebaseStatus.UNAVAILABLE))
        assertEquals(PracticeError.Offline, FirebasePracticeService.practiceError(FirebaseStatus.DEADLINE_EXCEEDED))
    }

    @Test
    fun `other statuses are not classified here`() {
        assertNull(FirebasePracticeService.practiceError(FirebaseStatus.INVALID_ARGUMENT))
        assertNull(FirebasePracticeService.practiceError(FirebaseStatus.OTHER))
    }

    @Test
    fun `a transport failure is offline, a domain error passes through, anything else is unknown`() {
        assertEquals(PracticeError.Offline, FirebasePracticeService.practiceError(IOException("no route")))
        assertEquals(PracticeError.InvalidResponse, FirebasePracticeService.practiceError(PracticeError.InvalidResponse))
        assertEquals(PracticeError.Unknown, FirebasePracticeService.practiceError(IllegalStateException("boom")))
    }

    @Test
    fun `an unknown failure never carries the raw message`() {
        val error = FirebasePracticeService.practiceError(IllegalStateException("English technical text"))

        assertEquals(PracticeError.Unknown.userMessage, error.userMessage)
    }
}
