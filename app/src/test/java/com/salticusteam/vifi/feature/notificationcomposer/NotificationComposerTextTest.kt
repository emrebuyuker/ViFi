package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.model.NotificationKind
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** The admin screens' date and kind choices (iOS `NotificationAdminLabels`, appendix C.3). */
class NotificationComposerTextTest {

    @Test
    fun `dates read like iOS's abbreviated date with a 24-hour time, in the device's time zone`() {
        val instant = Instant.parse("2026-10-30T11:30:00Z")
        val local = instant.atZone(ZoneId.systemDefault())

        assertEquals(
            "%d Eki 2026 %02d:%02d".format(local.dayOfMonth, local.hour, local.minute),
            instant.toComposerDateTime(),
        )
    }

    @Test
    fun `an admin picks Duyuru or Kampanya, never the automatic messages' kind`() {
        assertEquals(listOf(NotificationKind.GENERAL, NotificationKind.PROMO), SELECTABLE_KINDS)
    }
}
