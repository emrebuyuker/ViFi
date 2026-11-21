package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.feature.conversionadmin.ConversionFixture
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** iOS `FirebaseExamConversionService.conversionError(from:)` and the draft-edit write the rules allow. */
class ExamConversionErrorsTest {

    @Test
    fun `callable statuses map to the Turkish admin errors`() {
        assertEquals(ExamConversionError.PermissionDenied, ExamConversionErrors.fromStatus("PERMISSION_DENIED", null))
        assertEquals(ExamConversionError.PermissionDenied, ExamConversionErrors.fromStatus("UNAUTHENTICATED", null))
        assertEquals(ExamConversionError.NotFound, ExamConversionErrors.fromStatus("NOT_FOUND", null))
        assertEquals(ExamConversionError.BudgetExceeded, ExamConversionErrors.fromStatus("RESOURCE_EXHAUSTED", null))
        assertEquals(ExamConversionError.Offline, ExamConversionErrors.fromStatus("UNAVAILABLE", null))
        assertEquals(ExamConversionError.Offline, ExamConversionErrors.fromStatus("DEADLINE_EXCEEDED", null))
    }

    @Test
    fun `a precondition or argument refusal carries the server's message as is`() {
        assertEquals(
            ExamConversionError.NotAllowed("Sınav hazır değil."),
            ExamConversionErrors.fromStatus("FAILED_PRECONDITION", "Sınav hazır değil."),
        )
        assertEquals(
            ExamConversionError.InvalidInput("Sınav adı listeden seçilmeli."),
            ExamConversionErrors.fromStatus("INVALID_ARGUMENT", "  Sınav adı listeden seçilmeli. "),
        )
    }

    @Test
    fun `a refusal without a readable message and unknown statuses are not guessed at`() {
        assertNull(ExamConversionErrors.fromStatus("FAILED_PRECONDITION", "  "))
        assertNull(ExamConversionErrors.fromStatus("INVALID_ARGUMENT", null))
        assertNull(ExamConversionErrors.fromStatus("INTERNAL", "boom"))
        assertNull(ExamConversionErrors.fromStatus("ABORTED", null))
    }

    @Test
    fun `firestore reads and draft edits map no admin claim, a missing draft and no connection`() {
        assertEquals(ExamConversionError.PermissionDenied, ExamConversionErrors.fromFirestoreStatus("PERMISSION_DENIED"))
        assertEquals(ExamConversionError.NotFound, ExamConversionErrors.fromFirestoreStatus("NOT_FOUND"))
        assertEquals(ExamConversionError.Offline, ExamConversionErrors.fromFirestoreStatus("UNAVAILABLE"))
        assertNull(ExamConversionErrors.fromFirestoreStatus("INVALID_ARGUMENT"))
    }

    @Test
    fun `domain errors pass through and a transport failure is offline`() {
        assertSame(ExamConversionError.BudgetExceeded, ExamConversionErrors.map(ExamConversionError.BudgetExceeded))
        assertEquals(ExamConversionError.Offline, ExamConversionErrors.map(IOException("timeout")))
    }

    @Test
    fun `a draft edit writes only the fields the rules allow, signed by the admin`() {
        val question: ExamQuestion = ConversionFixture.question(order = 3, needsReview = true)
        val stamp = Any()

        val fields = DraftEdit.fields(question, uid = "admin-uid", editedAt = stamp)

        assertEquals(DraftEdit.KEYS, fields.keys)
        assertEquals(setOf("text", "speech", "choices", "answer", "solution", "needsReview", "editedBy", "editedAt"), fields.keys)
        assertEquals("admin-uid", fields["editedBy"])
        assertSame(stamp, fields["editedAt"])
        assertEquals(false, fields["needsReview"])
        assertEquals("2 + 2 kaçtır?", fields["text"])
        assertEquals("003", DraftEdit.documentId(question.order))
        assertEquals("012", DraftEdit.documentId(12))
    }
}
