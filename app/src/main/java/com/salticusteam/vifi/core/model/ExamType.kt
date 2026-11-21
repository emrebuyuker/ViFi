package com.salticusteam.vifi.core.model

import androidx.annotation.Keep

// Added in 4.2.0 — canonical exam types (`EXAM_TYPES` in the shared schema.js, iOS `ExamType`), in the server's order.
/**
 * Parsed from archive titles by `ExamTitleParser` and mapped from every [ExamNameCatalog] entry; also the
 * `examType` of a question and a practice pool. [raw] is the backend value.
 */
// Kept: travels in the type-safe `ViFiRoute.PracticeSession` (its serializer must survive R8).
@Keep
enum class ExamType(val raw: String) {
    VIZE("vize"),
    FINAL("final"),
    BUTUNLEME("butunleme"),
    MAZERET("mazeret"),
    KISA_SINAV("kisa-sinav"),
    YAZ_OKULU("yaz-okulu"),
    ARA_SINAV("ara-sinav"),
    TEK_DERS("tek-ders"),
    EK_SINAV("ek-sinav"),
    MUAFIYET("muafiyet"),
    SEVIYE_TESPIT("seviye-tespit"),
    UYGULAMA("uygulama"),
    LABORATUVAR("laboratuvar"),
    ODEV("odev"),
    PROJE("proje"),
    CALISMA_SORULARI("calisma-sorulari"),
    COZUMLU_SORULAR("cozumlu-sorular");

    /**
     * Whether the type is a real exam for "Sınava gir". Study materials (lab, homework, project, study and solved
     * questions) stay in the archive but get no practice pools (owner decision K27; `PRACTICE_EXAM_TYPES`).
     */
    val isPracticeEligible: Boolean
        get() = this !in MATERIALS

    companion object {
        private val MATERIALS = setOf(LABORATUVAR, ODEV, PROJE, CALISMA_SORULARI, COZUMLU_SORULAR)

        fun fromRaw(raw: String?): ExamType? = entries.firstOrNull { it.raw == raw }

        /** The practice types in the order the setup lists them (the "Sınav Ekle" name list's order; iOS). */
        val PRACTICE_DISPLAY_ORDER: List<ExamType> = listOf(
            VIZE, ARA_SINAV, FINAL, BUTUNLEME, MAZERET, KISA_SINAV, TEK_DERS, EK_SINAV,
            MUAFIYET, SEVIYE_TESPIT, UYGULAMA, YAZ_OKULU,
        )
    }
}
