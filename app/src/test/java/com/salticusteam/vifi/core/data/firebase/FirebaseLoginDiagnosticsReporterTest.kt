package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.LoginFailureInfo
import com.salticusteam.vifi.core.model.LoginFailureStage
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirebaseLoginDiagnosticsReporterTest {

    private val device = FirebaseLoginDiagnosticsReporter.DeviceInfo("2.5 (16)", "15", "Google Pixel 8")
    private val info = LoginFailureInfo(
        reason = "sms_temporarily_unavailable",
        backendCode = 39,
        stage = LoginFailureStage.SEND_CODE,
        phoneNumber = "+905321234567",
        occurredAt = Instant.parse("2026-10-23T09:15:00Z"),
    )

    @Test
    fun `payload uses the server field names`() {
        val payload = FirebaseLoginDiagnosticsReporter.payload(info, "valid", device)

        assertEquals(
            mapOf(
                "reason" to "sms_temporarily_unavailable",
                "step" to "send_code",
                "phoneNumber" to "+905321234567",
                "appCheckStatus" to "valid",
                "appVersion" to "2.5 (16)",
                "osVersion" to "15",
                "deviceModel" to "Google Pixel 8",
                "occurredAt" to "2026-10-23T09:15:00Z",
                "backendCode" to 39,
            ),
            payload,
        )
    }

    @Test
    fun `payload without backend code or number omits the code and sends an empty number`() {
        val payload = FirebaseLoginDiagnosticsReporter.payload(
            info.copy(backendCode = null, phoneNumber = null, stage = LoginFailureStage.VERIFY),
            "error",
            device,
        )

        assertFalse(payload.containsKey("backendCode"))
        assertEquals("", payload["phoneNumber"])
        assertEquals("verify", payload["step"])
    }

    @Test
    fun `signature joins number reason and stage`() {
        assertEquals(
            "+905321234567|sms_temporarily_unavailable|send_code",
            FirebaseLoginDiagnosticsReporter.signature(info),
        )
    }
}
