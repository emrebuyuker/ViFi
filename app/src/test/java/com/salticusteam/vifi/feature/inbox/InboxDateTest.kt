package com.salticusteam.vifi.feature.inbox

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A card's date (iOS `.dateTime.day().month().year()`, appendix A.4): "30 Eki 2026" in the device's time zone. */
class InboxDateTest {

    @Test
    fun `a card's date reads day, abbreviated Turkish month and year`() {
        // Noon UTC stays in October in every time zone, so only the day can move.
        val instant = Instant.parse("2026-10-30T12:00:00Z")
        val day = instant.atZone(ZoneId.systemDefault()).dayOfMonth

        assertEquals("$day Eki 2026", instant.toInboxDate())
    }

    @Test
    fun `an undated card shows no date`() {
        assertNull((null as Instant?).toInboxDate())
    }
}
