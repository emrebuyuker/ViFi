package com.salticusteam.vifi.feature.addexam

import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.core.model.RewardsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AddExamValidationTest {

    private val complete = AddExamUiState(
        ownerId = "uid",
        university = CatalogSelection.stored("Boğaziçi Üniversitesi", "bogazici-universitesi", false),
        faculty = CatalogSelection.stored("Mühendislik Fakültesi", "muhendislik-fakultesi", false),
        department = CatalogSelection.stored("Bilgisayar Mühendisliği", "bilgisayar-muhendisligi", false),
        lesson = CatalogSelection.custom("  BLG   101 "),
        examName = CatalogSelection.stored("VİZE", "vize", false),
        year = 2023,
        files = listOf(SubmissionFile(byteArrayOf(1), 1)),
        termsAccepted = true,
    )

    @Test
    fun `validName trims and collapses spaces`() {
        assertEquals("BLG 101", AddExamValidation.validName("  BLG   101 "))
    }

    @Test
    fun `validName when empty or too long returns null`() {
        assertNull(AddExamValidation.validName("   "))
        assertNull(AddExamValidation.validName("a".repeat(121)))
        assertNotNull(AddExamValidation.validName("a".repeat(120)))
    }

    @Test
    fun `build when complete returns the normalized submission`() {
        val submission = AddExamValidation.build(complete)

        assertEquals("BLG 101", submission?.lesson)
        assertEquals(2023, submission?.year)
        assertEquals(ExamKind.IMAGES, submission?.kind)
    }

    @Test
    fun `build when owner, a field or files are missing returns null`() {
        assertNull(AddExamValidation.build(complete.copy(ownerId = "")))
        assertNull(AddExamValidation.build(complete.copy(lesson = CatalogSelection.EMPTY)))
        assertNull(AddExamValidation.build(complete.copy(examName = CatalogSelection.EMPTY)))
        assertNull(AddExamValidation.build(complete.copy(files = emptyList())))
    }

    @Test
    fun `build when a pdf carries two files returns null`() {
        val twoFiles = listOf(SubmissionFile(byteArrayOf(1), 1), SubmissionFile(byteArrayOf(2), 2))
        assertNull(AddExamValidation.build(complete.copy(kind = ExamKind.PDF, files = twoFiles)))
    }

    @Test
    fun `canSubmit is false while preparing files, working or before the terms are accepted`() {
        assertEquals(true, complete.canSubmit)
        assertEquals(false, complete.copy(isPreparingFiles = true).canSubmit)
        assertEquals(false, complete.copy(isWorking = true).canSubmit)
        assertEquals(false, complete.copy(termsAccepted = false).canSubmit)
    }

    @Test
    fun `coinNote prefers the tier amount then the flat amount then generic`() {
        val config = RewardsConfig(examUploadCoins = 10, tiers = mapOf(SubmissionTier.NEW_COURSE to 120))

        assertEquals(
            CoinNote.Estimated(120, SubmissionTier.NEW_COURSE),
            complete.copy(rewards = config, estimatedTier = SubmissionTier.NEW_COURSE).coinNote,
        )
        // An unconfigured tier falls back to the flat amount through RewardsConfig.coins.
        assertEquals(
            CoinNote.Estimated(10, SubmissionTier.DUPLICATE),
            complete.copy(rewards = config, estimatedTier = SubmissionTier.DUPLICATE).coinNote,
        )
        assertEquals(CoinNote.Flat(10), complete.copy(rewards = config).coinNote)
        assertEquals(CoinNote.Generic, complete.copy(estimatedTier = SubmissionTier.NEW_EXAM).coinNote)
        assertEquals(CoinNote.Generic, complete.coinNote)
    }

    @Test
    fun `location is null until every level is chosen`() {
        assertNull(complete.copy(lesson = CatalogSelection.EMPTY).location)
        assertEquals("2023 VİZE", complete.location?.examText)
    }
}
