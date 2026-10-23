package com.salticusteam.vifi.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock tests move by hand (cooldowns, throttles). */
class MutableClock(var now: Instant = Instant.parse("2026-10-23T09:00:00Z")) : Clock() {

    fun advance(by: Duration) {
        now = now.plus(by)
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}
