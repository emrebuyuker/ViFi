package com.salticusteam.vifi.core.data.conversion

import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectNameSimilarity
import com.salticusteam.vifi.core.model.SubjectTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `SubjectMappingTests` › `SubjectNameSimilarity` + `SubjectMappingCalls` (the view-model suite belongs to ADX-06). */
class SubjectMappingTest {

    private fun course(id: String, university: String = "ege", questions: Int = 5) = SubjectCourse(
        id = id, name = "Ders", universityId = university, universityName = university.replaceFirstChar { it.uppercase() },
        facultyName = "Mühendislik", departmentName = "Bilgisayar", questionCount = questions,
    )

    private fun subject(id: String, name: String? = null, courses: List<SubjectCourse> = emptyList()) =
        AdminSubject(id = id, name = name ?: id.replaceFirstChar { it.uppercase() }, aliases = listOf(id), courses = courses)

    // --- SubjectNameSimilarity ---

    @Test
    fun `names are reduced to letters, folded Turkish letters and a trailing number`() {
        assertEquals(SubjectNameSimilarity.Key("veriyapilari", ""), SubjectNameSimilarity.key("Veri Yapıları"))
        assertEquals(SubjectNameSimilarity.Key("fizik", "2"), SubjectNameSimilarity.key("FİZİK II"))
        assertEquals(SubjectNameSimilarity.Key("calculus", "1"), SubjectNameSimilarity.key("Calculus 1"))
        assertEquals(SubjectNameSimilarity.Key("i", ""), SubjectNameSimilarity.key("I"))
        assertEquals(SubjectNameSimilarity.Key("matematik", "1"), SubjectNameSimilarity.key("Matematik-I"))
    }

    @Test
    fun `equal, prefix and near-equal names are close`() {
        listOf(
            "Algoritmalar" to "Algoritma", "Fizik 1" to "Fizik I", "Diferansiyel Denklemler" to "Diferansiyel Denklem",
            "Veri Yapıları" to "VERİ YAPILARI", "Lineer Cebir" to "Lineer Cebr",
        ).forEach { (lhs, rhs) -> assertTrue("$lhs ~ $rhs", SubjectNameSimilarity.areClose(lhs, rhs)) }
    }

    @Test
    fun `different numbers, unrelated and very short names are not close`() {
        listOf(
            "Fizik 1" to "Fizik 2", "Fizik I" to "Fizik II", "Algoritmalar" to "Veri Yapıları", "Kimya" to "Kimyo", "" to "Fizik",
        ).forEach { (lhs, rhs) -> assertFalse("$lhs !~ $rhs", SubjectNameSimilarity.areClose(lhs, rhs)) }
    }

    @Test
    fun `only single-university subjects are flagged, with the biggest close subject`() {
        val single = subject("algoritma", courses = listOf(course("a", university = "ege")))
        val small = subject("algoritmalar", courses = listOf(course("b", university = "itu")))
        val big = subject("algoritmalar-2", name = "Algoritmalar", courses = listOf(course("c", university = "odtu"), course("d", university = "ege")))
        val unrelated = subject("fizik", courses = listOf(course("e", university = "ege")))

        val matches = SubjectNameSimilarity.possibleMatches(listOf(single, small, big, unrelated))

        assertEquals("algoritmalar-2", matches["algoritma"])
        assertEquals("algoritmalar-2", matches["algoritmalar"])
        assertNull("two universities: already a country-wide subject", matches["algoritmalar-2"])
        assertNull(matches["fizik"])
    }

    @Test
    fun `a subject without courses is never flagged`() {
        val empty = subject("algoritma")
        val other = subject("algoritmalar", courses = listOf(course("a")))
        val matches = SubjectNameSimilarity.possibleMatches(listOf(empty, other))
        assertNull(matches["algoritma"])
        assertEquals("algoritma", matches["algoritmalar"])
    }

    // --- SubjectMappingCalls ---

    @Test
    fun `callable names and payloads match the server contract`() {
        assertEquals("listSubjects", SubjectMappingCalls.Name.LIST)
        assertEquals("setCourseSubject", SubjectMappingCalls.Name.SET_COURSE_SUBJECT)
        assertEquals("renameSubject", SubjectMappingCalls.Name.RENAME)
        assertEquals("mergeSubjects", SubjectMappingCalls.Name.MERGE)
        assertEquals(mapOf("courseId" to "c", "subjectId" to "s"), SubjectMappingCalls.setCourseSubject("c", SubjectTarget.Existing("s")))
        assertEquals(mapOf("courseId" to "c", "newSubjectName" to "N"), SubjectMappingCalls.setCourseSubject("c", SubjectTarget.New("N")))
        assertEquals(mapOf("subjectId" to "s", "name" to "N"), SubjectMappingCalls.rename("s", "N"))
        assertEquals(mapOf("fromSubjectId" to "a", "intoSubjectId" to "b"), SubjectMappingCalls.merge("a", "b"))
    }

    @Test
    fun `the list response is parsed, entries without an id are dropped`() {
        val data = mapOf(
            "subjects" to listOf(
                mapOf(
                    "id" to "algoritmalar", "name" to "Algoritmalar", "aliases" to listOf("algoritmalar"), "courseCount" to 1, "questionCount" to 7,
                    "courses" to listOf(
                        mapOf(
                            "id" to "c1", "name" to "Algoritmalar", "universityId" to "ege", "universityName" to "Ege Üniversitesi",
                            "facultyName" to "Mühendislik", "departmentName" to "Bilgisayar", "questionCount" to 7,
                        ),
                        mapOf("name" to "no id"),
                    ),
                ),
                mapOf("name" to "no id either"),
                mapOf("id" to "bos"),
            ),
        )

        val subjects = SubjectMappingCalls.subjects(data)

        assertEquals(listOf("algoritmalar", "bos"), subjects.map { it.id })
        assertEquals(listOf("c1"), subjects[0].courses.map { it.id })
        assertEquals(7, subjects[0].questionCount)
        assertEquals("Mühendislik · Bilgisayar", subjects[0].courses[0].place)
        assertEquals("bos", subjects[1].name)
        assertTrue(SubjectMappingCalls.subjects(null).isEmpty())
    }
}
