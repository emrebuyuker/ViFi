package com.salticusteam.vifi.core.data.catalog

import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSelectionTest {

    @Test
    fun `catalog keeps the listed id and is not custom`() {
        val selection = CatalogSelection.catalog(CatalogItem("ege-universitesi", "Ege Üniversitesi"))
        assertEquals(CatalogSelection("Ege Üniversitesi", "ege-universitesi", isCustom = false), selection)
    }

    @Test
    fun `custom trims the text and slugs it`() {
        val selection = CatalogSelection.custom("  Yeni Üniversite  ")
        assertEquals(CatalogSelection("Yeni Üniversite", "yeni-universite", isCustom = true), selection)
    }

    @Test
    fun `empty is empty and stored round-trips`() {
        assertTrue(CatalogSelection.EMPTY.isEmpty)
        val stored = CatalogSelection.stored("X", "x", isCustom = true)
        assertFalse(stored.isEmpty)
        assertEquals(CatalogSelection("X", "x", true), stored)
    }
}
