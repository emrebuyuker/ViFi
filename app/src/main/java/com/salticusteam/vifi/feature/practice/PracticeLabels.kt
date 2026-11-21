package com.salticusteam.vifi.feature.practice

import androidx.annotation.StringRes
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeScope

/** The Turkish name of an exam type in the setup list ("Ara Sınav", iOS `ExamType.practiceTitle`). */
@StringRes
fun ExamType.practiceTitleRes(): Int = when (this) {
    ExamType.VIZE -> R.string.practice_type_vize
    ExamType.ARA_SINAV -> R.string.practice_type_ara_sinav
    ExamType.FINAL -> R.string.practice_type_final
    ExamType.BUTUNLEME -> R.string.practice_type_butunleme
    ExamType.MAZERET -> R.string.practice_type_mazeret
    ExamType.KISA_SINAV -> R.string.practice_type_kisa_sinav
    ExamType.TEK_DERS -> R.string.practice_type_tek_ders
    ExamType.EK_SINAV -> R.string.practice_type_ek_sinav
    ExamType.MUAFIYET -> R.string.practice_type_muafiyet
    ExamType.SEVIYE_TESPIT -> R.string.practice_type_seviye_tespit
    ExamType.UYGULAMA -> R.string.practice_type_uygulama
    ExamType.YAZ_OKULU -> R.string.practice_type_yaz_okulu
    ExamType.LABORATUVAR -> R.string.practice_type_laboratuvar
    ExamType.ODEV -> R.string.practice_type_odev
    ExamType.PROJE -> R.string.practice_type_proje
    ExamType.CALISMA_SORULARI -> R.string.practice_type_calisma_sorulari
    ExamType.COZUMLU_SORULAR -> R.string.practice_type_cozumlu_sorular
}

/** The setup's name of a pool scope ("Türkiye geneli"). */
@StringRes
fun PracticeScope.titleRes(): Int = when (this) {
    PracticeScope.COURSE -> R.string.practice_scope_course
    PracticeScope.UNIVERSITY -> R.string.practice_scope_university
    PracticeScope.TURKEY -> R.string.practice_scope_turkey
}
