package com.salticusteam.vifi.core.data.moderation

import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.core.model.SubmitterInfo
import java.util.Collections
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/** K3: only the five display children of `users/<uid>/profile` are read — never the phone number or gender. */
class FirebaseSubmitterProfileReaderTest {

    private val stored = mapOf(
        "name" to "Ayşe",
        "surname" to "Demir",
        "university" to "Boğaziçi Üniversitesi",
        "faculty" to "Mühendislik Fakültesi",
        "department" to "Bilgisayar Mühendisliği",
        "gender" to "female",
        "phoneNumber" to "+900000000000",
    )
    private val requested = Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    private fun sut(profile: Map<String, Any>?) = FirebaseSubmitterProfileReader(
        readField = { uid, field ->
            requested += uid to field
            profile?.get(field)
        },
        timeout = 5.seconds,
    )

    @Test
    fun `reads exactly the five display fields and builds the submitter`() = runTest {
        val info = sut(stored).submitter("u1")

        assertEquals(
            SubmitterInfo("Ayşe Demir", "Boğaziçi Üniversitesi", "Mühendislik Fakültesi", "Bilgisayar Mühendisliği"),
            info,
        )
        assertEquals(
            setOf("name", "surname", "university", "faculty", "department"),
            requested.map { it.second }.toSet(),
        )
        assertEquals(5, requested.size)
        assertEquals(setOf("u1"), requested.map { it.first }.toSet())
    }

    @Test
    fun `a missing profile is null`() = runTest {
        assertNull(sut(null).submitter("gone"))
    }

    @Test
    fun `a profile without a surname is null and sparse affiliation is tolerated`() = runTest {
        assertNull(sut(mapOf("name" to "Ayşe")).submitter("u1"))
        assertEquals(
            SubmitterInfo("Ayşe Demir", "", "", ""),
            sut(mapOf("name" to "Ayşe", "surname" to "Demir")).submitter("u1"),
        )
    }

    @Test
    fun `a rejected read surfaces the profile error`() = runTest {
        val reader = FirebaseSubmitterProfileReader(readField = { _, _ -> throw ProfileError.PermissionDenied }, 5.seconds)
        try {
            reader.submitter("u1")
            fail("expected a failure")
        } catch (e: ProfileError) {
            assertSame(ProfileError.PermissionDenied, e)
        }
    }

    @Test
    fun `a read that never answers fails offline`() = runTest {
        val reader = FirebaseSubmitterProfileReader(readField = { _, _ -> awaitCancellation() }, 1.seconds)
        try {
            reader.submitter("u1")
            fail("expected a failure")
        } catch (e: ProfileError) {
            assertSame(ProfileError.Offline, e)
        }
    }
}
