package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun `fromInput when pasted in common formats returns same number`() {
        listOf("532 123 45 67", "0532-123-45-67", "+90 532 123 45 67", "0090 532 123 45 67", "90 5321234567", "(0532) 123.45.67")
            .forEach { input ->
                assertEquals(input, "+905321234567", PhoneNumber.fromInput(input)?.e164)
            }
    }

    @Test
    fun `fromNational when not ten digits or not starting with 5 returns null`() {
        assertNull(PhoneNumber.fromNational("532123456"))
        assertNull(PhoneNumber.fromNational("4321234567"))
        assertNull(PhoneNumber.fromNational("53212345678"))
        assertNull(PhoneNumber.fromNational("53212345a7"))
        assertNotNull(PhoneNumber.fromNational("5321234567"))
    }

    @Test
    fun `fromE164 when other country returns null`() {
        assertNull(PhoneNumber.fromE164("+14155550123"))
        assertEquals("5321234567", PhoneNumber.fromE164("+905321234567")?.nationalNumber)
    }

    @Test
    fun `formatted groups digits as 5XX XXX XX XX`() {
        assertEquals("+90 532 123 45 67", PhoneNumber.fromNational("5321234567")?.formatted)
    }

    @Test
    fun `nationalDigits when partial input keeps digits and caps at ten`() {
        assertEquals("53", PhoneNumber.nationalDigits("+90 53"))
        assertEquals("5321234567", PhoneNumber.nationalDigits("05321234567999"))
    }

    @Test
    fun `formattedInput when partial groups as far as it goes`() {
        assertEquals("532 12", PhoneNumber.formattedInput("0532 12"))
        assertEquals("", PhoneNumber.formattedInput(""))
    }

    @Test
    fun `equality when same national number is equal`() {
        assertEquals(PhoneNumber.fromInput("0532 123 45 67"), PhoneNumber.fromE164("+905321234567"))
    }

    @Test
    fun `verification code digits when mixed input keeps six ascii digits`() {
        assertEquals("123456", VerificationCode.digits("12 34-56 78"))
    }
}
