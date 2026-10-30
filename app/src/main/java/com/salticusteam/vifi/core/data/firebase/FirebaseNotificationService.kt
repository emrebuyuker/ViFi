package com.salticusteam.vifi.core.data.firebase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.salticusteam.vifi.core.data.preferences.UserPreferencesRepository
import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.notifications.NotificationPermissionRequester
import com.salticusteam.vifi.core.util.ForegroundActivityHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/** Live push registration: the Android 13+ runtime permission plus FCM token and topics. */
class FirebaseNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messaging: FirebaseMessaging,
    private val requester: NotificationPermissionRequester,
    private val preferences: UserPreferencesRepository,
    private val activityHolder: ForegroundActivityHolder,
) : NotificationService {

    override suspend fun permission(): NotificationPermission = when {
        isGranted() -> NotificationPermission.AUTHORIZED
        // Before Android 13 there is no prompt: switched off in system settings is simply denied.
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> NotificationPermission.DENIED
        // Android reports "never asked" and "denied" alike; the app's own prompted flag tells them apart.
        preferences.notificationsPrompted.first() -> NotificationPermission.DENIED
        else -> NotificationPermission.NOT_DETERMINED
    }

    override suspend fun requestAuthorization(): Boolean {
        if (isGranted()) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = requester.request(Manifest.permission.POST_NOTIFICATIONS)
        preferences.setNotificationsPrompted(true)
        return granted
    }

    override suspend fun canRequestPermission(): Boolean {
        if (isGranted() || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (!preferences.notificationsPrompted.first()) return true
        // After one "no" the system shows the dialog once more and says so through the rationale flag.
        val activity = activityHolder.activity ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    }

    override suspend fun token(): String? = try {
        messaging.token.await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Reading the FCM token failed")
        null
    }

    override suspend fun subscribe(topic: String) {
        messaging.subscribeToTopic(topic).await()
    }

    override suspend fun unsubscribe(topic: String) {
        messaging.unsubscribeFromTopic(topic).await()
    }

    private fun isGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
}
