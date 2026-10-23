package com.salticusteam.vifi.core.data.profile

import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UserProfileParserTest {

    private val profile = FakeProfileService.SAMPLE_PROFILE

    private val raw: Map<String, Any?> = mapOf(
        "name" to "Ayşe",
        "surname" to "Yılmaz",
        "university" to "Boğaziçi Üniversitesi",
        "universityId" to "bogazici-universitesi",
        "universityIsCustom" to false,
        "faculty" to "Mühendislik Fakültesi",
        "facultyId" to "muhendislik-fakultesi",
        "facultyIsCustom" to false,
        "department" to "Bilgisayar Mühendisliği",
        "departmentId" to "bilgisayar-muhendisligi",
        "departmentIsCustom" to false,
        "gender" to "female",
        "phoneNumber" to "+905321234567",
        "updatedAt" to 1_700_000_000_000L,
    )

    @Test
    fun `profile when snapshot complete parses and ignores updatedAt`() {
        assertEquals(profile, UserProfileParser.profile(raw))
    }

    @Test
    fun `profile when a required field missing returns null`() {
        val required = listOf(
            "name", "surname", "university", "universityId", "faculty", "facultyId",
            "department", "departmentId", "gender", "phoneNumber",
        )
        for (key in required) {
            assertNull("missing $key should not parse", UserProfileParser.profile(raw - key))
        }
    }

    @Test
    fun `profile when isCustom flag missing or malformed defaults to false`() {
        assertFalse(requireNotNull(UserProfileParser.profile(raw - "universityIsCustom")).universityIsCustom)
        assertFalse(requireNotNull(UserProfileParser.profile(raw + ("facultyIsCustom" to "yes"))).facultyIsCustom)
    }

    @Test
    fun `profile when field empty, gender unknown or value not an object returns null`() {
        assertNull(UserProfileParser.profile(raw + ("name" to "")))
        assertNull(UserProfileParser.profile(raw + ("gender" to "other")))
        assertNull(UserProfileParser.profile(raw + ("surname" to 42L)))
        assertNull(UserProfileParser.profile("not a profile"))
        assertNull(UserProfileParser.profile(null))
    }

    @Test
    fun `fields writes exactly the 13 rule keys without updatedAt and round-trips`() {
        val fields = UserProfileParser.fields(profile)

        assertEquals(UserProfileParser.FIELD_KEYS.toSet(), fields.keys)
        assertEquals(13, fields.size)
        assertEquals("female", fields["gender"])
        assertEquals(false, fields["universityIsCustom"])
        assertNull(fields[UserProfileParser.UPDATED_AT_KEY])
        assertEquals(profile, UserProfileParser.profile(fields))
    }
}
