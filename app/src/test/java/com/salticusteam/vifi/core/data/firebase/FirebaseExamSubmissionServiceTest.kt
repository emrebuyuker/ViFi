package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.ExamSubmissionError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Firestore/Storage error codes need the Firebase runtime; only the transport and fallback paths run on the JVM. */
class FirebaseExamSubmissionServiceTest {

    @Test
    fun `submissionError when transport fails maps to offline`() {
        assertSame(ExamSubmissionError.Offline, FirebaseExamSubmissionService.submissionError(IOException("reset")))
    }

    @Test
    fun `submissionError passes a domain error through`() {
        assertSame(
            ExamSubmissionError.NotSignedIn,
            FirebaseExamSubmissionService.submissionError(ExamSubmissionError.NotSignedIn),
        )
    }

    @Test
    fun `submissionError when unexpected falls back to the generic message`() {
        val error = FirebaseExamSubmissionService.submissionError(IllegalStateException("boom"))

        assertEquals(ExamSubmissionError.Unknown(FirebaseExamSubmissionService.GENERIC_MESSAGE), error)
        assertEquals("unknown", error.analyticsReason)
    }
}
