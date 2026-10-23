package com.salticusteam.vifi.core.util

import android.os.Bundle
import com.salticusteam.vifi.BuildConfig

/**
 * Deterministic start-up scenarios for the mock flavor, UI tests and demos (iOS `LaunchArgument` parity).
 * Passed as boolean intent extras, e.g. `adb shell am start -n <pkg>/<activity> --ez vifi_no_coins true`.
 * Wired from the launching intent by the single Compose host (FM-22); fakes read [LaunchArgs.isSet].
 */
enum class LaunchArg(val extraKey: String) {
    SIGNED_OUT("vifi_signed_out"),
    PHONE_AUTH_TESTING("vifi_phone_auth_testing"),
    FORCE_UPDATE("vifi_force_update"),
    PROFILE_INCOMPLETE("vifi_profile_incomplete"),
    ADMIN("vifi_admin"),
    NO_COINS("vifi_no_coins"),
    SHOW_ONBOARDING("vifi_show_onboarding"),
    NOTIFICATIONS_PROMPT("vifi_notifications_prompt"),
    DELETE_REQUIRES_REAUTH("vifi_delete_requires_reauth"),
}

/** Process-wide holder of the active launch arguments; empty unless set from a launching intent. */
object LaunchArgs {

    @Volatile
    private var active: Set<LaunchArg> = emptySet()

    fun isSet(arg: LaunchArg): Boolean = arg in active

    /** Reads every known extra from [extras]; unknown keys are ignored. Debug builds only (iOS `#if DEBUG`). */
    fun update(extras: Bundle?) {
        if (!BuildConfig.DEBUG) return
        active = parse(extras?.let { bundle -> LaunchArg.entries.associate { it.extraKey to bundle.getBoolean(it.extraKey) } })
    }

    /** Pure parsing step, separate from [Bundle] so it can be unit-tested on the JVM. */
    fun parse(flags: Map<String, Boolean>?): Set<LaunchArg> =
        LaunchArg.entries.filterTo(mutableSetOf()) { flags?.get(it.extraKey) == true }
}
