package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.LoginDiagnosticsReporter
import com.salticusteam.vifi.core.model.LoginFailureInfo

/** Keeps every reported failure, so tests can assert what reached the diagnostics sink. */
class RecordingLoginDiagnosticsReporter : LoginDiagnosticsReporter {
    val reports = mutableListOf<LoginFailureInfo>()

    override fun report(info: LoginFailureInfo) {
        reports += info
    }
}
