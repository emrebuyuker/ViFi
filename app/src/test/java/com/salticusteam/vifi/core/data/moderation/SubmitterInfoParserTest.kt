package com.salticusteam.vifi.core.data.moderation

import com.salticusteam.vifi.core.model.SubmitterInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS `SubmitterProfileTests` › parser cases (the lookup/caching suite belongs to ADM-03). */
class SubmitterInfoParserTest {

    private val info = SubmitterInfo("Ayşe Demir", "Boğaziçi Üniversitesi", "Mühendislik Fakültesi", "Bilgisayar Mühendisliği")

    @Test
    fun `the parser keeps name and affiliation and ignores the phone number and gender`() {
        val value = mapOf(
            "name" to "Ayşe", "surname" to "Demir", "university" to "Boğaziçi Üniversitesi",
            "faculty" to "Mühendislik Fakültesi", "department" to "Bilgisayar Mühendisliği",
            "gender" to "female", "phoneNumber" to "+900000000000",
        )

        val parsed = SubmitterInfoParser.info(value)

        assertEquals(info, parsed)
        assertFalse(parsed.toString().contains("+90"))
    }

    @Test
    fun `the parser rejects a profile without a name and tolerates missing affiliation`() {
        assertNull(SubmitterInfoParser.info(null))
        assertNull(SubmitterInfoParser.info(mapOf("surname" to "Demir")))
        assertEquals(SubmitterInfo("Ayşe Demir", "", "", ""), SubmitterInfoParser.info(mapOf("name" to "Ayşe", "surname" to "Demir")))
    }
}
