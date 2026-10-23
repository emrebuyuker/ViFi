package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.data.moderation.PublishedExamRef
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.ModerationError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Firestore/Storage error codes need the Firebase runtime; only the pure parts run on the JVM. */
class FirebaseModerationBackendTest {

    @Test
    fun `publishedExamRef reads the course and non-empty file paths`() {
        val data = mapOf(
            "courseId" to "blg101",
            "files" to listOf(
                mapOf("storagePath" to "pdfs/a.pdf", "order" to 1L),
                mapOf("storagePath" to "", "order" to 2L),
                "garbage",
            ),
        )

        assertEquals(PublishedExamRef("blg101", listOf("pdfs/a.pdf")), FirebaseModerationBackend.publishedExamRef(data))
    }

    @Test
    fun `publishedExamRef without course or files is empty`() {
        assertEquals(PublishedExamRef(null, emptyList()), FirebaseModerationBackend.publishedExamRef(emptyMap()))
    }

    @Test
    fun `moderationError when transport fails maps to offline`() {
        assertSame(ModerationError.Offline, FirebaseModerationBackend.moderationError(IOException("reset")))
    }

    @Test
    fun `moderationError passes a domain error through and falls back to the generic message`() {
        assertSame(ModerationError.NotPending, FirebaseModerationBackend.moderationError(ModerationError.NotPending))
        assertEquals(
            ModerationError.Unknown(GENERIC_ERROR_MESSAGE),
            FirebaseModerationBackend.moderationError(IllegalStateException("boom")),
        )
    }
}
