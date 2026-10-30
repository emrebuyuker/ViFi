package com.salticusteam.vifi.core.data.notifications.admin

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.salticusteam.vifi.core.model.NotificationAdminError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On device because the SDKs' code enums initialise an Android `SparseArray`: the admin error mapping reads them by
 * name, so every code it tells apart must keep its name in both SDKs (the mapping itself is unit-tested).
 */
@RunWith(AndroidJUnit4::class)
class FirebaseNotificationAdminErrorMappingTest {

    @Test
    fun functionsCodes_bridgeToTheSharedStatuses() {
        val expected = mapOf(
            FirebaseFunctionsException.Code.PERMISSION_DENIED to FirebaseStatus.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.UNAUTHENTICATED to FirebaseStatus.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT to FirebaseStatus.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION to FirebaseStatus.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.UNAVAILABLE to FirebaseStatus.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED to FirebaseStatus.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.INTERNAL to FirebaseStatus.OTHER,
            FirebaseFunctionsException.Code.NOT_FOUND to FirebaseStatus.OTHER,
        )
        for ((code, status) in expected) assertEquals(code.name, status, FirebaseStatus.named(code.name))
    }

    @Test
    fun firestoreCodes_bridgeToTheSharedStatuses() {
        val expected = mapOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED to FirebaseStatus.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED to FirebaseStatus.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.UNAVAILABLE to FirebaseStatus.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED to FirebaseStatus.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.DATA_LOSS to FirebaseStatus.OTHER,
        )
        for ((code, status) in expected) assertEquals(code.name, status, FirebaseStatus.named(code.name))
    }

    @Test
    fun firestoreFailures_mapLikeIos() {
        val denied = FirebaseFirestoreException("Missing or insufficient permissions.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val offline = FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE)
        val dataLoss = FirebaseFirestoreException("data loss", FirebaseFirestoreException.Code.DATA_LOSS)

        assertSame(NotificationAdminError.PermissionDenied, FirebaseNotificationAdminService.adminError(denied))
        assertSame(NotificationAdminError.Offline, FirebaseNotificationAdminService.adminError(offline))
        assertSame(NotificationAdminError.Unknown, FirebaseNotificationAdminService.adminError(dataLoss))
    }
}
