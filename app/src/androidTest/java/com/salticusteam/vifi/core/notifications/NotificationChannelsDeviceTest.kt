package com.salticusteam.vifi.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Channels exist from Android 8 on; on a real system the retired 4.0.0 channel is gone and the badge stays on. */
@RunWith(AndroidJUnit4::class)
class NotificationChannelsDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    }

    @Test
    fun ensure_createsEveryChannelWithItsTextsAndTheSystemBadge() {
        NotificationChannels.ensure(context)

        NotificationChannels.SPECS.forEach { spec ->
            val channel = checkNotNull(manager.getNotificationChannel(spec.id)) { "Missing channel ${spec.id}" }
            assertEquals(context.getString(spec.name), channel.name)
            assertEquals(context.getString(spec.description), channel.description)
            assertTrue(channel.canShowBadge())
        }
    }

    @Test
    fun ensure_deletesTheRetiredAnnouncementsChannel() {
        manager.createNotificationChannel(
            NotificationChannel("announcements", "Genel bildirimler", NotificationManager.IMPORTANCE_DEFAULT),
        )

        NotificationChannels.ensure(context)

        assertNull(manager.getNotificationChannel("announcements"))
    }
}
