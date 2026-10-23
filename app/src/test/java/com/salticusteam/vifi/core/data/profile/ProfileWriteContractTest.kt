package com.salticusteam.vifi.core.data.profile

import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileWriteContractTest {

    private val profile = FakeProfileService.SAMPLE_PROFILE
    private val phone = profile.phoneNumber

    @Test
    fun `violations when profile valid is empty`() {
        assertTrue(ProfileWriteContract.violations(profile, phone).isEmpty())
    }

    @Test
    fun `violations when text breaks length, forbidden characters or is blank`() {
        assertEquals(listOf("name"), ProfileWriteContract.violations(profile.copy(name = "a".repeat(61)), phone))
        assertTrue(ProfileWriteContract.violations(profile.copy(name = "a".repeat(60)), phone).isEmpty())
        assertEquals(listOf("surname"), ProfileWriteContract.violations(profile.copy(surname = "A.B"), phone))
        assertEquals(listOf("university"), ProfileWriteContract.violations(profile.copy(university = "   "), phone))
        assertEquals(listOf("faculty"), ProfileWriteContract.violations(profile.copy(faculty = "a/b"), phone))
        assertEquals(listOf("department"), ProfileWriteContract.violations(profile.copy(department = "a".repeat(121)), phone))
    }

    @Test
    fun `violations when id is not a slug or too long`() {
        assertEquals(listOf("universityId"), ProfileWriteContract.violations(profile.copy(universityId = "Ege"), phone))
        assertEquals(listOf("facultyId"), ProfileWriteContract.violations(profile.copy(facultyId = ""), phone))
        assertEquals(listOf("departmentId"), ProfileWriteContract.violations(profile.copy(departmentId = "a".repeat(141)), phone))
    }

    @Test
    fun `violations when phone differs from auth token or is not +90`() {
        assertEquals(listOf("phoneNumber"), ProfileWriteContract.violations(profile, "+905329999999"))
        assertEquals(listOf("phoneNumber"), ProfileWriteContract.violations(profile, null))
        val foreign = profile.copy(phoneNumber = "+15551234567")
        assertEquals(listOf("phoneNumber"), ProfileWriteContract.violations(foreign, foreign.phoneNumber))
    }
}
