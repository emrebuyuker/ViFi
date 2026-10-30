package com.salticusteam.vifi.core.data.firebase

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.messaging.FirebaseMessaging
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsStore
import com.salticusteam.vifi.core.data.preferences.fake.FakeNotificationPromptHistory
import com.salticusteam.vifi.core.data.preferences.fake.FakeUserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationSettingsService
import com.salticusteam.vifi.core.data.session.SessionState
import com.salticusteam.vifi.core.model.AuthUser
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.notifications.NotificationPermissionRequester
import com.salticusteam.vifi.core.util.ForegroundActivityHolder
import com.salticusteam.vifi.feature.notifications.NotificationPrompt
import com.salticusteam.vifi.feature.notifications.NotificationPromptCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * NDEC-01 on the device itself: the live permission service maps Android's notification permission onto iOS's states,
 * and the first-run offer follows from it.
 *
 * - **Android 13+** has the runtime permission: never asked → "Sorulmadı" and step 1/2; after the app's own "no" →
 *   denied; granted → allowed, and only the campaign consent (2/2) is offered while it is unanswered.
 * - **Android 12 and below** has none: notifications are on unless switched off in the system, so they read as allowed,
 *   the system is never asked and step 1/2 is skipped — only the consent is offered once (iOS "consent catch-up").
 *
 * Nothing reaches Firebase: the consent record and the prompt history are in memory; only the permission is read from
 * the system (and, on Android 13+, granted through the test's shell). Each test runs only on its API level; the
 * never-asked case needs a device that never granted the permission (`adb shell pm revoke <pkg>
 * android.permission.POST_NOTIFICATIONS` before the run), since revoking from inside the test kills its process.
 */
// By name: "beforeAnyAnswer" must run before "whenGranted" grants the permission for good.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class NotificationPermissionMappingDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = FakeUserPreferencesRepository()
    private val service = FirebaseNotificationService(
        context = context,
        messaging = FirebaseMessaging.getInstance(),
        requester = NotificationPermissionRequester(),
        preferences = preferences,
        activityHolder = ForegroundActivityHolder(),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun android12AndBelow_notificationsOnReadAsAllowedAndTheFirstRunOffersOnlyTheConsent() = runBlocking {
        assumeTrue("Android 12 and below only", Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        // A fresh install: on until the user switches them off in the system settings.
        assertTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())

        assertEquals(NotificationPermission.AUTHORIZED, service.permission())
        assertFalse(service.canRequestPermission())
        // Nothing to ask: no dialog, nothing recorded as asked.
        assertTrue(service.requestAuthorization())
        assertFalse(preferences.notificationsPrompted.first())

        assertEquals(NotificationPrompt.Kind.CONSENT, firstRunOffer()?.kind)
    }

    @Test
    fun android13AndAbove_beforeAnyAnswer_readsAsNotAskedAndTheFirstRunOffersStep1() = runBlocking {
        assumeTrue("Android 13+ only", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        // A grant survives reinstalls: only a fresh install (or `pm revoke`) has never been asked.
        assumeFalse("The permission was granted on this device before", isGranted())

        assertEquals(NotificationPermission.NOT_DETERMINED, service.permission())
        assertTrue(service.canRequestPermission())
        assertEquals(NotificationPrompt.Kind.PERMISSION, firstRunOffer()?.kind)
        // Once the app's own prompt has shown the system dialog, "not granted" is a "no".
        preferences.setNotificationsPrompted(true)
        assertEquals(NotificationPermission.DENIED, service.permission())
    }

    @Test
    fun android13AndAbove_whenGranted_readsAsAllowedAndTheFirstRunOffersOnlyTheConsent() = runBlocking {
        assumeTrue("Android 13+ only", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(NotificationPermission.AUTHORIZED, service.permission())
        assertFalse(service.canRequestPermission())
        // Nothing left to ask: no dialog, nothing recorded as asked.
        assertTrue(service.requestAuthorization())
        assertFalse(preferences.notificationsPrompted.first())
        assertEquals(NotificationPrompt.Kind.CONSENT, firstRunOffer()?.kind)
    }

    // Helpers
    /** What a fresh install's first run queues: no prompt history, the consent never answered. */
    private suspend fun firstRunOffer(): NotificationPrompt? {
        val settings = NotificationSettingsStore(FakeNotificationSettingsService(), scope)
        settings.start(flowOf(SessionState.SignedIn(AuthUser(id = "device-test", phoneNumber = null))))
        val coordinator = NotificationPromptCoordinator(service, settings, FakeNotificationPromptHistory())
        coordinator.offerFirstRunIfNeeded()
        return coordinator.pending.value
    }

    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
