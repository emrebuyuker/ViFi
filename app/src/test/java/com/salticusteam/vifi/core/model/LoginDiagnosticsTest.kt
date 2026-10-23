package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class LoginDiagnosticsTest {

    private val t0 = Instant.parse("2026-05-28T12:00:00Z")

    @Test
    fun `supportContext when full info lists number stage and reason with code`() {
        val info = LoginFailureInfo("sms_temporarily_unavailable", 39, LoginFailureStage.SEND_CODE, "+905321234567", t0)

        assertEquals(
            "Giriş sorunu bildirimi\nNumara: +905321234567\nAşama: Kod gönderme\n" +
                "Sebep: sms_temporarily_unavailable (Error code: 39)",
            info.supportContext,
        )
    }

    @Test
    fun `supportContext when number and code unknown omits them`() {
        val info = LoginFailureInfo("invalid_code", null, LoginFailureStage.VERIFY, null, t0)

        assertEquals("Giriş sorunu bildirimi\nAşama: Kod doğrulama\nSebep: invalid_code", info.supportContext)
    }

    @Test
    fun `throttle when same signature inside window suppresses and after window allows`() {
        val throttle = LoginDiagnosticThrottle(Duration.ofSeconds(60))

        assertTrue(throttle.shouldSend("a", t0))
        assertFalse(throttle.shouldSend("a", t0.plusSeconds(59)))
        assertTrue(throttle.shouldSend("a", t0.plusSeconds(60)))
    }

    @Test
    fun `throttle when signature differs allows immediately`() {
        val throttle = LoginDiagnosticThrottle()

        assertTrue(throttle.shouldSend("a", t0))
        assertTrue(throttle.shouldSend("b", t0.plusSeconds(1)))
    }

    @Test
    fun `failure stage raw values are stable`() {
        assertEquals("send_code", LoginFailureStage.SEND_CODE.raw)
        assertEquals("verify", LoginFailureStage.VERIFY.raw)
    }
}
