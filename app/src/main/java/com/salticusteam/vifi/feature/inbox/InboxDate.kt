package com.salticusteam.vifi.feature.inbox

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val inboxFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("tr", "TR")).withZone(ZoneId.systemDefault())

/** "20 Ekim 2026", or `null` when the announcement has no date. Built once; formatters are thread-safe. */
fun Instant?.toInboxDate(): String? = this?.let(inboxFormatter::format)
