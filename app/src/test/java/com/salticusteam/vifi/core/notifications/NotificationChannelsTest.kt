package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.model.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server names the channel by the notification's kind, so the ids are a backend contract that never changes. */
class NotificationChannelsTest {

    @Test
    fun `push channel ids are exactly the server's notification kinds`() {
        val pushChannels = NotificationChannels.SPECS.map { it.id } - NotificationChannels.SUBMISSIONS

        assertEquals(NotificationKind.entries.map { it.raw }, pushChannels)
        assertEquals(NotificationKind.GENERAL.raw, NotificationChannels.GENERAL)
        assertEquals(NotificationKind.PROMO.raw, NotificationChannels.PROMO)
        assertEquals(NotificationKind.SYSTEM.raw, NotificationChannels.SYSTEM)
    }

    @Test
    fun `channels keep the local submissions channel and never recreate a retired one`() {
        val ids = NotificationChannels.SPECS.map { it.id }

        assertTrue(NotificationChannels.SUBMISSIONS in ids)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("announcements"), NotificationChannels.RETIRED)
        assertTrue(NotificationChannels.RETIRED.none { it in ids })
    }

    @Test
    fun `every channel has its own name and description`() {
        val texts = NotificationChannels.SPECS.flatMap { listOf(it.name, it.description) }

        assertEquals(texts.size, texts.toSet().size)
    }
}
