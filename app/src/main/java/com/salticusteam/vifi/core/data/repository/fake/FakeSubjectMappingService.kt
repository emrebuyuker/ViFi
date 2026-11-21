package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectTarget

/**
 * In-memory [SubjectMappingService] (iOS `MockSubjectMappingService` / `StubSubjectMappingService`): scripted
 * [subjectList] (or queued [responses]), recorded [calls]. Actions do not change the list; tests script the next answer.
 */
class FakeSubjectMappingService(
    var subjectList: List<AdminSubject> = SAMPLES,
) : SubjectMappingService {

    sealed interface Call {
        data class SetCourseSubject(val courseId: String, val target: SubjectTarget) : Call
        data class Rename(val id: String, val name: String) : Call
        data class Merge(val id: String, val into: String) : Call
    }

    /** Answers served first in, first out before [subjectList]. */
    val responses = ArrayDeque<List<AdminSubject>>()

    /** Fails every call when non-null. */
    var error: ExamConversionError? = null

    /** Fails only the actions, not the listing. */
    var actionError: ExamConversionError? = null

    val calls = mutableListOf<Call>()

    var listCount = 0
        private set

    override suspend fun subjects(): List<AdminSubject> {
        listCount++
        error?.let { throw it }
        return responses.removeFirstOrNull() ?: subjectList
    }

    override suspend fun setCourseSubject(courseId: String, target: SubjectTarget) = act(Call.SetCourseSubject(courseId, target))

    override suspend fun renameSubject(id: String, name: String) = act(Call.Rename(id, name))

    override suspend fun mergeSubject(id: String, into: String) = act(Call.Merge(id, into))

    private fun act(call: Call) {
        calls += call
        (error ?: actionError)?.let { throw it }
    }

    companion object {
        val SAMPLES = listOf(
            AdminSubject(
                id = "topoloji-1",
                name = "Topoloji 1",
                aliases = listOf("topoloji-1"),
                courses = listOf(
                    SubjectCourse(
                        id = FakePoolIndexReader.SAMPLE_COURSE_ID,
                        name = "TOPOLOJİ 1",
                        universityId = "kocaeli-universitesi",
                        universityName = "KOCAELİ ÜNİVERSİTESİ",
                        facultyName = "MÜHENDİSLİK FAKÜLTESİ",
                        departmentName = "MATEMATİK BÖLÜMÜ",
                        questionCount = 3,
                    ),
                ),
            ),
            AdminSubject(
                id = "topoloji",
                name = "Topoloji I",
                aliases = emptyList(),
                courses = listOf(
                    SubjectCourse(
                        id = "ege-fen-matematik-topoloji",
                        name = "TOPOLOJİ",
                        universityId = "ege-universitesi",
                        universityName = "EGE ÜNİVERSİTESİ",
                        facultyName = "FEN FAKÜLTESİ",
                        departmentName = "MATEMATİK",
                        questionCount = 0,
                    ),
                ),
            ),
        )
    }
}
