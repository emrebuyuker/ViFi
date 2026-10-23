package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.LoginFailureInfo

/**
 * Reports failed sign-in attempts to the diagnostics sink. The live implementation calls the
 * `recordLoginDiagnostic` Cloud Function; [NoopLoginDiagnosticsReporter] is used in fake mode and as the
 * default, so nothing is reported unless the app is wired to Firebase.
 */
interface LoginDiagnosticsReporter {
    fun report(info: LoginFailureInfo)
}

/** Drops every report. Used in fake/preview/tests and as the default dependency. */
object NoopLoginDiagnosticsReporter : LoginDiagnosticsReporter {
    override fun report(info: LoginFailureInfo) = Unit
}
