package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {

    private val now = 1_800_000_000_000L
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `between when under a minute or in the future is just now`() {
        assertEquals(RelativeTime.JustNow, RelativeTime.between(now - 59_999, now))
        assertEquals(RelativeTime.JustNow, RelativeTime.between(now + hour, now))
    }

    @Test
    fun `between buckets each unit at its boundary`() {
        assertEquals(RelativeTime.Minutes(1), RelativeTime.between(now - minute, now))
        assertEquals(RelativeTime.Minutes(59), RelativeTime.between(now - hour + 1, now))
        assertEquals(RelativeTime.Hours(1), RelativeTime.between(now - hour, now))
        assertEquals(RelativeTime.Hours(23), RelativeTime.between(now - day + 1, now))
        assertEquals(RelativeTime.Days(1), RelativeTime.between(now - day, now))
        assertEquals(RelativeTime.Days(6), RelativeTime.between(now - 7 * day + 1, now))
        assertEquals(RelativeTime.Weeks(1), RelativeTime.between(now - 7 * day, now))
        assertEquals(RelativeTime.Weeks(4), RelativeTime.between(now - 30 * day + 1, now))
        assertEquals(RelativeTime.Months(1), RelativeTime.between(now - 30 * day, now))
        assertEquals(RelativeTime.Months(12), RelativeTime.between(now - 365 * day + 1, now))
        assertEquals(RelativeTime.Years(2), RelativeTime.between(now - 2 * 365 * day, now))
    }
}
