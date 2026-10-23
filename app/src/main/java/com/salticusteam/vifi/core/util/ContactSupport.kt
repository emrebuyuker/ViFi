package com.salticusteam.vifi.core.util

import java.net.URLEncoder

/**
 * What the "Bize Ulaş" (contact support) entry points share: the public support address and the `mailto:`
 * link they open (iOS `ContactSupport`). The no-mail-app fallback (copy the address) belongs to the UI that
 * opens the link (Account area).
 */
object ContactSupport {

    /** The app's public support address (same as iOS; deliberately public, not a private contact). */
    const val EMAIL = "buyukeryazilim@gmail.com"

    /** The subject prefilled for the user; they can edit it before sending. */
    const val SUBJECT = "ViFi — Bize Ulaş"

    /** The `mailto:` link, optionally prefilling [context] (e.g. the exam the user writes about). */
    fun mailtoUri(appVersion: String, androidVersion: String, context: String? = null): String =
        "mailto:$EMAIL?subject=${encode(SUBJECT)}&body=${encode(body(appVersion, androidVersion, context))}"

    /**
     * The prefilled body: the context line (if any), an empty area for the user, then a separator and the
     * app/Android version for support. Carries no personal data — no name, phone number or user id.
     */
    fun body(appVersion: String, androidVersion: String, context: String? = null): String {
        val lines = mutableListOf<String>()
        if (!context.isNullOrEmpty()) {
            lines += context
            lines += ""
        }
        lines += ""
        lines += "—"
        lines += "ViFi $appVersion · Android $androidVersion"
        return lines.joinToString("\n")
    }

    /** mailto needs %20 for spaces, not the form-encoding '+'. */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
