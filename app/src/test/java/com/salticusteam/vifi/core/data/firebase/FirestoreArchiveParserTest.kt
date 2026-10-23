package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreArchiveParserTest {

    private val course = ArchivePath(
        listOf("itu", "itu-muh", "itu-muh-blg", "itu-muh-blg-blg101"),
        listOf("İTÜ", "Mühendislik", "Bilgisayar", "BLG 101"),
    )

    // --- items: catalog levels ---

    @Test
    fun `items at root builds universities with slug ids, display names and child counts`() {
        val docs = listOf(
            "odtu" to mapOf<String, Any?>("name" to "ORTA DOĞU TEKNİK ÜNİVERSİTESİ", "childCount" to 4L),
            "itu" to mapOf<String, Any?>("name" to "İSTANBUL TEKNİK ÜNİVERSİTESİ", "childCount" to 12L),
        )

        val items = FirestoreArchiveParser.items(docs, ArchivePath.ROOT)

        assertEquals(listOf("İSTANBUL TEKNİK ÜNİVERSİTESİ", "ORTA DOĞU TEKNİK ÜNİVERSİTESİ"), items.map { it.name })
        assertEquals(listOf("itu"), items.first().path.components)
        assertEquals(ArchiveLevel.UNIVERSITY, items.first().level)
        assertEquals(12, items.first().childCount)
        assertNull(items.first().exam)
    }

    @Test
    fun `items clamps a negative child count and tolerates a missing one`() {
        val docs = listOf(
            "a" to mapOf<String, Any?>("name" to "A", "childCount" to -3L),
            "b" to mapOf<String, Any?>("name" to "B"),
        )

        val items = FirestoreArchiveParser.items(docs, ArchivePath.ROOT)

        assertEquals(listOf(0, null), items.map { it.childCount })
    }

    @Test
    fun `items skips catalog nodes without a name`() {
        val docs = listOf(
            "a" to mapOf<String, Any?>("name" to ""),
            "b" to mapOf<String, Any?>("title" to "Only a title"),
            "c" to mapOf<String, Any?>("name" to "C"),
        )

        assertEquals(listOf("c"), FirestoreArchiveParser.items(docs, ArchivePath.ROOT).map { it.path.components.last() })
    }

    @Test
    fun `items under a university lists faculties`() {
        val university = ArchivePath.ROOT.appending("itu", "İTÜ")

        val items = FirestoreArchiveParser.items(listOf("itu-muh" to mapOf("name" to "Mühendislik")), university)

        assertEquals(ArchiveLevel.FACULTY, items.single().level)
        assertEquals(listOf("İTÜ", "Mühendislik"), items.single().path.displayNames)
    }

    // --- items: exams ---

    @Test
    fun `items under a course builds exams from title, kind and file count, newest first`() {
        val docs = listOf(
            "e1" to exam("2002 VİZE", "PDF", files = listOf(file("pdfs/a.pdf"))),
            "e2" to exam("2019 FİNAL", "JPG", files = listOf(file("imagess/1.jpg"), file("imagess/2.jpg"))),
        )

        val items = FirestoreArchiveParser.items(docs, course)

        assertEquals(listOf("2019 FİNAL", "2002 VİZE"), items.map { it.name })
        assertEquals(ExamSummary(ExamKind.IMAGES, 2), items.first().exam)
        assertEquals(ExamSummary(ExamKind.PDF, 1), items.last().exam)
        assertTrue(items.all { it.level == ArchiveLevel.EXAM && it.childCount == null })
    }

    @Test
    fun `items skips exams without files, without usable paths or with an unknown kind`() {
        val docs = listOf(
            "noFiles" to exam("A", "PDF", files = emptyList()),
            "emptyPaths" to exam("B", "PDF", files = listOf(file(""), mapOf("order" to 1L))),
            "badKind" to exam("C", "DOCX", files = listOf(file("x"))),
            "noTitle" to mapOf("name" to "D", "kind" to "PDF", "files" to listOf(file("x"))),
            "ok" to exam("E", "PDF", files = listOf(file("x"))),
        )

        assertEquals(listOf("E"), FirestoreArchiveParser.items(docs, course).map { it.name })
    }

    @Test
    fun `items below the exam level is empty`() {
        val examPath = course.appending("e1", "2019 FİNAL")

        assertTrue(FirestoreArchiveParser.items(listOf("x" to mapOf("title" to "x")), examPath).isEmpty())
    }

    // --- document ---

    @Test
    fun `document orders files by order, stable for equal or missing orders, and drops empty paths`() {
        val data = exam(
            "2019 FİNAL",
            "JPG",
            files = listOf(
                file("imagess/c.jpg", order = 2L),
                file("imagess/a.jpg", order = 0L),
                file(""),
                file("imagess/b1.jpg", order = 1L),
                file("imagess/b2.jpg", order = 1L),
            ),
        )
        val path = course.appending("e1", "2019 FİNAL")

        val document = FirestoreArchiveParser.document(data, path, BUCKET)

        requireNotNull(document)
        assertEquals(ExamKind.IMAGES, document.kind)
        assertEquals(path, document.path)
        assertEquals(
            listOf("a", "b1", "b2", "c").map { url("imagess%2F$it.jpg") },
            document.fileUrls,
        )
    }

    @Test
    fun `document falls back to the array index when order is absent`() {
        val data = exam("V", "PDF", files = listOf(file("pdfs/second.pdf", order = 1L), file("pdfs/first.pdf")))

        val document = FirestoreArchiveParser.document(data, course.appending("e", "V"), BUCKET)

        // "first" has no order → its index 1 ties with "second"'s order 1; the earlier entry wins.
        assertEquals(listOf(url("pdfs%2Fsecond.pdf"), url("pdfs%2Ffirst.pdf")), document?.fileUrls)
    }

    @Test
    fun `document is null without a kind or without files`() {
        val path = course.appending("e", "V")

        assertNull(FirestoreArchiveParser.document(mapOf("files" to listOf(file("x"))), path, BUCKET))
        assertNull(FirestoreArchiveParser.document(exam("V", "PDF", files = emptyList()), path, BUCKET))
        assertNull(FirestoreArchiveParser.document(exam("V", "PDF", files = listOf(file("x"))), path, bucket = ""))
    }

    private fun exam(title: String, kind: String, files: List<Map<String, Any?>>): Map<String, Any?> =
        mapOf("title" to title, "courseId" to "itu-muh-blg-blg101", "kind" to kind, "status" to "published", "files" to files)

    private fun file(storagePath: String, order: Long? = null): Map<String, Any?> =
        buildMap {
            put("storagePath", storagePath)
            if (order != null) put("order", order)
        }

    private fun url(encodedPath: String) =
        "https://firebasestorage.googleapis.com/v0/b/$BUCKET/o/$encodedPath?alt=media"

    private companion object {
        const val BUCKET = "vifi-831a8.appspot.com"
    }
}
