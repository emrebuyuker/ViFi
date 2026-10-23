package com.salticusteam.vifi.core.model

// Added in 2.5 — the fixed exam names offered by "Sınav Ekle" (iOS ExamNameCatalog); free text is not allowed.
/**
 * Matches the exam types used across the archive (e.g. "2019 FİNAL"). Each carries a canonical id (for later
 * notification segments) and the uppercase display the archive stores; the ids must stay in lockstep with iOS.
 */
object ExamNameCatalog {
    val names: List<CatalogItem> = listOf(
        CatalogItem(id = "vize", name = "VİZE"),
        CatalogItem(id = "final", name = "FİNAL"),
        CatalogItem(id = "butunleme", name = "BÜTÜNLEME"),
        CatalogItem(id = "mazeret", name = "MAZERET"),
        CatalogItem(id = "kisa-sinav", name = "KISA SINAV"),
        CatalogItem(id = "yaz-okulu", name = "YAZ OKULU"),
        CatalogItem(id = "ara-sinav", name = "ARA SINAV"),
    )
}
