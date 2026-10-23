package com.salticusteam.vifi.core.data.repository

import app.cash.turbine.test
import com.salticusteam.vifi.core.model.AppUpdateConfig
import com.salticusteam.vifi.core.model.AppUpdateStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAppUpdateCheckerTest {

    private fun checker(vararg configs: AppUpdateConfig?, installed: String = "3.1") =
        RemoteAppUpdateChecker(
            reader = object : AppConfigReader {
                override fun configChanges(): Flow<AppUpdateConfig?> = flowOf(*configs)
            },
            installedVersion = installed,
            fallbackStoreUrl = "https://fallback",
        )

    private suspend fun RemoteAppUpdateChecker.collected(): List<AppUpdateStatus> {
        val items = mutableListOf<AppUpdateStatus>()
        statusChanges().test {
            while (true) {
                val event = awaitEvent()
                if (event is app.cash.turbine.Event.Item) items += event.value else break
            }
        }
        return items
    }

    @Test
    fun `status when minimum is above installed is required`() {
        val status = RemoteAppUpdateChecker.status(AppUpdateConfig(minimumVersion = "3.2"), "3.1", "https://fallback")
        assertTrue(status is AppUpdateStatus.Required)
        assertEquals("3.2", (status as AppUpdateStatus.Required).update.version)
    }

    @Test
    fun `status when only latest is above installed is optional`() {
        val status = RemoteAppUpdateChecker.status(AppUpdateConfig(latestVersion = "3.2"), "3.1")
        assertTrue(status is AppUpdateStatus.Optional)
    }

    @Test
    fun `status when both are above installed required wins`() {
        val status = RemoteAppUpdateChecker.status(AppUpdateConfig(minimumVersion = "3.2", latestVersion = "3.3"), "3.1")
        assertTrue(status is AppUpdateStatus.Required)
        assertEquals("3.2", (status as AppUpdateStatus.Required).update.version)
    }

    @Test
    fun `status when installed satisfies the policy is up to date`() {
        val status = RemoteAppUpdateChecker.status(AppUpdateConfig(minimumVersion = "3.1", latestVersion = "3.1"), "3.1")
        assertEquals(AppUpdateStatus.UpToDate, status)
    }

    @Test
    fun `status uses config store url and message else the fallback`() {
        val withConfig = RemoteAppUpdateChecker.status(
            AppUpdateConfig(minimumVersion = "4", storeUrl = "https://store", message = "Güncelle"), "3.1", "https://fallback",
        ) as AppUpdateStatus.Required
        assertEquals("https://store", withConfig.update.storeUrl)
        assertEquals("Güncelle", withConfig.update.message)

        val withoutUrl = RemoteAppUpdateChecker.status(AppUpdateConfig(minimumVersion = "4"), "3.1", "https://fallback") as AppUpdateStatus.Required
        assertEquals("https://fallback", withoutUrl.update.storeUrl)
    }

    @Test
    fun `statusChanges when policy is unreadable and never required is up to date`() = runTest {
        assertEquals(listOf(AppUpdateStatus.UpToDate), checker(null).collected())
    }

    @Test
    fun `statusChanges when required then unreadable keeps the gate latched`() = runTest {
        val items = checker(AppUpdateConfig(minimumVersion = "3.2"), null).collected()
        // distinctUntilChanged collapses the repeated required status into one emission.
        assertEquals(1, items.size)
        assertTrue(items.single() is AppUpdateStatus.Required)
    }

    @Test
    fun `statusChanges when required then satisfied lifts the gate`() = runTest {
        val items = checker(AppUpdateConfig(minimumVersion = "3.2"), AppUpdateConfig(minimumVersion = "1.0")).collected()
        assertEquals(2, items.size)
        assertTrue(items[0] is AppUpdateStatus.Required)
        assertEquals(AppUpdateStatus.UpToDate, items[1])
    }

    @Test
    fun `statusChanges after a lifted gate an unreadable policy stays up to date`() = runTest {
        val items = checker(AppUpdateConfig(minimumVersion = "3.2"), AppUpdateConfig(), null).collected()
        assertEquals(listOf(AppUpdateStatus.UpToDate), items.drop(1))
    }

    @Test
    fun `statusChanges when optional is later made required emits both in order`() = runTest {
        val items = checker(AppUpdateConfig(latestVersion = "3.2"), AppUpdateConfig(minimumVersion = "3.2")).collected()
        assertTrue(items[0] is AppUpdateStatus.Optional)
        assertTrue(items[1] is AppUpdateStatus.Required)
    }
}
