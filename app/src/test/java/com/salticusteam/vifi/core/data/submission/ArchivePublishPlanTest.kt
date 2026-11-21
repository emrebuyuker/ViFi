package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.model.SubmissionTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS ArchivePublishPlanTests parity: id composition and childCount bookkeeping per tier. */
class ArchivePublishPlanTest {

    private val record = ExamSubmissionRecord(
        id = "sub-1",
        ownerId = "u1",
        university = "Ege Üniversitesi",
        faculty = "Tıp Fakültesi",
        department = "Tıp",
        lesson = "Anatomi I",
        exam = "2023 VİZE",
        kind = ExamKind.PDF,
        status = "pending",
        createdAt = null,
        files = emptyList(),
    )

    @Test
    fun `make when new university creates four nodes and the exam id`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_UNIVERSITY))

        val university = "ege-universitesi"
        val faculty = "${university}__tip-fakultesi"
        val department = "${faculty}__tip"
        val course = "${department}__anatomi-i"
        assertEquals(
            listOf(
                CatalogNodeWrite(course, "course", "ANATOMİ I", department, 1),
                CatalogNodeWrite(department, "department", "TIP", faculty, 1),
                CatalogNodeWrite(faculty, "faculty", "TIP FAKÜLTESİ", university, 1),
                CatalogNodeWrite(university, "university", "EGE ÜNİVERSİTESİ", "", 1),
            ),
            plan.nodesToCreate,
        )
        assertTrue(plan.childCountIncrements.isEmpty())
        assertEquals("${course}__2023-vize", plan.examId)
        assertFalse(plan.isDuplicate)
    }

    // ADM-04 (iOS `newUniversityChain`): a new node's name is the archive's Turkish upper case; the slug is unchanged.
    @Test
    fun `make names new nodes in Turkish upper case and keeps the slug of the typed name`() {
        val typed = record.copy(university = "  boğaziçi   üniversitesi ")
        val plan = ArchivePublishPlan.make(typed, ResolvedPlacement(SubmissionTier.NEW_UNIVERSITY))

        val university = plan.nodesToCreate.single { it.level == "university" }
        assertEquals("BOĞAZİÇİ ÜNİVERSİTESİ", university.name)
        assertEquals("bogazici-universitesi", university.id)
        assertEquals("bogazici-universitesi", university.fields["slug"])
    }

    @Test
    fun `make when new faculty bumps the existing university`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_FACULTY, universityId = "ege"))

        assertEquals(listOf("course", "department", "faculty"), plan.nodesToCreate.map { it.level })
        assertEquals(listOf("ege"), plan.childCountIncrements)
        assertEquals("ege__tip-fakultesi", plan.facultyId)
    }

    @Test
    fun `make when new department bumps the existing faculty`() {
        val plan = ArchivePublishPlan.make(
            record,
            ResolvedPlacement(SubmissionTier.NEW_DEPARTMENT, universityId = "ege", facultyId = "ege__tip"),
        )

        assertEquals(listOf("course", "department"), plan.nodesToCreate.map { it.level })
        assertEquals(listOf("ege__tip"), plan.childCountIncrements)
    }

    @Test
    fun `make when new course bumps the existing department`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_COURSE, "u", "f", "d"))

        assertEquals(listOf(CatalogNodeWrite("d__anatomi-i", "course", "ANATOMİ I", "d", 1)), plan.nodesToCreate)
        assertEquals(listOf("d"), plan.childCountIncrements)
    }

    @Test
    fun `make when new exam bumps only the course`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_EXAM, "u", "f", "d", "c"))

        assertTrue(plan.nodesToCreate.isEmpty())
        assertEquals(listOf("c"), plan.childCountIncrements)
        assertEquals("c__2023-vize", plan.examId)
    }

    // iOS `duplicatePublishesNothing` (K22): no node or count changes, and the plan targets the existing exam.
    @Test
    fun `make when duplicate creates no nodes and targets the existing exam`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.DUPLICATE, "u", "f", "d", "c", "e"))

        assertTrue(plan.nodesToCreate.isEmpty())
        assertTrue(plan.childCountIncrements.isEmpty())
        assertEquals("e", plan.examId)
        assertTrue(plan.isDuplicate)
    }

    @Test
    fun `publishPlanDocument for a new exam has the server's create shape`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_COURSE, "u", "f", "d"))
        val files = listOf(SubmissionFileRef("pdfs/b.pdf", 2), SubmissionFileRef("pdfs/a.pdf", 1))

        val document = plan.publishPlanDocument(record, files)

        assertEquals(
            mapOf(
                "examId" to "d__anatomi-i__2023-vize",
                "universityId" to "u",
                "facultyId" to "f",
                "departmentId" to "d",
                "courseId" to "d__anatomi-i",
                "nodesToCreate" to listOf(
                    mapOf(
                        "id" to "d__anatomi-i",
                        "level" to "course",
                        "name" to "ANATOMİ I",
                        "parentId" to "d",
                        "status" to "published",
                        "childCount" to 1,
                        "slug" to "anatomi-i",
                        "source" to "submission",
                    ),
                ),
                "childCountIncrements" to listOf("d"),
                "exam" to plan.examDocument(record, files),
                "mode" to "create",
            ),
            document,
        )
        @Suppress("UNCHECKED_CAST")
        val exam = document?.get("exam") as Map<String, Any>
        assertEquals(
            listOf(mapOf("storagePath" to "pdfs/a.pdf", "order" to 1), mapOf("storagePath" to "pdfs/b.pdf", "order" to 2)),
            exam["files"],
        )
        assertEquals("d__anatomi-i", exam["courseId"])
    }

    @Test
    fun `publishPlanDocument for a duplicate replaces the existing exam with empty node lists`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.DUPLICATE, "u", "f", "d", "c", "c__e"))
        val files = listOf(SubmissionFileRef("pdfs/a.pdf", 1))

        val document = plan.publishPlanDocument(record, files)

        assertEquals("c__e", document?.get("examId"))
        assertEquals("replace", document?.get("mode"))
        assertEquals(emptyList<Any>(), document?.get("nodesToCreate"))
        assertEquals(emptyList<Any>(), document?.get("childCountIncrements"))
        assertEquals("c", document?.get("courseId"))
        assertEquals(plan.examDocument(record, files), document?.get("exam"))
        assertEquals(9, document?.size)
    }

    @Test
    fun `publishPlanDocument without an exam id is null`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_EXAM, "u", "f", "d", "c"))
            .copy(examId = null)

        assertNull(plan.publishPlanDocument(record, emptyList()))
    }

    @Test
    fun `node fields match the archive shape`() {
        val fields = CatalogNodeWrite("ege", "university", "Ege Üniversitesi", "", 1).fields

        assertEquals(
            mapOf(
                "level" to "university",
                "name" to "Ege Üniversitesi",
                "parentId" to "",
                "status" to "published",
                "childCount" to 1,
                "slug" to "ege-universitesi",
                "source" to "submission",
            ),
            fields,
        )
    }

    @Test
    fun `examDocument carries the names ids and sorted files`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_EXAM, "u", "f", "d", "c"))
        val document = plan.examDocument(
            record,
            listOf(SubmissionFileRef("pdfs/b.pdf", 2), SubmissionFileRef("pdfs/a.pdf", 1)),
        )

        assertEquals("2023 VİZE", document["title"])
        assertEquals("c", document["courseId"])
        assertEquals("Anatomi I", document["courseName"])
        assertEquals("PDF", document["kind"])
        assertEquals("published", document["status"])
        assertEquals(false, document["isLocked"])
        assertEquals(0, document["coinCost"])
        assertEquals(
            listOf(mapOf("storagePath" to "pdfs/a.pdf", "order" to 1), mapOf("storagePath" to "pdfs/b.pdf", "order" to 2)),
            document["files"],
        )
        assertEquals(17, document.size)
    }
}
