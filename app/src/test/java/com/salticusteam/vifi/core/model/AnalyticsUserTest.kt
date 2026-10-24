package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.salticusteam.vifi.core.util.Slug
import org.junit.Test

class AnalyticsUserTest {

    private val profile = UserProfile(
        name = "Ayşe", surname = "Yılmaz",
        university = "İTÜ", universityId = "itu", universityIsCustom = false,
        faculty = "Mühendislik", facultyId = "muh", facultyIsCustom = false,
        department = "Bilgisayar", departmentId = "blg", departmentIsCustom = false,
        gender = Gender.FEMALE, phoneNumber = "+905321234567",
    )

    @Test
    fun `from when profile present copies canonical ids and gender raw`() {
        val user = AnalyticsUser.from(id = "u1", profile = profile, isAdmin = false)

        assertEquals("itu", user.universityId)
        assertEquals("muh", user.facultyId)
        assertEquals("blg", user.departmentId)
        assertEquals("female", user.gender)
    }

    @Test
    fun `properties when profile missing contains only is_admin`() {
        val user = AnalyticsUser.from(id = "u1", profile = null, isAdmin = true)

        assertEquals(mapOf("is_admin" to "true"), user.properties)
    }

    @Test
    fun `properties when profile present contains every known value`() {
        val props = AnalyticsUser.from("u1", profile, isAdmin = false).properties

        assertEquals(
            mapOf(
                "is_admin" to "false", "university_id" to "itu", "faculty_id" to "muh",
                "department_id" to "blg", "gender" to "female",
            ),
            props,
        )
        assertTrue(props.keys.all { it in AnalyticsUser.PROPERTY_NAMES })
    }

    @Test
    fun `properties when value exceeds 36 characters clamps it`() {
        val long = "a".repeat(50)
        val user = AnalyticsUser("u1", universityId = long, facultyId = null, departmentId = null, gender = null, isAdmin = false)

        assertEquals(36, user.properties.getValue("university_id").length)
        assertFalse("faculty_id" in user.properties)
    }

    @Test
    fun `properties when names were typed by the user carry their canonical topic-safe slugs`() {
        val typed = profile.copy(
            universityId = Slug.make("Özel Işık Üniversitesi"), universityIsCustom = true,
            facultyId = Slug.make("Fen-Edebiyat Fakültesi"), facultyIsCustom = true,
        )

        val props = AnalyticsUser.from("u1", typed, isAdmin = false).properties

        assertEquals("ozel-isik-universitesi", props["university_id"])
        assertEquals("fen-edebiyat-fakultesi", props["faculty_id"])
        listOf("university_id", "faculty_id", "department_id").forEach {
            assertTrue(props.getValue(it).matches(Regex("[a-z0-9-]+")))
        }
    }
}
