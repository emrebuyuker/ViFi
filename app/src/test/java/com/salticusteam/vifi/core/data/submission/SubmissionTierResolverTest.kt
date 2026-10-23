package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.SubmissionLocation
import com.salticusteam.vifi.core.model.SubmissionTier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/** Against FakeArchiveRepository's sample tree: İTÜ > Mühendislik > Bilgisayar Mühendisliği > BLG 101 > 2023 Vize. */
class SubmissionTierResolverTest {

    private val repository = FakeArchiveRepository()
    private val sut = SubmissionTierResolver(repository)

    private fun location(
        university: String = "İSTANBUL TEKNİK ÜNİVERSİTESİ",
        faculty: String = "Mühendislik",
        department: String = "Bilgisayar Mühendisliği Bölümü",
        lesson: String = "blg 101",
        examName: String = "VİZE",
        year: Int = 2023,
    ) = SubmissionLocation(university, faculty, department, lesson, examName, year)

    @Test
    fun `placement when university missing is newUniversity`() = runTest {
        assertEquals(
            ResolvedPlacement(SubmissionTier.NEW_UNIVERSITY),
            sut.placement(location(university = "Ege Üniversitesi")),
        )
    }

    @Test
    fun `placement when neither faculty nor department exists is newFaculty`() = runTest {
        assertEquals(
            ResolvedPlacement(SubmissionTier.NEW_FACULTY, universityId = "itu"),
            sut.placement(location(faculty = "Tıp Fakültesi", department = "Tıp")),
        )
    }

    @Test
    fun `placement when faculty exists but department missing is newDepartment`() = runTest {
        assertEquals(
            ResolvedPlacement(SubmissionTier.NEW_DEPARTMENT, universityId = "itu", facultyId = "muh"),
            sut.placement(location(department = "Elektrik Mühendisliği")),
        )
    }

    @Test
    fun `placement finds the department under a differently named faculty`() = runTest {
        val placement = sut.placement(location(faculty = "Bilgisayar ve Bilişim Fakültesi"))

        assertEquals(SubmissionTier.DUPLICATE, placement.tier)
        assertEquals("muh", placement.facultyId)
        assertEquals("blg", placement.departmentId)
    }

    @Test
    fun `placement when lesson missing is newCourse`() = runTest {
        assertEquals(
            ResolvedPlacement(SubmissionTier.NEW_COURSE, "itu", "muh", "blg"),
            sut.placement(location(lesson = "BLG 202")),
        )
    }

    @Test
    fun `placement when exam year differs is newExam`() = runTest {
        assertEquals(
            ResolvedPlacement(SubmissionTier.NEW_EXAM, "itu", "muh", "blg", "blg101"),
            sut.placement(location(year = 2019)),
        )
    }

    @Test
    fun `placement when same exam exists is duplicate with its id`() = runTest {
        assertEquals(
            ResolvedPlacement(SubmissionTier.DUPLICATE, "itu", "muh", "blg", "blg101", examId = "2023-vize"),
            sut.placement(location()),
        )
        assertEquals(SubmissionTier.DUPLICATE, sut.tier(location()))
    }

    @Test
    fun `placement when archive read fails propagates the error`() = runTest {
        repository.error = ArchiveError.Offline
        try {
            sut.tier(location())
            fail("expected ArchiveError")
        } catch (e: ArchiveError) {
            assertSame(ArchiveError.Offline, e)
        }
    }
}
