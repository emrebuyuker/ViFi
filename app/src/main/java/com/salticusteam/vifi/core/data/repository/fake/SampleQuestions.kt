package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.firebase.ExamQuestionParser
import com.salticusteam.vifi.core.model.ExamQuestion

/**
 * The sample structured questions (4.2.0) for the mock flavor, previews and tests — the iOS
 * `Preview Content/SampleExams/sample-questions.json`, verbatim, as raw documents parsed through the real
 * [ExamQuestionParser] (so the mock exercises the live parsing). The third question's figure path
 * (`sample-page-1.jpg`) is answered by the mock build's `SampleFilesInterceptor`.
 */
object SampleQuestions {

    /** The sample exam's id in the sample documents (iOS `mock-structured-exam`). */
    const val EXAM_ID = "mock-structured-exam"

    /** The raw `questions` documents, without ids or exam id (both are filled per exam). */
    val documents: List<Map<String, Any?>> = listOf(
        mapOf(
            "schemaVersion" to 1L,
            "order" to 1L,
            "label" to "1",
            "text" to "\$\\displaystyle \\int_0^1 x^2 \\, dx\$ integralini hesaplayınız.",
            "speech" to "Sıfırdan bire x kare dx integralini hesaplayınız.",
            "choices" to emptyList<Any>(),
            "figures" to emptyList<Any>(),
            "answer" to mapOf("text" to "\$\\frac{1}{3}\$", "speech" to "üçte bir"),
            "solution" to listOf(
                "Ters türev: \$\\frac{x^3}{3}\$.",
                "Sınırlar yerine konur: \$\\frac{1}{3} - 0 = \\frac{1}{3}\$.",
            ),
            "topics" to listOf("belirli-integral"),
            "difficulty" to 1L,
        ),
        mapOf(
            "schemaVersion" to 1L,
            "order" to 2L,
            "label" to "2",
            "text" to "\$f(x) = x^3 - 3x\$ fonksiyonunun yerel maksimum noktası hangisidir?",
            "speech" to "f x eşittir x küp eksi 3 x fonksiyonunun yerel maksimum noktası hangisidir?",
            "choices" to listOf(
                mapOf("label" to "A", "text" to "\$x = -1\$"),
                mapOf("label" to "B", "text" to "\$x = 0\$"),
                mapOf("label" to "C", "text" to "\$x = 1\$"),
                mapOf("label" to "D", "text" to "\$x = 3\$"),
            ),
            "figures" to emptyList<Any>(),
            "answer" to mapOf("text" to "\$x = -1\$", "choice" to "A", "speech" to "x eşittir eksi bir"),
            "solution" to listOf(
                "\$f'(x) = 3x^2 - 3 = 0 \\Rightarrow x = \\pm 1\$.",
                "\$f''(-1) = -6 < 0\$ olduğundan \$x = -1\$ yerel maksimumdur.",
            ),
            "topics" to listOf("turev", "ekstremum"),
            "difficulty" to 2L,
        ),
        mapOf(
            "schemaVersion" to 1L,
            "order" to 3L,
            "label" to "3a",
            "text" to "Şekildeki devrede \$R_1 = 2\\,\\Omega\$, \$R_2 = 4\\,\\Omega\$ ve \$V = 12\\,V\$ ise toplam akımı bulunuz.",
            "speech" to "Şekildeki devrede R bir 2 ohm, R iki 4 ohm ve V 12 volt ise toplam akımı bulunuz.",
            "choices" to emptyList<Any>(),
            "figures" to listOf(mapOf("storagePath" to "sample-page-1.jpg", "width" to 1200L, "height" to 800L)),
            "answer" to mapOf("text" to "\$I = 2\\,A\$", "speech" to "I eşittir 2 amper"),
            "solution" to listOf(
                "Seri bağlı: \$R = R_1 + R_2 = 6\\,\\Omega\$.",
                "\$I = V / R = 12 / 6 = 2\\,A\$.",
            ),
            "topics" to listOf("ohm-yasasi"),
            "difficulty" to 1L,
        ),
    )

    /** The sample set for any exam id, with ids `<examId>-<nnn>` (iOS `MockExamQuestionReader.sampleQuestions`). */
    fun questions(examId: String = EXAM_ID): List<ExamQuestion> = ExamQuestionParser.questions(
        documents.map { doc ->
            val order = (doc["order"] as Long).toInt()
            "$examId-${"%03d".format(order)}" to (doc + ("examId" to examId))
        },
        examId,
    )
}
