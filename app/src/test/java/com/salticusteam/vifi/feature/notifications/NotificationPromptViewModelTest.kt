package com.salticusteam.vifi.feature.notifications

import com.salticusteam.vifi.core.data.preferences.fake.FakeUserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeNotificationService
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()

    @Test
    fun `undetermined and never prompted shows the prompt and joins the all topic`() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeNotificationService(NotificationPermission.NOT_DETERMINED)
        val sut = NotificationPromptViewModel(service, preferences)
        advanceUntilIdle()

        assertTrue(sut.isVisible.value)
        assertEquals(setOf("all"), service.subscribedTopics)
    }

    @Test
    fun `already allowed shows no prompt but still subscribes`() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeNotificationService(NotificationPermission.AUTHORIZED)
        val sut = NotificationPromptViewModel(service, preferences)
        advanceUntilIdle()

        assertFalse(sut.isVisible.value)
        assertEquals(setOf("all"), service.subscribedTopics)
    }

    @Test
    fun `allow requests the permission and is not asked again`() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeNotificationService(NotificationPermission.NOT_DETERMINED)
        val sut = NotificationPromptViewModel(service, preferences)
        advanceUntilIdle()

        sut.onAllow()
        advanceUntilIdle()

        assertFalse(sut.isVisible.value)
        assertEquals(1, service.requestCount)
        assertTrue(preferences.notificationsPrompted.first())
    }

    @Test
    fun `later records the prompt without asking the system`() = runTest(UnconfinedTestDispatcher()) {
        val service = FakeNotificationService(NotificationPermission.NOT_DETERMINED)
        val sut = NotificationPromptViewModel(service, preferences)
        advanceUntilIdle()

        sut.onLater()
        advanceUntilIdle()

        assertFalse(sut.isVisible.value)
        assertEquals(0, service.requestCount)
        assertTrue(preferences.notificationsPrompted.first())
    }

    @Test
    fun `an already prompted device is not asked again`() = runTest(UnconfinedTestDispatcher()) {
        preferences.setNotificationsPrompted(true)
        val sut = NotificationPromptViewModel(FakeNotificationService(NotificationPermission.DENIED), preferences)
        advanceUntilIdle()

        assertFalse(sut.isVisible.value)
    }
}
