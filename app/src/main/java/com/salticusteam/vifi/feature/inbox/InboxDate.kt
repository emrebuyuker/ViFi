package com.salticusteam.vifi.feature.inbox

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val inboxFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("tr-TR")).withZone(ZoneId.systemDefault())

/** "30 Eki 2026" (iOS's abbreviated month), or `null` without a date. Built once; formatters are thread-safe. */
fun Instant?.toInboxDate(): String? = this?.let(inboxFormatter::format)
