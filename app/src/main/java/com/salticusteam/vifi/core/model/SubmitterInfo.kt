package com.salticusteam.vifi.core.model

// Added in 4.2.0 — who submitted an exam, as shown to the reviewing admin (iOS `SubmitterInfo`).
/**
 * Deliberately minimal: no phone number (KVKK) and no gender, only what a moderator needs to recognise the submitter.
 * Read from RTDB `users/<uid>/profile`.
 */
data class SubmitterInfo(
    val fullName: String,
    val university: String,
    val faculty: String,
    val department: String,
)
