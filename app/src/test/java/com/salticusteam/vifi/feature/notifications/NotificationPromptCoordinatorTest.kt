package com.salticusteam.vifi.feature.notifications

import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeNotificationPromptHistory
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationSettingsService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.core.model.NotificationSettingsError
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NotificationPromptCoordinatorTests` (21), on Android's permission model (NDEC-01). */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptCoordinatorTest {

    private var now = Instant.parse("2026-10-30T09:00:00Z")
    private val history = FakeNotificationPromptHistory(now = { now })

    private fun later(days: Long) {
        now = now.plus(Duration.ofDays(days))
    }

    private fun TestScope.sut(
        permission: NotificationPermission = NotificationPermission.NOT_DETERMINED,
        consent: NotificationConsent? = null,
        settingsService: NotificationSettingsService = FakeNotificationSettingsService(consent),
    ): Pair<NotificationPromptCoordinator, FakeNotificationService> {
        val notifications = FakeNotificationService(permission)
        val settings = NotificationSettingsStore(settingsService, backgroundScope)
            .apply { start(MutableStateFlow(SessionState.SignedIn(FakeAuthService.DEFAULT_USER))) }
        return NotificationPromptCoordinator(notifications, settings, history) to notifications
    }

    // --- First offer ---

    @Test
    fun `nothing is asked silently, Android has no quiet permission`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, notifications) = sut()

        sut.offerFirstRunIfNeeded()

        assertEquals(0, notifications.requestCount)
        assertEquals(NotificationPermission.NOT_DETERMINED, notifications.permission())
    }

    @Test
    fun `a device never asked is offered the permission step`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = sut()

        sut.offerFirstRunIfNeeded()

        val pending = sut.pending.value
        assertEquals(NotificationPrompt.Kind.PERMISSION, pending?.kind)
        assertEquals(NotificationPromptSource.PROMPT, pending?.source)
        assertEquals(NotificationPermission.NOT_DETERMINED, pending?.permission)
    }

    @Test
    fun `the permission step is not offered twice`() = runTest(UnconfinedTestDispatcher()) {
        history.primingOffered = true
        val (sut, _) = sut()

        sut.offerFirstRunIfNeeded()

        assertNull(sut.pending.value)
    }

    @Test
    fun `a waiting or shown run is never replaced`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = sut()
        sut.offerFirstRunIfNeeded()
        val first = sut.pending.value

        sut.offerFirstRunIfNeeded()
        assertEquals(first, sut.pending.value)

        assertEquals(first, sut.present())
        assertEquals(first, sut.presented.value)
        assertNull(sut.pending.value)
        sut.offerFirstRunIfNeeded()
        assertNull(sut.pending.value)
    }

    @Test
    fun `nothing is offered first while denied`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = sut(NotificationPermission.DENIED)

        sut.offerFirstRunIfNeeded()

        assertNull(sut.pending.value)
    }

    @Test
    fun `an allowed device that never answered is offered the consent once`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = sut(NotificationPermission.AUTHORIZED)

        sut.offerFirstRunIfNeeded()
        assertEquals(NotificationPrompt.Kind.CONSENT, sut.pending.value?.kind)
        assertEquals(NotificationPromptSource.PROMPT, sut.pending.value?.source)

        sut.dismiss()
        history.consentOffered = true
        sut.offerFirstRunIfNeeded()
        assertNull(sut.pending.value)
    }

    @Test
    fun `no consent catch-up once answered, yes or no`() = runTest(UnconfinedTestDispatcher()) {
        listOf(true, false).forEach { answer ->
            val (sut, _) = sut(NotificationPermission.AUTHORIZED, NotificationConsent(marketing = answer))

            sut.offerFirstRunIfNeeded()

            assertNull(sut.pending.value)
        }
    }

    @Test
    fun `no consent catch-up when the record cannot be read`() = runTest(UnconfinedTestDispatcher()) {
        val unreadable = FakeNotificationSettingsService().apply { readFailure = NotificationSettingsError.Unavailable }
        val (sut, _) = sut(NotificationPermission.AUTHORIZED, settingsService = unreadable)

        sut.offerFirstRunIfNeeded()

        assertNull(sut.pending.value)
        assertFalse(history.consentOffered)
    }

    @Test
    fun `the consent catch-up waits five seconds for the record, then leaves it for next time`() =
        runTest(UnconfinedTestDispatcher()) {
            val silent = object : NotificationSettingsService {
                override fun consentChanges(uid: String): Flow<NotificationConsent?> = flow { awaitCancellation() }
                override suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource) = Unit
            }
            val (sut, _) = sut(NotificationPermission.AUTHORIZED, settingsService = silent)

            sut.offerFirstRunIfNeeded()

            assertEquals(5_000L, currentTime)
            assertNull(sut.pending.value)
            assertFalse(history.consentOffered)
        }

    @Test
    fun `4_0_0's prompt without an allow counts as a no that arms the re-ask`() = runTest(UnconfinedTestDispatcher()) {
        history.primingOffered = true
        val (sut, _) = sut(NotificationPermission.NOT_DETERMINED)

        sut.offerFirstRunIfNeeded()

        assertNull(sut.pending.value)
        assertFalse(history.shouldReaskPermission())
        later(31)
        assertTrue(history.shouldReaskPermission())
    }

    // --- Re-asking the permission after a submission ---

    @Test
    fun `the permission is asked again after a submission 30 days after a no`() = runTest(UnconfinedTestDispatcher()) {
        history.recordPermissionDeclined()
        later(31)
        val (sut, _) = sut()

        sut.examWasSubmitted()

        assertEquals(NotificationPrompt.Kind.PERMISSION, sut.pending.value?.kind)
        assertEquals(NotificationPromptSource.REASK, sut.pending.value?.source)
    }

    @Test
    fun `not asked again too soon`() = runTest(UnconfinedTestDispatcher()) {
        history.recordPermissionDeclined()
        later(29)
        val (sut, _) = sut()

        sut.examWasSubmitted()

        assertNull(sut.pending.value)
    }

    @Test
    fun `not asked again without an earlier no`() = runTest(UnconfinedTestDispatcher()) {
        history.primingOffered = true
        later(400)
        val (sut, _) = sut()

        sut.examWasSubmitted()

        assertNull(sut.pending.value)
    }

    @Test
    fun `not asked again once allowed`() = runTest(UnconfinedTestDispatcher()) {
        history.recordPermissionDeclined()
        later(31)
        val (sut, _) = sut(NotificationPermission.AUTHORIZED)

        sut.examWasSubmitted()

        assertNull(sut.pending.value)
    }

    @Test
    fun `a re-ask while denied carries the denial (it offers the settings)`() = runTest(UnconfinedTestDispatcher()) {
        history.recordPermissionDeclined()
        later(31)
        val (sut, _) = sut(NotificationPermission.DENIED)

        sut.examWasSubmitted()

        assertEquals(NotificationPermission.DENIED, sut.pending.value?.permission)
    }

    @Test
    fun `the permission is asked again only once`() = runTest(UnconfinedTestDispatcher()) {
        history.recordPermissionDeclined()
        later(31)
        history.markPermissionReaskShown()
        history.recordPermissionDeclined()
        later(365)
        val (sut, _) = sut()

        sut.examWasSubmitted()

        assertNull(sut.pending.value)
    }

    @Test
    fun `a queued re-ask that never reached the screen is not used up`() = runTest(UnconfinedTestDispatcher()) {
        history.recordPermissionDeclined()
        later(31)
        val (sut, _) = sut()
        sut.examWasSubmitted()

        sut.dismiss()

        assertTrue(history.shouldReaskPermission())
    }

    // --- Re-asking the consent in the inbox ---

    @Test
    fun `the consent is asked again in the inbox 30 days after a no`() = runTest(UnconfinedTestDispatcher()) {
        history.recordConsentDeclined()
        later(31)
        val (sut, _) = sut(NotificationPermission.AUTHORIZED, NotificationConsent(marketing = false))

        assertTrue(sut.inboxConsentReask())

        assertEquals(NotificationPrompt.Kind.CONSENT, sut.pending.value?.kind)
        assertEquals(NotificationPromptSource.REASK, sut.pending.value?.source)
    }

    @Test
    fun `no consent re-ask without a no, too soon, or with campaigns on`() = runTest(UnconfinedTestDispatcher()) {
        val (withoutNo, _) = sut(NotificationPermission.AUTHORIZED, NotificationConsent(marketing = false))
        later(400)
        assertFalse(withoutNo.inboxConsentReask())

        history.recordConsentDeclined()
        later(10)
        val (tooSoon, _) = sut(NotificationPermission.AUTHORIZED, NotificationConsent(marketing = false))
        assertFalse(tooSoon.inboxConsentReask())

        later(21)
        val (campaignsOn, _) = sut(NotificationPermission.AUTHORIZED, NotificationConsent(marketing = true))
        assertFalse(campaignsOn.inboxConsentReask())
    }

    @Test
    fun `the consent is asked again only once`() = runTest(UnconfinedTestDispatcher()) {
        history.recordConsentDeclined()
        later(31)
        history.markConsentReaskShown()
        val (sut, _) = sut(NotificationPermission.AUTHORIZED, NotificationConsent(marketing = false))

        assertFalse(sut.inboxConsentReask())
    }

    @Test
    fun `present moves the waiting run on screen and dismiss clears both`() = runTest(UnconfinedTestDispatcher()) {
        val (sut, _) = sut()
        sut.offerFirstRunIfNeeded()

        val shown = sut.present()
        assertEquals(shown, sut.presented.value)
        assertNull(sut.present())

        sut.dismiss()
        assertNull(sut.pending.value)
        assertNull(sut.presented.value)
        assertTrue(sut.isFree)
    }
}
