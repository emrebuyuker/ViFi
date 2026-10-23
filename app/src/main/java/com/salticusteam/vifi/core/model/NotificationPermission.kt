package com.salticusteam.vifi.core.model

// Added in 2.5 — whether the user has allowed the app to show notifications.
enum class NotificationPermission {
    NOT_DETERMINED,
    DENIED,

    /** The app may show notifications. */
    AUTHORIZED,
}
