package com.salticusteam.vifi.core.data.catalog

import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.util.Slug
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversityCatalogTest {

    private val catalog = UniversityCatalog(
        universities = listOf(
            CatalogItem("bogazici-universitesi", "Boğaziçi Üniversitesi"),
            CatalogItem("ege-universitesi", "Ege Üniversitesi"),
            CatalogItem("istanbul-teknik-universitesi", "İstanbul Teknik Üniversitesi"),
        ),
        faculties = listOf(
            CatalogItem("muhendislik-fakultesi", "Mühendislik Fakültesi"),
            CatalogItem("tip-fakultesi", "Tıp Fakültesi"),
        ),
        departments = listOf(
            CatalogItem("bilgisayar-muhendisligi", "Bilgisayar Mühendisliği"),
            CatalogItem("isletme", "İşletme"),
        ),
    )

    // --- filtering ---

    @Test
    fun `filter when query blank returns whole list`() {
        assertEquals(3, catalog.universities("   ").size)
        assertEquals(2, catalog.faculties("").size)
        assertEquals(2, catalog.departments("").size)
    }

    @Test
    fun `filter when query differs in case and diacritics matches Turkish-insensitively`() {
        assertEquals(listOf("bogazici-universitesi"), catalog.universities("bogazici").map { it.id })
        assertEquals(listOf("ege-universitesi"), catalog.universities("EGE").map { it.id })
        assertEquals(listOf("isletme"), catalog.departments("isletme").map { it.id })
        assertEquals(listOf("istanbul-teknik-universitesi"), catalog.universities("İSTANBUL").map { it.id })
        assertEquals(listOf("muhendislik-fakultesi"), catalog.faculties("muh").map { it.id })
    }

    @Test
    fun `filter when query uses dotless i does not fold it like iOS`() {
        assertEquals(listOf("tip-fakultesi"), catalog.faculties("tıp").map { it.id })
        assertTrue(catalog.faculties("tip").isEmpty())
    }

    @Test
    fun `filter when no match returns empty list`() {
        assertTrue(catalog.universities("zzz").isEmpty())
    }

    // --- sorting ---

    @Test
    fun `compareNames orders Turkish letters, ignores case and compares digits numerically`() {
        val sorted = listOf("Çukurova", "Zonguldak", "cumhuriyet", "Ege", "Ankara 10", "Ankara 9", "İnönü", "Iğdır")
            .sortedWith(UniversityCatalog::compareNames)
        assertEquals(
            listOf("Ankara 9", "Ankara 10", "cumhuriyet", "Çukurova", "Ege", "Iğdır", "İnönü", "Zonguldak"),
            sorted,
        )
    }

    // --- bundled assets ---

    @Test
    fun `load reads the bundled assets sorted, with slug ids in lockstep with names`() {
        val bundled = UniversityCatalog.load(open = { File(ASSETS_DIR, it).inputStream() })

        assertTrue(bundled.universities.isNotEmpty())
        assertTrue(bundled.faculties.isNotEmpty())
        assertTrue(bundled.departments.isNotEmpty())
        assertTrue(bundled.universities.any { it.id == "sakarya-universitesi" })
        for (list in listOf(bundled.universities, bundled.faculties, bundled.departments)) {
            assertEquals(list.sortedWith { a, b -> UniversityCatalog.compareNames(a.name, b.name) }, list)
            list.forEach { assertEquals(it.name, Slug.make(it.name), it.id) }
        }
    }

    @Test
    fun `load when an asset is missing returns empty list and reports it`() {
        val failed = mutableListOf<String>()
        val result = UniversityCatalog.load(
            open = { if (it == UniversityCatalog.FACULTIES_ASSET) throw FileNotFoundException(it) else File(ASSETS_DIR, it).inputStream() },
            onError = { asset, _ -> failed += asset },
        )

        assertTrue(result.faculties.isEmpty())
        assertTrue(result.universities.isNotEmpty())
        assertEquals(listOf(UniversityCatalog.FACULTIES_ASSET), failed)
    }

    @Test
    fun `load when an asset is malformed returns empty list`() {
        val result = UniversityCatalog.load(open = { "not json".byteInputStream() })
        assertEquals(UniversityCatalog.EMPTY, result)
    }

    private companion object {
        // Gradle runs unit tests from the module directory.
        val ASSETS_DIR = File("src/main/assets")
    }
}
