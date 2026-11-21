package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.SubjectMappingService
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.ExamConversionError
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectTarget
import java.text.Collator
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * An in-memory subject mapping for the mock flavor, previews and demos (iOS `MockSubjectMappingService`): five
 * subjects, two of which ("Algoritmalar" / "Algoritma", one university each) look like the same course. Unlike
 * [FakeSubjectMappingService] (scripted answers for unit tests), actions update the list the way the server would.
 */
class InMemorySubjectMappingService(
    subjects: List<AdminSubject> = SAMPLES,
    private val latency: Duration = 200.milliseconds,
) : SubjectMappingService {

    private var current: List<AdminSubject> = subjects
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("tr-TR"))

    override suspend fun subjects(): List<AdminSubject> {
        pause()
        return current.sortedWith { lhs, rhs -> collator.compare(lhs.name, rhs.name) }
    }

    override suspend fun setCourseSubject(courseId: String, target: SubjectTarget) {
        pause()
        val moving = current.flatMap { it.courses }.firstOrNull { it.id == courseId } ?: throw ExamConversionError.NotFound
        val targetId = when (target) {
            is SubjectTarget.Existing -> {
                if (current.none { it.id == target.id }) throw ExamConversionError.NotFound
                target.id
            }
            is SubjectTarget.New -> {
                val id = target.name.lowercase(Locale.forLanguageTag("tr-TR")).replace(' ', '-')
                if (current.none { it.id == id }) {
                    current = current + AdminSubject(id = id, name = target.name, aliases = listOf(id), courses = emptyList())
                }
                id
            }
        }
        current = current.map { subject ->
            val courses = subject.courses.filter { it.id != courseId }
            subject.copy(courses = if (subject.id == targetId) courses + moving else courses)
        }
    }

    override suspend fun renameSubject(id: String, name: String) {
        pause()
        if (current.none { it.id == id }) throw ExamConversionError.NotFound
        current = current.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun mergeSubject(id: String, into: String) {
        pause()
        val from = current.firstOrNull { it.id == id } ?: throw ExamConversionError.NotFound
        val target = current.firstOrNull { it.id == into } ?: throw ExamConversionError.NotFound
        val merged = target.copy(aliases = target.aliases + from.aliases, courses = target.courses + from.courses)
        current = current.filter { it.id != id }.map { if (it.id == into) merged else it }
    }

    private suspend fun pause() {
        if (latency > Duration.ZERO) delay(latency)
    }

    companion object {
        val SAMPLES: List<AdminSubject> = listOf(
            AdminSubject(
                "algoritmalar", "Algoritmalar", listOf("algoritmalar"),
                listOf(course("c-algo-bogazici", "Algoritmalar", "bogazici", "Boğaziçi Üniversitesi", 24)),
            ),
            AdminSubject(
                "algoritma", "Algoritma", listOf("algoritma"),
                listOf(course("c-algo-ege", "Algoritma", "ege", "Ege Üniversitesi", 12)),
            ),
            AdminSubject(
                "fizik-1", "Fizik 1", listOf("fizik-1", "fizik-i"),
                listOf(
                    course("c-fizik-bogazici", "Fizik I", "bogazici", "Boğaziçi Üniversitesi", 30),
                    course("c-fizik-ege", "Fizik 1", "ege", "Ege Üniversitesi", 18),
                ),
            ),
            AdminSubject(
                "fizik-2", "Fizik 2", listOf("fizik-2"),
                listOf(course("c-fizik2-ege", "Fizik 2", "ege", "Ege Üniversitesi", 9)),
            ),
            AdminSubject(
                "veri-yapilari", "Veri Yapıları", listOf("veri-yapilari"),
                listOf(
                    course("c-veri-ege", "Veri Yapıları", "ege", "Ege Üniversitesi", 15),
                    course("c-veri-itu", "Veri Yapıları ve Algoritmalar", "itu", "İstanbul Teknik Üniversitesi", 6),
                ),
            ),
        )

        private fun course(id: String, name: String, universityId: String, university: String, questions: Int) =
            SubjectCourse(
                id = id,
                name = name,
                universityId = universityId,
                universityName = university,
                facultyName = "Mühendislik Fakültesi",
                departmentName = "Bilgisayar Mühendisliği",
                questionCount = questions,
            )
    }
}
