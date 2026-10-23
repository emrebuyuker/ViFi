package com.salticusteam.vifi.core.model

import java.time.Instant

// Added in 2.5 — a notification shown in the in-app inbox (public list; read state is tracked per device).
data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Instant?,
)
