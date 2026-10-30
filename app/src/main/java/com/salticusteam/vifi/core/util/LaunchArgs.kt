package com.salticusteam.vifi.core.util

import android.os.Bundle
import com.salticusteam.vifi.BuildConfig

/**
 * Deterministic start-up scenarios for the mock flavor, UI tests and demos (iOS `LaunchArgument` parity).
 * Flags are boolean intent extras, e.g. `adb shell am start -n <pkg>/<activity> --ez vifi_no_coins true`; an
 * argument that [takesValue] is a string extra, e.g. `--es vifi_open_notification p-paywall`.
 * Wired from the launching intent by the single Compose host (FM-22); fakes read [LaunchArgs.isSet] / [LaunchArgs.value].
 */
enum class LaunchArg(val extraKey: String, val takesValue: Boolean = false) {
    SIGNED_OUT("vifi_signed_out"),
    PHONE_AUTH_TESTING("vifi_phone_auth_testing"),
    FORCE_UPDATE("vifi_force_update"),
    OPTIONAL_UPDATE("vifi_optional_update"),
    PROFILE_INCOMPLETE("vifi_profile_incomplete"),
    ADMIN("vifi_admin"),
    NO_COINS("vifi_no_coins"),
    SHOW_ONBOARDING("vifi_show_onboarding"),

    /** A fresh install: notifications never asked and no prompt history, so the permission flow opens (1/2 → 2/2). */
    NOTIFICATIONS_PROMPT("vifi_notifications_prompt"),
    DELETE_REQUIRES_REAUTH("vifi_delete_requires_reauth"),
    SUBSCRIBED("vifi_subscribed"),

    /** Starts as if the push with this notification id was tapped (iOS `-ViFiOpenNotification <id>`). */
    OPEN_NOTIFICATION("vifi_open_notification", takesValue = true),

    /** The system notification permission is denied (Account › Bildirim Ayarları offers "Ayarlar'ı Aç"). */
    NOTIFICATIONS_DENIED("vifi_notifications_denied"),

    /** The permission step was declined 31 days ago: the next successful exam submission asks once more. */
    NOTIFICATIONS_REASK("vifi_notifications_reask"),

    /** The campaign consent was declined 31 days ago: opening the inbox asks once more. */
    CONSENT_REASK("vifi_consent_reask"),
}

/** Process-wide holder of the active launch arguments; empty unless set from a launching intent. */
object LaunchArgs {

    @Volatile
    private var active: Set<LaunchArg> = emptySet()

    @Volatile
    private var values: Map<LaunchArg, String> = emptyMap()

    /** Whether a flag is set, or a valued argument was given a value. */
    fun isSet(arg: LaunchArg): Boolean = arg in active || arg in values

    /** The value of an argument that [LaunchArg.takesValue], or `null` when it was not given. */
    fun value(arg: LaunchArg): String? = values[arg]

    /** Reads every known extra from [extras]; unknown keys are ignored. Debug builds only (iOS `#if DEBUG`). */
    fun update(extras: Bundle?) {
        if (!BuildConfig.DEBUG) return
        val (valued, flags) = LaunchArg.entries.partition { it.takesValue }
        active = parse(extras?.let { bundle -> flags.associate { it.extraKey to bundle.getBoolean(it.extraKey) } })
        values = parseValues(extras?.let { bundle -> valued.associate { it.extraKey to bundle.getString(it.extraKey) } })
    }

    /** Pure parsing step for the flags, separate from [Bundle] so it can be unit-tested on the JVM. */
    fun parse(flags: Map<String, Boolean>?): Set<LaunchArg> =
        LaunchArg.entries.filterTo(mutableSetOf()) { !it.takesValue && flags?.get(it.extraKey) == true }

    /** Pure parsing step for the valued arguments: trimmed, blank means not given. */
    fun parseValues(raw: Map<String, String?>?): Map<LaunchArg, String> =
        LaunchArg.entries.filter { it.takesValue }.mapNotNull { arg ->
            raw?.get(arg.extraKey)?.trim()?.takeIf { it.isNotEmpty() }?.let { arg to it }
        }.toMap()
}
