package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `CatalogNameTests` = server `CATALOG_NAME_CASES` (the recent-exam flag case belongs to SQS-06). */
class CatalogNameTest {

    private val cases = listOf(
        "Güç elektroniği devreleri" to "GÜÇ ELEKTRONİĞİ DEVRELERİ",
        "Eskişehir Teknik Üniversitesi" to "ESKİŞEHİR TEKNİK ÜNİVERSİTESİ",
        "ılık işlem" to "ILIK İŞLEM",
        "  Fen   Edebiyat Fakültesi " to "FEN EDEBİYAT FAKÜLTESİ",
        "MATEMATİK BÖLÜMÜ" to "MATEMATİK BÖLÜMÜ",
        "Fizik 2" to "FİZİK 2",
    )

    @Test
    fun `each shared case matches the server and is idempotent`() {
        for ((input, expected) in cases) {
            assertEquals(input, expected, CatalogName.display(input))
            assertEquals(expected, expected, CatalogName.display(expected))
        }
    }
}
