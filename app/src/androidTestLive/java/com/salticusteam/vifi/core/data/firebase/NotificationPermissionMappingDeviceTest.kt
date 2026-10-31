package com.salticusteam.vifi.core.data.firebase

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
 *   Switched off in the system settings they read as denied, nothing can ask again and nothing is offered first.
 * - **The real system dialog (Android 13+)**: after a first "Reddet" Android shows the dialog once more (the rationale
 *   flag), so the app may ask again; after a second "Reddet" it is final and only the system settings are left.
 *
 * Nothing reaches Firebase: the consent record and the prompt history are in memory; only the permission is read from
 * the system (switched in the system's own settings page on Android 12 and below; on Android 13+ granted through the
 * test's shell or answered in the real dialog). Each test runs only on its API level; the Android 13+ ones need a
 * device that never granted the permission (`adb shell pm revoke <pkg> android.permission.POST_NOTIFICATIONS` before
 * the run), since revoking from inside the test kills its process; forgetting the dialog's answers does not.
 */
// By name: "beforeAnyAnswer" and "systemDialog" must run before "whenGranted" grants the permission for good.
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class NotificationPermissionMappingDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = FakeUserPreferencesRepository()
    private val requester = NotificationPermissionRequester()
    private val activityHolder = ForegroundActivityHolder()
    private val service = FirebaseNotificationService(
        context = context,
        messaging = FirebaseMessaging.getInstance(),
        requester = requester,
        preferences = preferences,
        activityHolder = activityHolder,
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun android12AndBelow_notificationsOffReadAsDeniedAndNothingIsOffered() = runBlocking<Unit> {
        assumeTrue("Android 12 and below only", Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        switchAppNotifications(on = false)
        try {
            assertEquals(NotificationPermission.DENIED, service.permission())
            // There is no dialog to show: only the system settings can turn them back on.
            assertFalse(service.canRequestPermission())
            assertFalse(service.requestAuthorization())
            assertFalse(preferences.notificationsPrompted.first())
            // Never offered first while denied: Account › Bildirim Ayarları leads to the settings then.
            assertNull(firstRunOffer())
        } finally {
            switchAppNotifications(on = true)
        }
    }

    @Test
    fun android12AndBelow_notificationsOnReadAsAllowedAndTheFirstRunOffersOnlyTheConsent() = runBlocking {
        assumeTrue("Android 12 and below only", Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        // A fresh install: on until the user switches them off in the system settings.
        assertTrue(areNotificationsEnabled())

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
    fun android13AndAbove_systemDialog_aFirstNoLeavesOneMoreAskAndASecondIsFinal() = runBlocking<Unit> {
        assumeTrue("Android 13+ only", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        assumeFalse("The permission was granted on this device before", isGranted())
        // Not granted, but maybe answered by an earlier run: back to never asked (clearing flags keeps the process).
        clearPermissionAnswer()
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                var launcher: ActivityResultLauncher<String>? = null
                // The same wiring as MainActivity: the system launcher feeds the requester, the holder lends the
                // Activity for the rationale flag.
                scenario.onActivity { activity ->
                    activityHolder.attach(activity)
                    launcher = activity.activityResultRegistry.register(
                        "notification-permission",
                        ActivityResultContracts.RequestPermission(),
                        requester::onResult,
                    ).also(requester::attach)
                }
                assertEquals(NotificationPermission.NOT_DETERMINED, service.permission())
                assertTrue(service.canRequestPermission())

                // A first "Reddet": denied, yet Android lets the app show the dialog once more.
                assertFalse(requestAndDenyInTheSystemDialog())
                assertTrue(preferences.notificationsPrompted.first())
                assertEquals(NotificationPermission.DENIED, service.permission())
                assertTrue(service.canRequestPermission())

                // A second "Reddet" is final: no more dialogs, only the system settings can turn them on.
                assertFalse(requestAndDenyInTheSystemDialog())
                assertEquals(NotificationPermission.DENIED, service.permission())
                assertFalse(service.canRequestPermission())

                launcher?.unregister()
            }
        } finally {
            clearPermissionAnswer()
        }
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

    /** Asks through the app's own path ([FirebaseNotificationService.requestAuthorization]) and taps "Reddet". */
    private suspend fun requestAndDenyInTheSystemDialog(): Boolean = coroutineScope {
        val answer = async(Dispatchers.Main) { service.requestAuthorization() }
        tapDenyInTheSystemDialog()
        answer.await()
    }

    private fun tapDenyInTheSystemDialog() =
        tapSystemNode(DENY_BUTTON_IDS, "The system's notification permission dialog did not appear")

    /** Flips "All ViFi notifications" on the system's own settings page (Android 12 and below has no shell for it). */
    private fun switchAppNotifications(on: Boolean) {
        if (areNotificationsEnabled() == on) return
        shell(
            "am start -a ${Settings.ACTION_APP_NOTIFICATION_SETTINGS} " +
                "--es ${Settings.EXTRA_APP_PACKAGE} ${context.packageName}",
        )
        tapSystemNode(MAIN_SWITCH_IDS, "The app's notification settings did not open")
        val deadline = SystemClock.uptimeMillis() + SYSTEM_UI_TIMEOUT_MS
        while (areNotificationsEnabled() != on && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(POLL_MS)
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        assertEquals(on, areNotificationsEnabled())
    }

    /** Taps the first of [ids] that shows up on screen (a system dialog or the Settings app). */
    private fun tapSystemNode(ids: List<String>, missing: String) {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val deadline = SystemClock.uptimeMillis() + SYSTEM_UI_TIMEOUT_MS
        var seen: List<CharSequence?> = emptyList()
        while (SystemClock.uptimeMillis() < deadline) {
            // The active window alone is not always reported (Android 12), so every window on screen is searched.
            val roots = (listOf(automation.rootInActiveWindow) + automation.windows.map { it.root }).filterNotNull()
            seen = roots.map { it.packageName }
            val node = ids.firstNotNullOfOrNull { id ->
                roots.firstNotNullOfOrNull { root -> root.findAccessibilityNodeInfosByViewId(id).firstOrNull() }
            }
            if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return
            SystemClock.sleep(POLL_MS)
        }
        fail("$missing (on screen: $seen)")
    }

    private fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Forgets the dialog's answers ("Reddet", "Bir daha sorma"); unlike a revoke it keeps the test's process. */
    private fun clearPermissionAnswer() = shell(
        "pm clear-permission-flags ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS} " +
            "user-set user-fixed",
    )

    private fun shell(command: String) {
        val output = instrumentation.uiAutomation.executeShellCommand(command)
        // Reading to the end waits for the command to finish.
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private companion object {
        const val SYSTEM_UI_TIMEOUT_MS = 20_000L
        const val POLL_MS = 100L

        // The first dialog's "Reddet" and the second one's (which also means "don't ask again").
        val DENY_BUTTON_IDS = listOf(
            "com.android.permissioncontroller:id/permission_deny_button",
            "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
        )

        // "All <app> notifications": Android 12's main switch bar, and the older switch bar.
        val MAIN_SWITCH_IDS = listOf(
            "com.android.settings:id/main_switch_bar",
            "com.android.settings:id/switch_bar",
        )
    }
}
