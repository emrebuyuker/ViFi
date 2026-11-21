package com.salticusteam.vifi.core.util

import com.salticusteam.vifi.core.model.ExamNameCatalog
import com.salticusteam.vifi.core.model.ExamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Same cases as iOS `ExamTitleParserTests` and the server's `conversionSchema.test.mjs` (36 type/year, 32 list-title
 * conversions, 20 list-title validations): the server and the apps must split and convert titles identically.
 */
class ExamTitleParserTest {

    private val cases: List<Triple<String, ExamType?, Int?>> = listOf(
        Triple("2019 FİNAL", ExamType.FINAL, 2019),
        Triple("2018 VİZE", ExamType.VIZE, 2018),
        Triple("2017-2018 VİZE", ExamType.VIZE, 2018),
        Triple("2019 2020 FİNAL", ExamType.FINAL, 2020),
        Triple("2020 BÜTÜNLEME", ExamType.BUTUNLEME, 2020),
        Triple("2021 MAZERET", ExamType.MAZERET, 2021),
        Triple("2019 KISA SINAV 2", ExamType.KISA_SINAV, 2019),
        Triple("KISA SINAV 3", ExamType.KISA_SINAV, null),
        Triple("2019 ARASINAV", ExamType.ARA_SINAV, 2019),
        Triple("2022 ARA SINAV", ExamType.ARA_SINAV, 2022),
        Triple("2019 YAZ OKULU FİNAL", ExamType.FINAL, 2019),
        Triple("2019 VİZE YAZ OKULU", ExamType.VIZE, 2019),
        Triple("2023 YAZ OKULU", ExamType.YAZ_OKULU, 2023),
        Triple("FİNAL", ExamType.FINAL, null),
        Triple("DERS NOTU", null, null),
        Triple("2019 TEK DERS", ExamType.TEK_DERS, 2019),
        Triple("2020 EK SINAV", ExamType.EK_SINAV, 2020),
        Triple("2020 YEDEK SINAV", null, 2020),
        Triple("2021 YAZ OKULU VİZE", ExamType.VIZE, 2021),
        Triple("2021 YAZ OKULU ARA SINAV", ExamType.ARA_SINAV, 2021),
        Triple("2021 YAZ OKULU FİNAL", ExamType.FINAL, 2021),
        Triple("QUIZ", ExamType.KISA_SINAV, null),
        Triple("2018 QUIZ 2", ExamType.KISA_SINAV, 2018),
        Triple("2022 MUAFİYET SINAVI", ExamType.MUAFIYET, 2022),
        Triple("2022 SEVİYE TESPİT SINAVI", ExamType.SEVIYE_TESPIT, 2022),
        Triple("2022 UYGULAMA SINAVI", ExamType.UYGULAMA, 2022),
        Triple("2022 UYGULAMA", null, 2022),
        Triple("2018 LABORATUVAR", ExamType.LABORATUVAR, 2018),
        Triple("2018 ÖDEV", ExamType.ODEV, 2018),
        Triple("2018 PROJE", ExamType.PROJE, 2018),
        Triple("2018 ÇALIŞMA SORULARI", ExamType.CALISMA_SORULARI, 2018),
        Triple("2018 ÇÖZÜMLÜ SORULAR", ExamType.COZUMLU_SORULAR, 2018),
        Triple("FİNAL ÇALIŞMA SORULARI", ExamType.CALISMA_SORULARI, null),
        Triple("2019 VİZE ÇÖZÜMLÜ SORULAR", ExamType.COZUMLU_SORULAR, 2019),
        Triple("2019 VİZE CEVAPLI", ExamType.VIZE, 2019),
        Triple("", null, null),
    )

    /** (title, expected list title, entry id). */
    private val canonicalCases: List<Triple<String, String?, String?>> = listOf(
        Triple("2019 KISA SINAV 2", "2019 KISA SINAV 2", "kisa-sinav-2"),
        Triple("2017-2018 VİZE", "2018 VİZE", "vize"),
        Triple("2019 2020 FİNAL", "2020 FİNAL", "final"),
        Triple("2019 FİZİK LAB", "2019 LABORATUVAR", "laboratuvar"),
        Triple("2018 FİZİK LABORATUAR 2", "2018 LABORATUVAR 2", "laboratuvar"),
        Triple("2019 VİZE YAZ OKULU", "2019 YAZ OKULU VİZE", "yaz-okulu-vize"),
        Triple("2021 YAZ OKULU ARA SINAV", "2021 YAZ OKULU ARA SINAV", "yaz-okulu-ara-sinav"),
        Triple("2021 YAZ OKULU FİNAL", "2021 YAZ OKULU FİNAL", "yaz-okulu-final"),
        Triple("2019 YAZ OKULU BÜTÜNLEME", "2019 BÜTÜNLEME", "butunleme"),
        Triple("QUIZ 3", "KISA SINAV 3", "kisa-sinav-3"),
        Triple("QUIZ 2", "KISA SINAV 2", "kisa-sinav-2"),
        Triple("QUIZ 5", "KISA SINAV 5", "kisa-sinav"),
        Triple("2018 KISA SINAV 12", "2018 KISA SINAV 12", "kisa-sinav"),
        Triple("QUIZ", "KISA SINAV", "kisa-sinav"),
        Triple("2018 FİNAL ÇALIŞMA SORULARI", "2018 ÇALIŞMA SORULARI", "calisma-sorulari"),
        Triple("2019 FİNAL", "2019 FİNAL", "final"),
        Triple("FİNAL", "FİNAL", "final"),
        Triple("MATEMATİK 2 FİNAL", "FİNAL", "final"),
        Triple("2019 VİZE 2", "2019 VİZE 2", "vize"),
        Triple("2019 VİZE CEVAPLI", "2019 VİZE", "vize"),
        Triple("2019 ARASINAV", "2019 ARA SINAV", "ara-sinav"),
        Triple("2020 EK SINAVI", "2020 EK SINAV", "ek-sinav"),
        Triple("2018 ÖDEVLER", "2018 ÖDEV", "odev"),
        Triple("2019 PROJESİ", "2019 PROJE", "proje"),
        Triple("2022 MUAFİYET SINAVI", "2022 MUAFİYET SINAVI", "muafiyet"),
        Triple("2023 YAZ OKULU", null, null),
        Triple("DERS NOTU", null, null),
        Triple("SİSTEM", null, null),
        Triple("POSTER", null, null),
        Triple("2019 TEK", null, null),
        Triple("2022 UYGULAMA", null, null),
        Triple("", null, null),
    )

    private val acceptedListTitles = listOf(
        "2019 VİZE" to "2019 VİZE",
        "VİZE" to "VİZE",
        "KISA SINAV 2" to "KISA SINAV 2",
        "KISA SINAV 5" to "KISA SINAV 5",
        "2018 YAZ OKULU FİNAL" to "2018 YAZ OKULU FİNAL",
        "ÇÖZÜMLÜ SORULAR 99" to "ÇÖZÜMLÜ SORULAR 99",
        "  2019   FİNAL " to "2019 FİNAL",
    )

    private val rejectedListTitles = listOf(
        "2017-2018 VİZE", "2019 VIZE", "vize", "VİZE 0", "VİZE 100", "VİZE 05", "KISA SINAV 2 3", "DERS NOTU",
        "19 VİZE", "1899 VİZE", "2019 FİZİK VİZE", "VİZE 2019", "",
    )

    @Test
    fun `parse splits titles into type and year like the server`() {
        assertEquals(36, cases.size)
        for ((title, type, year) in cases) {
            assertEquals("\"$title\"", ExamTitleParser.Result(type, year), ExamTitleParser.parse(title))
        }
    }

    @Test
    fun `canonicalTitle converts archive titles to list titles like the server (K28)`() {
        assertEquals(32, canonicalCases.size)
        for ((title, expected, entryId) in canonicalCases) {
            val result = ExamTitleParser.canonicalTitle(title)
            assertEquals("\"$title\"", expected, result?.title)
            assertEquals("\"$title\"", entryId, result?.entryId)
            if (result != null) {
                assertEquals(result.title, ExamListTitle.parse(result.title)?.title)
                assertEquals(result.examType, ExamTitleParser.parse(result.title).examType)
            }
        }
    }

    @Test
    fun `list titles are validated like the server`() {
        assertEquals(20, acceptedListTitles.size + rejectedListTitles.size)
        for ((title, normalized) in acceptedListTitles) {
            assertEquals(title, normalized, ExamListTitle.parse(title)?.title)
        }
        for (title in rejectedListTitles) {
            assertNull(title, ExamListTitle.parse(title))
        }
        assertEquals(ExamListTitle(2018, "kisa-sinav-1", null), ExamListTitle.parse("2018 KISA SINAV 1"))
        assertEquals(ExamListTitle(null, "kisa-sinav", 7), ExamListTitle.parse("KISA SINAV 7"))
        assertEquals("2019 YAZ OKULU VİZE 2", ExamListTitle(2019, "yaz-okulu-vize", 2).title)
    }

    @Test
    fun `every list name parses to its entry's exam type and is its own list title`() {
        for (entry in ExamNameCatalog.entries) {
            assertEquals(entry.name, ExamTitleParser.Result(entry.examType, 2024), ExamTitleParser.parse("2024 ${entry.name}"))
            assertEquals(
                entry.name,
                ExamTitleParser.CanonicalTitle("2024 ${entry.name}", entry.id, entry.examType),
                ExamTitleParser.canonicalTitle("2024 ${entry.name}"),
            )
            assertEquals(entry.name, ExamListTitle.parse(entry.name)?.title)
        }
    }

    @Test
    fun `list ids, names and order are the owner's and the server's (K27, K29)`() {
        val ids = ExamNameCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf(
                listOf("vize", "VİZE"), listOf("ara-sinav", "ARA SINAV"), listOf("final", "FİNAL"),
                listOf("butunleme", "BÜTÜNLEME"), listOf("mazeret", "MAZERET"), listOf("tek-ders", "TEK DERS"),
                listOf("ek-sinav", "EK SINAV"), listOf("yaz-okulu-vize", "YAZ OKULU VİZE"),
                listOf("yaz-okulu-ara-sinav", "YAZ OKULU ARA SINAV"), listOf("yaz-okulu-final", "YAZ OKULU FİNAL"),
                listOf("kisa-sinav", "KISA SINAV"), listOf("kisa-sinav-1", "KISA SINAV 1"),
                listOf("kisa-sinav-2", "KISA SINAV 2"), listOf("kisa-sinav-3", "KISA SINAV 3"),
                listOf("muafiyet", "MUAFİYET SINAVI"), listOf("seviye-tespit", "SEVİYE TESPİT SINAVI"),
                listOf("uygulama", "UYGULAMA SINAVI"), listOf("laboratuvar", "LABORATUVAR"), listOf("odev", "ÖDEV"),
                listOf("proje", "PROJE"), listOf("calisma-sorulari", "ÇALIŞMA SORULARI"),
                listOf("cozumlu-sorular", "ÇÖZÜMLÜ SORULAR"),
            ),
            ExamNameCatalog.entries.map { listOf(it.id, it.name) },
        )
    }

    @Test
    fun `study materials are not practice exam types (K27) and the type order is the server's`() {
        assertEquals(
            listOf(ExamType.LABORATUVAR, ExamType.ODEV, ExamType.PROJE, ExamType.CALISMA_SORULARI, ExamType.COZUMLU_SORULAR),
            ExamType.entries.filterNot { it.isPracticeEligible },
        )
        assertEquals(
            listOf(
                "vize", "final", "butunleme", "mazeret", "kisa-sinav", "yaz-okulu", "ara-sinav",
                "tek-ders", "ek-sinav", "muafiyet", "seviye-tespit", "uygulama",
                "laboratuvar", "odev", "proje", "calisma-sorulari", "cozumlu-sorular",
            ),
            ExamType.entries.map { it.raw },
        )
    }
}
