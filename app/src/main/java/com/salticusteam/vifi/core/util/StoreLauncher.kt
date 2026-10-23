package com.salticusteam.vifi.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

/** Sends the user to the Play Store to update (iOS `openURL(update.storeURL)`). */
object StoreLauncher {

    /**
     * Opens the Play Store app on the app's page via `market://`, falling back to the https [storeUrl] in
     * any browser when no store app can handle it. Never throws: with nothing to open it does nothing.
     */
    fun openStore(context: Context, storeUrl: String) {
        val market = marketUrl(storeUrl, context.packageName)
        // If neither link can be opened there is nothing more to do; the gate simply stays up.
        if (!start(context, market)) start(context, storeUrl)
    }

    /** `market://details?id=…` for the package named in a Play URL's `id` parameter, else [packageName]. */
    fun marketUrl(storeUrl: String, packageName: String): String {
        val id = runCatching { URI(storeUrl).rawQuery }.getOrNull()
            ?.split('&')
            ?.firstNotNullOfOrNull { pair -> pair.substringAfter("id=", "").takeIf { pair.startsWith("id=") && it.isNotEmpty() } }
        return "market://details?id=${id ?: packageName}"
    }

    private fun start(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
