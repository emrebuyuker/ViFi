package com.salticusteam.vifi.feature.notificationsettings

import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationSettingsService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Account › Bildirim Ayarları: how the OS permission reads and what it offers, and the campaign switch (iOS
 * `NotificationSettingsViewModelTests`, 9, plus Android's own: a "no" the system can still ask again about, the
 * permission read again on return, a double tap on "Bildirimleri Aç", a change while another is saving, the record
 * changing elsewhere, and the screen view).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = FakeAuthService.DEFAULT_USER.id
    private val notifications = StubNotificationService()
    private val analytics = FakeAnalyticsTracker()
    private val sessions = MutableStateFlow<SessionState>(SessionState.Unknown)

    private fun TestScope.makeSut(
        service: NotificationSettingsService = FakeNotificationSettingsService(),
    ): NotificationSettingsViewModel {
        val settings = NotificationSettingsStore(service, backgroundScope).apply { start(sessions) }
        // The test scope itself: a switch change runs in the app scope, which advanceUntilIdle then waits for.
        return NotificationSettingsViewModel(notifications, settings, analytics, appScope = this)
    }

    /** Signs the user in, so the store starts reading their record. */
    private fun TestScope.signIn() {
        sessions.value = SessionState.SignedIn(FakeAuthService.DEFAULT_USER)
        settle()
    }

    /** The store's listener runs in backgroundScope, which advanceUntilIdle alone does not drain. */
    private fun TestScope.settle() {
        advanceUntilIdle()
        runCurrent()
    }

    // --- Permission ---

    @Test
    fun `each permission reads in words and offers the right way to turn it on`() = runTest {
        val sut = makeSut()
        data class Case(
            val permission: NotificationPermission,
            val systemCanAsk: Boolean,
            val label: Int,
            val canRequest: Boolean,
            val needsSettings: Boolean,
            val note: Int?,
        )
        val cases = listOf(
            Case(NotificationPermission.AUTHORIZED, false, R.string.notification_settings_permission_authorized, false, false, null),
            Case(
                NotificationPermission.DENIED, false, R.string.notification_settings_permission_denied, false, true,
                R.string.notification_settings_permission_denied_note,
            ),
            Case(
                NotificationPermission.NOT_DETERMINED, true, R.string.notification_settings_permission_not_determined,
                true, false, R.string.notification_settings_permission_not_determined_note,
            ),
            // Android: after one "no" the system still shows its dialog once more, so it is offered again.
            Case(
                NotificationPermission.DENIED, true, R.string.notification_settings_permission_denied, true, false,
                R.string.notification_settings_permission_not_determined_note,
            ),
        )

        for (case in cases) {
            notifications.current = case.permission
            notifications.systemCanAsk = case.systemCanAsk
            sut.onResume()
            settle()

            val state = sut.uiState.value
            assertEquals(case.toString(), case.label, state.permissionLabel)
            assertEquals(case.toString(), case.canRequest, state.canRequestPermission)
            assertEquals(case.toString(), case.needsSettings, state.needsSettingsApp)
            assertEquals(case.toString(), case.note, state.permissionNote)
        }
    }

    @Test
    fun `until the permission is read nothing is offered`() = runTest {
        val sut = makeSut()

        val state = sut.uiState.value
        assertNull(state.permission)
        assertEquals(R.string.common_loading, state.permissionLabel)
        assertFalse(state.canRequestPermission)
        assertFalse(state.needsSettingsApp)
        assertNull(state.permissionNote)
    }

    @Test
    fun `enabling asks the system, shows the new status and reports nothing`() = runTest {
        notifications.current = NotificationPermission.NOT_DETERMINED
        notifications.systemCanAsk = true
        val sut = makeSut()
        sut.onResume()
        settle()

        sut.onEnable()
        settle()

        assertEquals(1, notifications.authorizationRequests)
        assertEquals(NotificationPermission.AUTHORIZED, sut.uiState.value.permission)
        assertFalse(sut.uiState.value.canRequestPermission)
        assertFalse(sut.uiState.value.isRequestingPermission)
        // Account's "Bildirimleri Aç" is not the prompt flow: no priming events (iOS B.6).
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `enabling does nothing when the system cannot be asked`() = runTest {
        notifications.current = NotificationPermission.DENIED
        notifications.systemCanAsk = false
        val sut = makeSut()
        sut.onResume()
        settle()

        sut.onEnable()
        settle()

        assertEquals(0, notifications.authorizationRequests)
        assertEquals(NotificationPermission.DENIED, sut.uiState.value.permission)
    }

    @Test
    fun `a second tap while the system dialog is up is ignored`() = runTest {
        notifications.current = NotificationPermission.NOT_DETERMINED
        notifications.systemCanAsk = true
        val dialog = CompletableDeferred<Unit>()
        notifications.dialog = dialog
        val sut = makeSut()
        sut.onResume()
        settle()

        sut.onEnable()
        runCurrent()
        assertTrue(sut.uiState.value.isRequestingPermission)
        sut.onEnable()
        dialog.complete(Unit)
        settle()

        assertEquals(1, notifications.authorizationRequests)
        assertFalse(sut.uiState.value.isRequestingPermission)
    }

    @Test
    fun `coming back to the screen reads the permission again`() = runTest {
        notifications.current = NotificationPermission.DENIED
        val sut = makeSut()
        sut.onResume()
        settle()
        assertTrue(sut.uiState.value.needsSettingsApp)

        // Turned on in the system settings meanwhile.
        notifications.current = NotificationPermission.AUTHORIZED
        sut.onResume()
        settle()

        assertEquals(NotificationPermission.AUTHORIZED, sut.uiState.value.permission)
        assertFalse(sut.uiState.value.needsSettingsApp)
        assertNull(sut.uiState.value.permissionNote)
    }

    // --- Campaign switch ---

    @Test
    fun `the switch is off and unavailable until the record is read`() = runTest {
        val service = FakeNotificationSettingsService()
        val sut = makeSut(service)

        assertFalse(sut.uiState.value.isConsentAvailable)
        assertFalse(sut.uiState.value.marketingEnabled)

        sut.onMarketingChange(true)
        settle()

        assertTrue("Nothing is written before the real answer is known", service.writes.isEmpty())
        assertFalse(sut.uiState.value.isSavingConsent)
    }

    @Test
    fun `turning campaigns on writes a settings answer and reports it`() = runTest {
        val service = FakeNotificationSettingsService()
        val sut = makeSut(service)
        signIn()

        sut.onMarketingChange(true)
        settle()

        assertEquals(listOf(FakeNotificationSettingsService.Write(uid, true, NotificationConsentSource.SETTINGS)), service.writes)
        assertTrue(sut.uiState.value.marketingEnabled)
        assertFalse(sut.uiState.value.saveFailed)
        assertEquals(listOf(AnalyticsEvent.MarketingConsentAccepted(NotificationPromptSource.SETTINGS)), analytics.events)
    }

    @Test
    fun `turning campaigns off writes a settings answer and reports it`() = runTest {
        val service = FakeNotificationSettingsService(initial = NotificationConsent(marketing = true))
        val sut = makeSut(service)
        signIn()
        assertTrue(sut.uiState.value.marketingEnabled)

        sut.onMarketingChange(false)
        settle()

        assertEquals(listOf(FakeNotificationSettingsService.Write(uid, false, NotificationConsentSource.SETTINGS)), service.writes)
        assertFalse(sut.uiState.value.marketingEnabled)
        assertEquals(listOf(AnalyticsEvent.MarketingConsentDeclined(NotificationPromptSource.SETTINGS)), analytics.events)
    }

    @Test
    fun `a switch that cannot be saved goes back and says so`() = runTest {
        val service = FakeNotificationSettingsService(initial = NotificationConsent(marketing = false))
        val sut = makeSut(service)
        signIn()
        service.writeFailure = IOException("rejected")

        sut.onMarketingChange(true)
        settle()

        assertFalse(sut.uiState.value.marketingEnabled)
        assertTrue(sut.uiState.value.saveFailed)
        assertTrue(analytics.events.isEmpty())

        service.writeFailure = null
        sut.onMarketingChange(true)
        settle()

        assertTrue(sut.uiState.value.marketingEnabled)
        assertFalse(sut.uiState.value.saveFailed)
    }

    @Test
    fun `a record that cannot be read leaves the switch unavailable`() = runTest {
        val service = FakeNotificationSettingsService().apply { readFailure = IOException("offline") }
        val sut = makeSut(service)

        signIn()

        assertTrue(sut.uiState.value.isConsentUnavailable)
        assertFalse(sut.uiState.value.isConsentAvailable)
        assertFalse(sut.uiState.value.canChangeMarketing)
    }

    @Test
    fun `a change while another is being saved is ignored`() = runTest {
        val service = GatedSettingsService(FakeNotificationSettingsService())
        val sut = makeSut(service)
        signIn()
        val ack = CompletableDeferred<Unit>()
        service.gate = ack

        sut.onMarketingChange(true)
        runCurrent()
        assertTrue(sut.uiState.value.isSavingConsent)
        assertTrue("The switch shows the new value at once", sut.uiState.value.marketingEnabled)
        assertFalse(sut.uiState.value.canChangeMarketing)

        sut.onMarketingChange(false)
        ack.complete(Unit)
        settle()

        assertEquals(1, service.attempts)
        assertTrue(sut.uiState.value.marketingEnabled)
        assertFalse(sut.uiState.value.isSavingConsent)
    }

    @Test
    fun `the switch follows the record when it changes elsewhere`() = runTest {
        val service = FakeNotificationSettingsService()
        val sut = makeSut(service)
        signIn()
        assertFalse(sut.uiState.value.marketingEnabled)

        // Answered on another device.
        service.set(NotificationConsent(marketing = true))
        settle()

        assertTrue(sut.uiState.value.marketingEnabled)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `the screen is reported as NotificationSettings`() = runTest {
        val sut = makeSut()

        sut.onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.NOTIFICATION_SETTINGS)), analytics.events)
        assertEquals("NotificationSettings", AnalyticsScreen.NOTIFICATION_SETTINGS)
    }

    /** The OS permission as a test sets it (iOS `StubNotificationService`). */
    private class StubNotificationService : NotificationService {
        var current = NotificationPermission.AUTHORIZED

        /** Whether the system dialog can still be shown. */
        var systemCanAsk = false

        /** While set, a request waits here: the system dialog is up. */
        var dialog: CompletableDeferred<Unit>? = null

        var authorizationRequests = 0
            private set

        override suspend fun permission(): NotificationPermission = current

        override suspend fun requestAuthorization(): Boolean {
            authorizationRequests++
            dialog?.await()
            if (systemCanAsk) current = NotificationPermission.AUTHORIZED
            return current == NotificationPermission.AUTHORIZED
        }

        override suspend fun canRequestPermission(): Boolean = systemCanAsk && current != NotificationPermission.AUTHORIZED

        override suspend fun token(): String? = null

        override suspend fun subscribe(topic: String) = Unit

        override suspend fun unsubscribe(topic: String) = Unit
    }

    /** Holds each write until [gate] opens (an acknowledgement still on its way). */
    private class GatedSettingsService(private val delegate: FakeNotificationSettingsService) : NotificationSettingsService {
        var gate: CompletableDeferred<Unit>? = null

        var attempts = 0
            private set

        override fun consentChanges(uid: String): Flow<NotificationConsent?> = delegate.consentChanges(uid)

        override suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource) {
            attempts++
            gate?.await()
            delegate.setMarketing(uid, enabled, source)
        }
    }
}
