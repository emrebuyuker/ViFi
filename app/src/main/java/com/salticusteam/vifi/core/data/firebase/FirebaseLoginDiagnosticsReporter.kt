package com.salticusteam.vifi.core.data.firebase

import android.os.Build
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.functions.FirebaseFunctions
import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.core.data.repository.LoginDiagnosticsReporter
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.LoginDiagnosticThrottle
import com.salticusteam.vifi.core.model.LoginFailureInfo
import com.salticusteam.vifi.core.util.LogCategory
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Reports failed sign-in attempts to the shared `recordLoginDiagnostic` callable (europe-west1, injected by
 * FirebaseModule) so support can later look up why a number could not sign in (iOS FirebaseLoginDiagnosticsService).
 *
 * Fire-and-forget: runs in the app scope, never blocks or fails the login flow, and a lost report is acceptable.
 * App Check is deliberately not enforced on the function (a login that fails *because* of App Check must still be
 * recorded), so the device's App Check status is probed and sent as data. Identical reports within 60 seconds are
 * dropped by [LoginDiagnosticThrottle].
 */
class FirebaseLoginDiagnosticsReporter @Inject constructor(
    private val functions: FirebaseFunctions,
    private val appCheck: FirebaseAppCheck,
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: Clock,
) : LoginDiagnosticsReporter {

    private val throttle = LoginDiagnosticThrottle()

    override fun report(info: LoginFailureInfo) {
        val shouldSend = synchronized(throttle) { throttle.shouldSend(signature(info), clock.instant()) }
        if (!shouldSend) {
            LogCategory.APP.log.d("Login diagnostic suppressed (duplicate): %s", info.reason)
            return
        }
        scope.launch {
            try {
                val data = payload(info, appCheckStatus(), DeviceInfo.current())
                functions.getHttpsCallable(FUNCTION_NAME).call(data).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Diagnostics must never surface to the user; losing one record is acceptable.
                LogCategory.APP.log.e(e, "Login diagnostic report failed")
            }
        }
    }

    /** Whether the device can mint an App Check token right now; "error" itself hints at why sign-in failed. */
    private suspend fun appCheckStatus(): String =
        try {
            appCheck.getAppCheckToken(false).await()
            "valid"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "error"
        }

    /** What the payload says about the app and device (Build/BuildConfig on a device, fixed values in tests). */
    data class DeviceInfo(val appVersion: String, val osVersion: String, val deviceModel: String) {
        companion object {
            fun current() = DeviceInfo(
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                osVersion = Build.VERSION.RELEASE,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }

    internal companion object {
        private const val FUNCTION_NAME = "recordLoginDiagnostic"

        /** Duplicate key: the same number failing for the same reason at the same stage. */
        fun signature(info: LoginFailureInfo): String =
            "${info.phoneNumber.orEmpty()}|${info.reason}|${info.stage.raw}"

        /** The callable's payload; field names are the server contract, shared with iOS. */
        fun payload(info: LoginFailureInfo, appCheckStatus: String, device: DeviceInfo): Map<String, Any> =
            buildMap {
                put("reason", info.reason)
                put("step", info.stage.raw)
                put("phoneNumber", info.phoneNumber.orEmpty())
                put("appCheckStatus", appCheckStatus)
                put("appVersion", device.appVersion)
                put("osVersion", device.osVersion)
                put("deviceModel", device.deviceModel)
                // ISO 8601 in UTC, e.g. 2026-10-23T09:15:00Z.
                put("occurredAt", info.occurredAt.toString())
                info.backendCode?.let { put("backendCode", it) }
            }
    }
}
