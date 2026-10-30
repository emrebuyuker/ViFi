package com.salticusteam.vifi.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salticusteam.vifi.core.model.NotificationKind
import java.io.ByteArrayOutputStream
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A foreground push posted for real: channel by kind, one tray entry per id, the image as a big picture. */
@RunWith(AndroidJUnit4::class)
class PushNotifierDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val requests = mutableListOf<Request>()

    private val image: ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.rgb(251, 146, 15)) }
            .compress(Bitmap.CompressFormat.PNG, 100, out)
    }.toByteArray()

    @After
    fun tearDown() {
        listOf("n-text", "n-image", "n-broken", "n-repeat", "n-other").forEach {
            manager.cancel(NotificationContent.TRAY_TAG, it.hashCode())
        }
    }

    @Test
    fun show_postsNothingWithoutPermission_thenPostsOnTheKindsChannel() {
        val content = NotificationContent("Duyuru", "Bakım bu gece", notificationId = "n-text", kind = NotificationKind.SYSTEM)
        val notifier = notifier(serving(image))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted()) {
            assertFalse(notifier.show(content))
            SystemClock.sleep(300)
            assertNull(active("n-text"))
        }
        grantPermission()

        assertTrue(notifier.show(content))

        val posted = awaitActive("n-text").notification
        assertEquals(NotificationChannels.SYSTEM, posted.channelId)
        assertEquals("Duyuru", posted.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(BIG_TEXT, posted.extras.getString(Notification.EXTRA_TEMPLATE))
        assertNotNull(posted.contentIntent)
    }

    @Test
    fun show_withAnImage_postsABigPictureFetchedWithoutCredentials() {
        grantPermission()
        val content = NotificationContent(
            title = "Kampanya",
            body = "Kafede öğrenciye %20",
            notificationId = "n-image",
            kind = NotificationKind.PROMO,
            imageUrl = "https://storage.googleapis.com/vifi-831a8.appspot.com/notificationImages/n-image.jpg?X-Goog-Signature=ab",
        )

        assertTrue(notifier(serving(image)).show(content))

        val posted = awaitActive("n-image").notification
        assertEquals(NotificationChannels.PROMO, posted.channelId)
        assertEquals(BIG_PICTURE, posted.extras.getString(Notification.EXTRA_TEMPLATE))
        val request = requests.single()
        assertNull(request.header("Authorization"))
        assertNull(request.header("X-Firebase-AppCheck"))
    }

    @Test
    fun show_whenTheImageFails_fallsBackToTheText() {
        grantPermission()
        val content = NotificationContent(
            title = "Duyuru",
            body = "Görselsiz",
            notificationId = "n-broken",
            imageUrl = "https://storage.googleapis.com/b/expired.jpg",
        )

        assertTrue(notifier(failing()).show(content))

        val posted = awaitActive("n-broken").notification
        assertEquals(NotificationChannels.GENERAL, posted.channelId)
        assertEquals(BIG_TEXT, posted.extras.getString(Notification.EXTRA_TEMPLATE))
    }

    @Test
    fun show_sameIdReplacesOnlyItsOwnEntry() {
        grantPermission()
        val notifier = notifier(serving(image))

        notifier.show(NotificationContent("İlk", "1", notificationId = "n-repeat"))
        notifier.show(NotificationContent("Diğer", "2", notificationId = "n-other"))
        notifier.show(NotificationContent("Güncel", "3", notificationId = "n-repeat"))

        awaitActive("n-other")
        val repeated = awaitActive("n-repeat") {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "Güncel"
        }
        assertEquals("Güncel", repeated.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(1, manager.activeNotifications.count { it.tag == NotificationContent.TRAY_TAG && it.id == "n-repeat".hashCode() })
    }

    // Helpers
    private fun notifier(interceptor: Interceptor) =
        PushNotifier(context, NotificationImageLoader(OkHttpClient.Builder().addInterceptor(interceptor).build()))

    private fun serving(bytes: ByteArray) = Interceptor { chain ->
        requests += chain.request()
        response(chain.request(), 200, bytes)
    }

    private fun failing() = Interceptor { chain -> response(chain.request(), 403, "expired".toByteArray()) }

    private fun response(request: Request, code: Int, bytes: ByteArray) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("")
        .body(bytes.toResponseBody("image/png".toMediaType()))
        .build()

    private fun isGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun grantPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted()) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun active(id: String): StatusBarNotification? =
        manager.activeNotifications.firstOrNull { it.tag == NotificationContent.TRAY_TAG && it.id == id.hashCode() }

    /** Posting reaches the system asynchronously, so the entry is polled for a short while. */
    private fun awaitActive(id: String, matches: (StatusBarNotification) -> Boolean = { true }): StatusBarNotification {
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < deadline) {
            active(id)?.takeIf(matches)?.let { return it }
            SystemClock.sleep(50)
        }
        return checkNotNull(active(id)?.takeIf(matches)) { "Notification $id was not posted" }
    }

    private companion object {
        const val BIG_TEXT = "android.app.Notification\$BigTextStyle"
        const val BIG_PICTURE = "android.app.Notification\$BigPictureStyle"
    }
}
