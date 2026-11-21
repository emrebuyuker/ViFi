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

    // Added in 4.2.0 — one name of the 22-name list (K27/K29) with the exam type it parses to.
    /** [id] and [name] are the shared contract (server `EXAM_NAME_LIST`, iOS `ExamNameCatalog.Entry`). */
    data class Entry(val id: String, val name: String, val examType: ExamType)

    /**
     * The owner's and the server's 22-name list, in order (iOS `ExamNameCatalog.entries`). `ExamTitleParser` builds
     * list titles from it. "Sınav Ekle" still shows [names] until SMP-01 switches the picker to this list.
     */
    val entries: List<Entry> = listOf(
        Entry("vize", "VİZE", ExamType.VIZE),
        Entry("ara-sinav", "ARA SINAV", ExamType.ARA_SINAV),
        Entry("final", "FİNAL", ExamType.FINAL),
        Entry("butunleme", "BÜTÜNLEME", ExamType.BUTUNLEME),
        Entry("mazeret", "MAZERET", ExamType.MAZERET),
        Entry("tek-ders", "TEK DERS", ExamType.TEK_DERS),
        Entry("ek-sinav", "EK SINAV", ExamType.EK_SINAV),
        Entry("yaz-okulu-vize", "YAZ OKULU VİZE", ExamType.VIZE),
        Entry("yaz-okulu-ara-sinav", "YAZ OKULU ARA SINAV", ExamType.ARA_SINAV),
        Entry("yaz-okulu-final", "YAZ OKULU FİNAL", ExamType.FINAL),
        Entry("kisa-sinav", "KISA SINAV", ExamType.KISA_SINAV),
        Entry("kisa-sinav-1", "KISA SINAV 1", ExamType.KISA_SINAV),
        Entry("kisa-sinav-2", "KISA SINAV 2", ExamType.KISA_SINAV),
        Entry("kisa-sinav-3", "KISA SINAV 3", ExamType.KISA_SINAV),
        Entry("muafiyet", "MUAFİYET SINAVI", ExamType.MUAFIYET),
        Entry("seviye-tespit", "SEVİYE TESPİT SINAVI", ExamType.SEVIYE_TESPIT),
        Entry("uygulama", "UYGULAMA SINAVI", ExamType.UYGULAMA),
        Entry("laboratuvar", "LABORATUVAR", ExamType.LABORATUVAR),
        Entry("odev", "ÖDEV", ExamType.ODEV),
        Entry("proje", "PROJE", ExamType.PROJE),
        Entry("calisma-sorulari", "ÇALIŞMA SORULARI", ExamType.CALISMA_SORULARI),
        Entry("cozumlu-sorular", "ÇÖZÜMLÜ SORULAR", ExamType.COZUMLU_SORULAR),
    )

    /** The list entry with [id], or `null`. */
    fun entry(id: String): Entry? = entries.firstOrNull { it.id == id }
}
