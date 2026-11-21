package com.salticusteam.vifi.core.data.structured

import com.google.firebase.Timestamp
import com.salticusteam.vifi.core.data.firebase.ExamQuestionParser
import com.salticusteam.vifi.core.data.structured.FirestoreExamQuestionReader.Companion.toQuestionReadError
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.QuestionFigure
import java.io.IOException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The live question reader's Firestore-free parts (iOS `FirebaseExamQuestionReader.convertingTimestamps` /
 * `archiveError`). Firestore error codes share the archive's mapping, covered on device by
 * `FirestoreArchiveErrorMappingTest` (the code enum needs Android).
 */
class FirestoreExamQuestionReaderTest {

    @Test
    fun `top-level timestamps become instants before parsing, other values stay`() {
        val data = mapOf(
            ExamQuestionParser.Field.UPDATED_AT to Timestamp(1_700_000_000, 0),
            ExamQuestionParser.Field.ORDER to 2L,
            ExamQuestionParser.Field.TEXT to "\$x\$",
            ExamQuestionParser.Field.ANSWER to mapOf("text" to "1"),
        )

        val converted = FirestoreExamQuestionReader.convertingTimestamps(data)

        assertEquals(Instant.ofEpochSecond(1_700_000_000), converted[ExamQuestionParser.Field.UPDATED_AT])
        assertEquals(2L, converted[ExamQuestionParser.Field.ORDER])
        assertEquals(mapOf("text" to "1"), converted[ExamQuestionParser.Field.ANSWER])
        val question = ExamQuestionParser.question("e-002", converted, "e")
        assertEquals(Instant.ofEpochSecond(1_700_000_000), question?.updatedAt)
    }

    @Test
    fun `read failures map to the archive's user-facing errors`() {
        assertEquals(ArchiveError.Offline, IOException("offline").toQuestionReadError("e"))
        assertEquals(ArchiveError.NotFound, ArchiveError.NotFound.toQuestionReadError("e"))
        assertEquals(ArchiveError.Unknown(GENERIC_ERROR_MESSAGE), IllegalStateException("boom").toQuestionReadError("e"))
    }

    @Test
    fun `figures resolve to canonical token-free URLs in the app's bucket`() {
        val resolver = StorageQuestionFigureUrlResolver("vifi-831a8.appspot.com")
        val figure = QuestionFigure("questionFigures/job1/001-1.jpg", 1200, 800)

        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/vifi-831a8.appspot.com/o/questionFigures%2Fjob1%2F001-1.jpg?alt=media",
            resolver.url(figure),
        )
    }
}
