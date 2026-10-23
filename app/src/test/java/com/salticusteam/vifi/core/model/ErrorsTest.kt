package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorsTest {

    @Test
    fun `archive error userMessage when known case matches ios copy`() {
        assertEquals("İçerik bulunamadı.", ArchiveError.NotFound.userMessage)
        assertEquals("İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene.", ArchiveError.Offline.userMessage)
        assertEquals(
            "Dosya şu anda sunucudan alınamıyor. Lütfen daha sonra tekrar dene.",
            ArchiveError.FileUnavailable(503).userMessage,
        )
    }

    @Test
    fun `unknown error when message given uses it as userMessage`() {
        assertEquals("Boom", ArchiveError.Unknown("Boom").userMessage)
        assertEquals("Boom", AuthError.Unknown("Boom").userMessage)
        assertEquals("Boom", ArchiveError.Unknown("Boom").message)
    }

    @Test
    fun `auth analyticsReason when each case returns stable snake_case reason`() {
        assertEquals("sms_temporarily_unavailable", AuthError.SmsTemporarilyUnavailable.analyticsReason)
        assertEquals("requires_recent_login", AuthError.RequiresRecentLogin.analyticsReason)
        assertEquals("unknown", AuthError.Unknown("x").analyticsReason)
    }

    @Test
    fun `submission and unlock analyticsReason when cases map to stable reasons`() {
        assertEquals("invalid_submission", ExamSubmissionError.InvalidSubmission.analyticsReason)
        assertEquals("insufficient_coins", ExamUnlockError.InsufficientCoins.analyticsReason)
        assertEquals("unknown", ExamUnlockError.Unknown("x").analyticsReason)
    }

    @Test
    fun `sms unavailable userMessage when concatenated matches ios copy`() {
        assertEquals(
            "SMS servisinde geçici bir sorun nedeniyle kod gönderilemedi. Lütfen bir süre sonra tekrar dene; sorun sürerse bizimle iletişime geç.",
            AuthError.SmsTemporarilyUnavailable.userMessage,
        )
    }

    @Test
    fun `moderation and profile messages when known match ios copy`() {
        assertEquals("Bu işlem için yetkin yok.", ModerationError.PermissionDenied.userMessage)
        assertEquals(
            "Profilin şu anda kaydedilemiyor. Lütfen daha sonra tekrar dene.",
            ProfileError.PermissionDenied.userMessage,
        )
    }

    @Test
    fun `auth user formattedPhoneNumber when turkish number formats else raw`() {
        assertEquals("+90 532 123 45 67", AuthUser("u", "+905321234567").formattedPhoneNumber)
        assertEquals("+14155550123", AuthUser("u", "+14155550123").formattedPhoneNumber)
        assertEquals(null, AuthUser("u", null).formattedPhoneNumber)
    }

    @Test
    fun `coin wallet empty when no wallet has zero balance`() {
        assertEquals(CoinWallet(0, null), CoinWallet.EMPTY)
    }
}
