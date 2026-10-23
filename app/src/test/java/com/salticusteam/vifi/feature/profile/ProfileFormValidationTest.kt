package com.salticusteam.vifi.feature.profile

import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileFormValidationTest {

    private val complete = ProfileFormUiState.initial(
        phoneNumber = FakeProfileService.SAMPLE_PROFILE.phoneNumber,
        existing = FakeProfileService.SAMPLE_PROFILE,
    )

    @Test
    fun `build when every field is valid returns the profile`() {
        assertEquals(FakeProfileService.SAMPLE_PROFILE, ProfileFormValidation.build(complete))
    }

    @Test
    fun `build trims and collapses spaces in names`() {
        val profile = ProfileFormValidation.build(complete.copy(name = "  Ayşe   Nur ", surname = " Yılmaz "))

        assertEquals("Ayşe Nur", profile?.name)
        assertEquals("Yılmaz", profile?.surname)
    }

    @Test
    fun `build when a required field is missing returns null`() {
        assertNull(ProfileFormValidation.build(complete.copy(name = "   ")))
        assertNull(ProfileFormValidation.build(complete.copy(gender = null)))
        assertNull(ProfileFormValidation.build(complete.copy(faculty = CatalogSelection.EMPTY)))
        assertNull(ProfileFormValidation.build(complete.copy(phoneNumber = null)))
    }

    @Test
    fun `build when a value has a character the database forbids returns null`() {
        listOf("Ay.şe", "A/B", "a#b", "a\$b", "a[b", "a\nb").forEach { name ->
            assertNull(name, ProfileFormValidation.build(complete.copy(name = name)))
        }
    }

    @Test
    fun `build when a name is too long returns null`() {
        assertNull(ProfileFormValidation.build(complete.copy(name = "a".repeat(61))))
        assertEquals(60, ProfileFormValidation.build(complete.copy(name = "a".repeat(60)))?.name?.length)
    }

    @Test
    fun `build derives a slug id for a custom selection`() {
        val profile = ProfileFormValidation.build(
            complete.copy(department = CatalogSelection.custom("Yapay Zeka Mühendisliği"), gender = Gender.MALE),
        )

        assertEquals("Yapay Zeka Mühendisliği", profile?.department)
        assertEquals("yapay-zeka-muhendisligi", profile?.departmentId)
        assertEquals(true, profile?.departmentIsCustom)
    }

    @Test
    fun `validSelection when a custom value has no slug-able character falls back to the unknown id`() {
        // Slug is lockstep with iOS: a value with no ASCII letter or digit gets the "unknown" id.
        assertEquals("unknown", ProfileFormValidation.validSelection(CatalogSelection.custom("!!!"))?.id)
    }
}
