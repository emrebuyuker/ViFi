package com.salticusteam.vifi.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The public legal pages on the shared Firebase Hosting site (the same pages iOS links to), opened in the browser
 * from the add-exam terms row and the About screen.
 */
object LegalLinks {

    /** Kullanım Koşulları: what may be submitted, moderation, coins. */
    const val TERMS_URL = "https://vifi-831a8.web.app/kullanim-kosullari"

    /** Gizlilik Politikası (also the Play listing's privacy policy URL). */
    const val PRIVACY_URL = "https://vifi-831a8.web.app/gizlilik"

    /** Opens [url] in a browser. Never throws: with no app to open it, it does nothing and returns `false`. */
    fun open(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
