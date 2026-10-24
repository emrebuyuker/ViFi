package com.salticusteam.vifi.app

import androidx.lifecycle.LifecycleOwner
import com.salticusteam.vifi.core.data.repository.RemoteFileLoader
import com.salticusteam.vifi.core.notifications.NotificationBadge
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLifecycleHooksTest {

    private val badge = mockk<NotificationBadge> { every { clear() } just runs }
    private val loader = CountingFileLoader()
    private val owner = mockk<LifecycleOwner>()

    private fun TestScope.sut() = AppLifecycleHooks({ badge }, { loader }, backgroundScope)

    @Test
    fun `onStart clears the badge on every foreground`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.onStart(owner)
        sut.onStop(owner)
        sut.onStart(owner)

        verify(exactly = 2) { badge.clear() }
    }

    @Test
    fun `cache is pruned at the first foreground and on every background`() = runTest(UnconfinedTestDispatcher()) {
        val sut = sut()

        sut.onStart(owner)
        assertEquals(1, loader.prunes)
        sut.onStop(owner)
        sut.onStart(owner)
        sut.onStop(owner)

        assertEquals(3, loader.prunes)
    }

    @Test
    fun `a failing prune does not break the next transition`() = runTest(UnconfinedTestDispatcher()) {
        loader.failure = IllegalStateException("disk")
        val sut = sut()

        sut.onStart(owner)
        sut.onStop(owner)

        verify(exactly = 1) { badge.clear() }
        assertEquals(2, loader.prunes)
    }

    private class CountingFileLoader : RemoteFileLoader {
        var prunes = 0
            private set
        var failure: Exception? = null

        override suspend fun localFile(url: String, fileName: String): File = error("unused")

        override suspend fun pruneDiskCache() {
            prunes++
            failure?.let { throw it }
        }

        override suspend fun purge() = Unit
    }
}
