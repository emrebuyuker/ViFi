package com.salticusteam.vifi.feature.inbox

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salticusteam.vifi.core.model.NotificationLocation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Konuma Git"'s two choices on a real device (iOS `MapsLinksTests.googleChoice`, NDEC-03): what each one starts, with
 * nothing actually opened (the starts are caught on the way out). The missing Google Maps app is simulated by the
 * system's own "no such app" answer; whether any app takes a `geo:` link is the device's.
 */
@RunWith(AndroidJUnit4::class)
class MapsLaunchDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val place = NotificationLocation(
        latitude = 40.9903,
        longitude = 29.0290,
        name = "ViFi Öğrenci Kafesi",
        address = "Caferağa, Kadıköy, İstanbul",
        placeId = "ChIJ-test",
    )

    private val started = mutableListOf<Intent>()
    private var googleMapsMissing = false

    // Catches only the maps starts (the test's own host activity still opens) and opens none of them.
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_CHOOSER) return null
            if (googleMapsMissing && intent.`package` == MapsLinks.GOOGLE_MAPS_PACKAGE) {
                throw ActivityNotFoundException("No Activity found to handle $intent")
            }
            started += Intent(intent)
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Before
    fun setUp() {
        instrumentation.addMonitor(monitor)
    }

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
    }

    @Test
    fun googleMaps_opensTheLinkInTheGoogleMapsApp() {
        fromAnActivity { it.openInGoogleMaps(place) }

        val intent = started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(MapsLinks.googleMaps(place), intent.dataString)
        assertEquals(MapsLinks.GOOGLE_MAPS_PACKAGE, intent.`package`)
    }

    @Test
    fun googleMaps_withoutTheApp_opensTheSameLinkInTheBrowser() {
        googleMapsMissing = true

        fromAnActivity { it.openInGoogleMaps(place) }

        assertOpensGoogleMapsOnTheWeb(started.single())
    }

    @Test
    fun otherMapsApp_offersTheAppsThatTakeAGeoLink() {
        assumeTrue("No app on this device takes a geo: link", geoHandlerCount() > 0)

        fromAnActivity { it.openInOtherMapsApp(place) }

        val chooser = started.single()
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val geo = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_VIEW, geo?.action)
        assertEquals(MapsLinks.geo(place), geo?.dataString)
    }

    @Test
    fun otherMapsApp_withoutAnyMapsApp_opensGoogleMapsOnTheWeb() {
        assumeTrue("An app on this device takes a geo: link", geoHandlerCount() == 0)

        fromAnActivity { it.openInOtherMapsApp(place) }

        assertOpensGoogleMapsOnTheWeb(started.single())
    }

    // Helpers
    /** The production calls run from the screen's activity, as in the app (a plain context needs a new task). */
    private fun fromAnActivity(block: (Activity) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity(block) }
    }

    private fun assertOpensGoogleMapsOnTheWeb(intent: Intent) {
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(MapsLinks.googleMaps(place), intent.dataString)
        assertNull(intent.`package`)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    private fun geoHandlerCount(): Int {
        val geo = Intent(Intent.ACTION_VIEW, MapsLinks.geo(place).toUri())
        val packageManager = instrumentation.targetContext.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(geo, PackageManager.ResolveInfoFlags.of(0)).size
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(geo, 0).size
        }
    }
}
