package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.model.SubmissionTier
import org.junit.Assert.assertEquals
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
                CatalogNodeWrite(course, "course", "Anatomi I", department, 1),
                CatalogNodeWrite(department, "department", "Tıp", faculty, 1),
                CatalogNodeWrite(faculty, "faculty", "Tıp Fakültesi", university, 1),
                CatalogNodeWrite(university, "university", "Ege Üniversitesi", "", 1),
            ),
            plan.nodesToCreate,
        )
        assertTrue(plan.childCountIncrements.isEmpty())
        assertEquals("${course}__2023-vize", plan.examId)
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

        assertEquals(listOf(CatalogNodeWrite("d__anatomi-i", "course", "Anatomi I", "d", 1)), plan.nodesToCreate)
        assertEquals(listOf("d"), plan.childCountIncrements)
    }

    @Test
    fun `make when new exam bumps only the course`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.NEW_EXAM, "u", "f", "d", "c"))

        assertTrue(plan.nodesToCreate.isEmpty())
        assertEquals(listOf("c"), plan.childCountIncrements)
        assertEquals("c__2023-vize", plan.examId)
    }

    @Test
    fun `make when duplicate publishes nothing`() {
        val plan = ArchivePublishPlan.make(record, ResolvedPlacement(SubmissionTier.DUPLICATE, "u", "f", "d", "c", "e"))

        assertTrue(plan.nodesToCreate.isEmpty())
        assertTrue(plan.childCountIncrements.isEmpty())
        assertNull(plan.examId)
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
