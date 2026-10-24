package com.salticusteam.vifi.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the Baseline Profile for the critical user journey: cold start → Home → one university's
 * faculties. Runs against the mock flavor (bundled sample data), so no Firebase call is made.
 *
 * Generate with a device/emulator connected:
 * `./gradlew :app:generateBaselineProfile`
 * The result lands in `app/src/main/generated/baselineProfiles/` and is shipped by every release build.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // First launch: the onboarding tour and the notification prompt cover Home; dismiss them if shown.
        device.clickIfPresent(SKIP_ONBOARDING)
        device.clickIfPresent(NOTIFICATION_LATER)

        // Home → the first sample university → its faculty list.
        device.wait(Until.hasObject(By.text(SAMPLE_UNIVERSITY)), TIMEOUT_MS)
        device.findObject(By.text(SAMPLE_UNIVERSITY))?.click()
        device.wait(Until.hasObject(By.text(SAMPLE_FACULTY)), TIMEOUT_MS)
        device.waitForIdle()
    }

    private fun UiDevice.clickIfPresent(text: String) {
        if (wait(Until.hasObject(By.text(text)), SHORT_TIMEOUT_MS) == true) {
            findObject(By.text(text))?.click()
            waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.salticusteam.vifi"
        // Visible texts of the app (strings.xml) and of the mock FakeArchiveRepository's sample catalog.
        const val SKIP_ONBOARDING = "Atla"
        const val NOTIFICATION_LATER = "Şimdi Değil"
        const val SAMPLE_UNIVERSITY = "İstanbul Teknik Üniversitesi"
        const val SAMPLE_FACULTY = "Mühendislik"
        const val TIMEOUT_MS = 10_000L
        const val SHORT_TIMEOUT_MS = 3_000L
    }
}
